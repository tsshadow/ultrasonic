/*
 * JukeboxMediaPlayer.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
import android.view.KeyEvent.KEYCODE_MEDIA_PLAY
import android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.api.subsonic.SubsonicRESTException
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.JukeboxStatus
import org.moire.ultrasonic.playback.MediaNotificationProvider
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Util.getPendingIntentToShowPlayer
import org.moire.ultrasonic.util.Util.sleepQuietly
import org.moire.ultrasonic.util.Util.stopForegroundRemoveNotification
import timber.log.Timber

private const val STATUS_UPDATE_INTERVAL_SECONDS = 5L
private const val SEEK_INCREMENT_SECONDS = 5L
private const val SEEK_START_AFTER_SECONDS = 5
private const val QUEUE_POLL_INTERVAL_SECONDS = 1L

/**
 * Provides an asynchronous interface to the remote jukebox on the Subsonic server.
 *
 * TODO: Report warning if queue fills up.
 * TODO: Disable repeat.
 * TODO: Persist RC state?
 * TODO: Minimize status updates.
 */
@Suppress("TooManyFunctions")
@SuppressLint("UnsafeOptInUsageError")
class JukeboxMediaPlayer : JukeboxUnimplementedFunctions(), Player {
    private val tasks = TaskQueue()
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private var statusUpdateFuture: ScheduledFuture<*>? = null
    private val timeOfLastUpdate = AtomicLong()
    private var jukeboxStatus: JukeboxStatus? = null
    private var previousJukeboxStatus: JukeboxStatus? = null
    private var gain = 0.5f
    private var volumeToast: VolumeToast? = null
    private var serviceThread: Thread? = null

    private var listeners: MutableList<Player.Listener> = mutableListOf()
    private val playlist: MutableList<MediaItem> = mutableListOf()
    private var currentIndex: Int = 0
    private val notificationProvider = MediaNotificationProvider(applicationContext())
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManagerCompat: NotificationManagerCompat

    @Suppress("MagicNumber")
    override fun onCreate() {
        super.onCreate()
        if (running.get()) return
        running.set(true)

        tasks.clear()
        updatePlaylist()
        stop()

        startFuture?.set(this)

        startProcessTasks()

        notificationManagerCompat = NotificationManagerCompat.from(this)
        mediaSession = MediaSession.Builder(applicationContext(), this)
            .setId("jukebox")
            .setSessionActivity(getPendingIntentToShowPlayer(this))
            .build()
        val notification = notificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            JukeboxNotificationActionFactory()
        ) {}

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                notification.notificationId,
                notification.notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(
                notification.notificationId, notification.notification
            )
        }

        Timber.d("Started Jukebox Service")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (Intent.ACTION_MEDIA_BUTTON != intent?.action) return START_STICKY

        val extras = intent.extras
        if ((extras != null) && extras.containsKey(Intent.EXTRA_KEY_EVENT)) {
            val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            }
            when (event?.keyCode) {
                KEYCODE_MEDIA_PLAY -> play()
                KEYCODE_MEDIA_PAUSE -> stop()
                KEYCODE_MEDIA_STOP -> stop()
                KEYCODE_MEDIA_PLAY_PAUSE -> if (isPlaying) stop() else play()
                KEYCODE_MEDIA_PREVIOUS -> seekToPrevious()
                KEYCODE_MEDIA_NEXT -> seekToNext()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tasks.clear()
        stop()

        if (!running.get()) return
        running.set(false)

        serviceThread!!.join()

        stopForegroundRemoveNotification()
        mediaSession.release()

        super.onDestroy()
        Timber.d("Stopped Jukebox Service")
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    fun requestStop() {
        stopSelf()
    }

    private fun updateNotification() {
        val notification = notificationProvider.createNotification(
            mediaSession,
            ImmutableList.of(),
            JukeboxNotificationActionFactory()
        ) {}
        notificationManagerCompat.notify(notification.notificationId, notification.notification)
    }

    companion object {
        val running = AtomicBoolean()
        private var startFuture: SettableFuture<JukeboxMediaPlayer>? = null

        @JvmStatic
        fun requestStart(): ListenableFuture<JukeboxMediaPlayer>? {
            if (running.get()) return null
            startFuture = SettableFuture.create()
            val context = applicationContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(
                    Intent(context, JukeboxMediaPlayer::class.java)
                )
            } else {
                context.startService(Intent(context, JukeboxMediaPlayer::class.java))
            }
            Timber.i("JukeboxMediaPlayer starting...")
            return startFuture
        }
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun getCurrentMediaItem(): MediaItem? {
        if (playlist.isEmpty()) return null
        if (currentIndex < 0 || currentIndex >= playlist.size) return null
        return playlist[currentIndex]
    }

    override fun getCurrentMediaItemIndex(): Int {
        return currentIndex
    }

    override fun getCurrentPeriodIndex(): Int {
        return currentIndex
    }

    override fun getContentPosition(): Long {
        return currentPosition
    }

    override fun play() {
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        startStatusUpdate()
        tasks.add(Start())
    }

    override fun seekTo(positionMs: Long) {
        seekTo(currentIndex, positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        tasks.remove(Skip::class.java)
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        startStatusUpdate()
        val positionSeconds = (positionMs / 1000).toInt()
        if (jukeboxStatus != null) {
            jukeboxStatus!!.positionSeconds = positionSeconds
        }
        tasks.add(Skip(mediaItemIndex, positionSeconds))
        currentIndex = mediaItemIndex
    }

    override fun seekBack() {
        seekTo(0L.coerceAtMost((jukeboxStatus?.positionSeconds ?: 0) - SEEK_INCREMENT_SECONDS))
    }

    override fun seekForward() {
        seekTo((jukeboxStatus?.positionSeconds ?: 0) + SEEK_INCREMENT_SECONDS)
    }

    override fun prepare() {}

    override fun isPlaying(): Boolean {
        return jukeboxStatus?.isPlaying ?: false
    }

    override fun getPlaybackState(): Int {
        return when (jukeboxStatus?.isPlaying) {
            true -> Player.STATE_READY
            null, false -> Player.STATE_IDLE
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        val commandsBuilder = Player.Commands.Builder().addAll(
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_GET_VOLUME
        )
        if (isPlaying) commandsBuilder.add(Player.COMMAND_STOP)
        if (playlist.isNotEmpty()) {
            commandsBuilder.addAll(
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_MEDIA_ITEMS_METADATA,
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            )
            if (currentIndex > 0) commandsBuilder.addAll(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
            if (currentIndex < playlist.size - 1) commandsBuilder.addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
        }
        return commandsBuilder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return availableCommands.contains(command)
    }

    override fun getPlayWhenReady(): Boolean {
        return isPlaying
    }

    override fun pause() {
        stop()
    }

    override fun stop() {
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        stopStatusUpdate()
        tasks.add(Stop())
    }

    override fun getCurrentTimeline(): Timeline {
        return PlaylistTimeline(playlist)
    }

    override fun getMediaItemCount(): Int {
        return playlist.size
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        if (playlist.size == 0) return MediaItem.EMPTY
        if (index < 0 || index >= playlist.size) return MediaItem.EMPTY
        return playlist[index]
    }

    override fun getShuffleModeEnabled(): Boolean {
        return false
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}

    override fun setVolume(volume: Float) {
        gain = volume
        tasks.remove(SetGain::class.java)
        tasks.add(SetGain(volume))
        val context = applicationContext()
        if (volumeToast == null) volumeToast = VolumeToast(context)
        volumeToast!!.setVolume(volume)
    }

    override fun getVolume(): Float {
        return gain
    }

    override fun getDeviceVolume(): Int {
        return (gain * 100).toInt()
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        playlist.addAll(index, mediaItems)
        updatePlaylist()
    }

    override fun getBufferedPercentage(): Int {
        return 0
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        if (playlist.size == 0) return
        if (currentIndex < 0 || currentIndex >= playlist.size) return
        if (newIndex < 0 || newIndex >= playlist.size) return

        val insertIndex = if (newIndex < currentIndex) newIndex else newIndex - 1
        val item = playlist.removeAt(currentIndex)
        playlist.add(insertIndex, item)
        updatePlaylist()
    }

    override fun removeMediaItem(index: Int) {
        if (playlist.size == 0) return
        if (index < 0 || index >= playlist.size) return
        playlist.removeAt(index)
        updatePlaylist()
    }

    override fun clearMediaItems() {
        playlist.clear()
        currentIndex = 0
        updatePlaylist()
    }

    override fun getRepeatMode(): Int {
        return Player.REPEAT_MODE_OFF
    }

    override fun setRepeatMode(repeatMode: Int) {}

    override fun getCurrentPosition(): Long {
        return positionSeconds * 1000L
    }

    override fun getDuration(): Long {
        if (playlist.isEmpty()) return 0
        if (currentIndex < 0 || currentIndex >= playlist.size) return 0

        return (playlist[currentIndex].mediaMetadata.extras?.getInt("duration") ?: 0)
            .toLong() * 1000
    }

    override fun getContentDuration(): Long {
        return duration
    }

    override fun getMediaMetadata(): MediaMetadata {
        if (playlist.isEmpty()) return MediaMetadata.EMPTY
        if (currentIndex < 0 || currentIndex >= playlist.size) return MediaMetadata.EMPTY

        return playlist[currentIndex].mediaMetadata
    }

    override fun seekToNext() {
        if (currentIndex < 0 || currentIndex >= playlist.size) return
        currentIndex++
        seekTo(currentIndex, 0)
    }

    override fun seekToPrevious() {
        if ((jukeboxStatus?.positionSeconds ?: 0) > SEEK_START_AFTER_SECONDS) {
            seekTo(currentIndex, 0)
            return
        }
        if (currentIndex <= 0) return
        currentIndex--
        seekTo(currentIndex, 0)
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        playlist.clear()
        playlist.addAll(mediaItems)
        updatePlaylist()
        seekTo(startIndex, startPositionMs)
    }

    private fun startProcessTasks() {
        serviceThread = object : Thread() {
            override fun run() {
                processTasks()
            }
        }
        (serviceThread as Thread).start()
    }

    @Synchronized
    private fun startStatusUpdate() {
        stopStatusUpdate()
        val updateTask = Runnable {
            tasks.remove(GetStatus::class.java)
            tasks.add(GetStatus())
        }
        statusUpdateFuture = executorService.scheduleWithFixedDelay(
            updateTask,
            STATUS_UPDATE_INTERVAL_SECONDS,
            STATUS_UPDATE_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    @Synchronized
    private fun stopStatusUpdate() {
        if (statusUpdateFuture != null) {
            statusUpdateFuture!!.cancel(false)
            statusUpdateFuture = null
        }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun processTasks() {
        Timber.d("JukeboxMediaPlayer processTasks starting")
        while (true) {
            // Sleep a bit to spare processor time if we loop a lot
            sleepQuietly(10)
            // This is only necessary if Ultrasonic goes offline sooner than the thread stops
            if (isOffline()) continue
            var task: JukeboxTask? = null
            try {
                task = tasks.poll()
                // If running is false, exit when the queue is empty
                if (task == null && !running.get()) break
                if (task == null) continue
                Timber.v("JukeBoxMediaPlayer processTasks processes Task %s", task::class)
                val status = task.execute()
                onStatusUpdate(status)
            } catch (x: Throwable) {
                onError(task, x)
            }
        }
        Timber.d("JukeboxMediaPlayer processTasks stopped")
    }

    private fun onStatusUpdate(jukeboxStatus: JukeboxStatus) {
        timeOfLastUpdate.set(System.currentTimeMillis())
        previousJukeboxStatus = this.jukeboxStatus
        this.jukeboxStatus = jukeboxStatus
        currentIndex = jukeboxStatus.currentPlayingIndex ?: currentIndex

        if (jukeboxStatus.isPlaying != previousJukeboxStatus?.isPlaying) {
            Handler(Looper.getMainLooper()).post {
                listeners.forEach {
                    it.onPlaybackStateChanged(
                        if (jukeboxStatus.isPlaying) Player.STATE_READY else Player.STATE_IDLE
                    )
                    it.onIsPlayingChanged(jukeboxStatus.isPlaying)
                }
            }
        }

        if (jukeboxStatus.currentPlayingIndex != previousJukeboxStatus?.currentPlayingIndex) {
            currentIndex = jukeboxStatus.currentPlayingIndex ?: 0
            val currentMedia =
                if (currentIndex > 0 && currentIndex < playlist.size) playlist[currentIndex]
                else MediaItem.EMPTY
            Handler(Looper.getMainLooper()).post {
                listeners.forEach {
                    it.onMediaItemTransition(
                        currentMedia,
                        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    )
                }
            }
        }

        updateNotification()
    }

    private fun onError(task: JukeboxTask?, x: Throwable) {
        if (x is ApiNotSupportedException && task !is Stop) {
            Handler(Looper.getMainLooper()).post {
                listeners.forEach {
                    it.onPlayerError(
                        PlaybackException(
                            "Jukebox server too old",
                            null,
                            R.string.download_jukebox_server_too_old
                        )
                    )
                }
            }
        } else if (x is OfflineException && task !is Stop) {
            Handler(Looper.getMainLooper()).post {
                listeners.forEach {
                    it.onPlayerError(
                        PlaybackException(
                            "Jukebox offline",
                            null,
                            R.string.download_jukebox_offline
                        )
                    )
                }
            }
        } else if (x is SubsonicRESTException && x.code == 50 && task !is Stop) {
            Handler(Looper.getMainLooper()).post {
                listeners.forEach {
                    it.onPlayerError(
                        PlaybackException(
                            "Jukebox not authorized",
                            null,
                            R.string.download_jukebox_not_authorized
                        )
                    )
                }
            }
        } else {
            Timber.e(x, "Failed to process jukebox task")
        }
    }

    private fun updatePlaylist() {
        if (!running.get()) return
        tasks.remove(Skip::class.java)
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        val ids: MutableList<String> = ArrayList()
        for (item in playlist) {
            ids.add(item.mediaId)
        }
        tasks.add(SetPlaylist(ids))
        Handler(Looper.getMainLooper()).post {
            listeners.forEach {
                it.onTimelineChanged(
                    PlaylistTimeline(playlist),
                    Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
                )
            }
        }
    }

    private val musicService: MusicService
        get() = getMusicService()

    private val positionSeconds: Int
        get() {
            if (jukeboxStatus == null ||
                jukeboxStatus!!.positionSeconds == null ||
                timeOfLastUpdate.get() == 0L
            ) {
                return 0
            }
            if (jukeboxStatus!!.isPlaying) {
                val secondsSinceLastUpdate =
                    ((System.currentTimeMillis() - timeOfLastUpdate.get()) / 1000L).toInt()
                return jukeboxStatus!!.positionSeconds!! + secondsSinceLastUpdate
            }
            return jukeboxStatus!!.positionSeconds!!
        }

    private class TaskQueue {
        private val queue = LinkedBlockingQueue<JukeboxTask>()
        fun add(jukeboxTask: JukeboxTask) {
            queue.add(jukeboxTask)
        }

        fun poll(): JukeboxTask? {
            return queue.poll(QUEUE_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
        }

        fun remove(taskClass: Class<out JukeboxTask?>) {
            try {
                val iterator = queue.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()
                    if (taskClass == task.javaClass) {
                        iterator.remove()
                    }
                }
            } catch (x: Throwable) {
                Timber.w(x, "Failed to clean-up task queue.")
            }
        }

        fun clear() {
            queue.clear()
        }
    }

    private abstract class JukeboxTask {
        @Throws(Exception::class)
        abstract fun execute(): JukeboxStatus
        override fun toString(): String {
            return javaClass.simpleName
        }
    }

    private inner class GetStatus : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.getJukeboxStatus()
        }
    }

    private inner class SetPlaylist(private val ids: List<String>) :
        JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.updateJukeboxPlaylist(ids)
        }
    }

    private inner class Skip(
        private val index: Int,
        private val offsetSeconds: Int
    ) : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.skipJukebox(index, offsetSeconds)
        }
    }

    private inner class Stop : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.stopJukebox()
        }
    }

    private inner class Start : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.startJukebox()
        }
    }

    private inner class SetGain(private val gain: Float) : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.setJukeboxGain(gain)
        }
    }

    @SuppressLint("InflateParams")
    private class VolumeToast(context: Context) : Toast(context) {
        private val progressBar: ProgressBar
        fun setVolume(volume: Float) {
            progressBar.progress = (100 * volume).roundToInt()
            show()
        }

        init {
            duration = LENGTH_SHORT
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.jukebox_volume, null)
            progressBar = view.findViewById<View>(R.id.jukebox_volume_progress_bar) as ProgressBar
            setView(view)
            setGravity(Gravity.TOP, 0, 0)
        }
    }

    // The constants below are necessary so a MediaSession can be built from the Jukebox Service
    override fun isCurrentMediaItemDynamic(): Boolean {
        return false
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return SEEK_START_AFTER_SECONDS * 1000L
    }

    override fun getSeekBackIncrement(): Long {
        return SEEK_INCREMENT_SECONDS * 1000L
    }

    override fun getSeekForwardIncrement(): Long {
        return SEEK_INCREMENT_SECONDS * 1000L
    }

    override fun isLoading(): Boolean {
        return false
    }

    override fun getPlaybackSuppressionReason(): Int {
        return Player.PLAYBACK_SUPPRESSION_REASON_NONE
    }

    override fun isDeviceMuted(): Boolean {
        return false
    }

    override fun getCurrentCues(): CueGroup {
        return CueGroup.EMPTY
    }

    override fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.DEFAULT
    }

    override fun getVideoSize(): VideoSize {
        return VideoSize(0, 0)
    }

    override fun getContentBufferedPosition(): Long {
        return bufferedPosition
    }

    override fun getCurrentLiveOffset(): Long {
        return C.TIME_UNSET
    }

    override fun getTotalBufferedDuration(): Long {
        return 0
    }

    override fun isPlayingAd(): Boolean {
        return false
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return C.INDEX_UNSET
    }

    override fun getCurrentAdGroupIndex(): Int {
        return C.INDEX_UNSET
    }

    override fun canAdvertiseSession(): Boolean {
        return true
    }

    override fun getApplicationLooper(): Looper {
        return applicationContext().mainLooper
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return MediaMetadata.EMPTY
    }

    override fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, 0, 1)
    }

    override fun getPlayerError(): PlaybackException? {
        return null
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return PlaybackParameters(1F, 1F)
    }

    override fun getBufferedPosition(): Long {
        return 0
    }
}
