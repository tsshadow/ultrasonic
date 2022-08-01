/*
 * MediaPlayerController.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.playback.PlaybackService
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X1
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X2
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X3
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X4
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.MainThreadExecutor
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.setPin
import org.moire.ultrasonic.util.toMediaItem
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

/**
 * The implementation of the Media Player Controller.
 * This class contains everything that is necessary for the Application UI
 * to control the Media Player implementation.
 */
@Suppress("TooManyFunctions")
class MediaPlayerController(
    private val playbackStateSerializer: PlaybackStateSerializer,
    private val externalStorageMonitor: ExternalStorageMonitor,
    private val downloader: Downloader,
    val context: Context
) : KoinComponent {

    private var created = false
    var suggestedPlaylistName: String? = null
    var keepScreenOn = false
    var showVisualization = false
    private var autoPlayStart = false

    private val scrobbler = Scrobbler()

    private val jukeboxMediaPlayer: JukeboxMediaPlayer by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private var mainScope = CoroutineScope(Dispatchers.Main)

    private var sessionToken =
        SessionToken(context, ComponentName(context, PlaybackService::class.java))

    private var mediaControllerFuture = MediaController.Builder(
        context,
        sessionToken
    ).buildAsync()

    var controller: MediaController? = null

    private lateinit var listeners: Player.Listener

    private var cachedMediaItem: MediaItem? = null

    fun onCreate(onCreated: () -> Unit) {
        if (created) return
        externalStorageMonitor.onCreate { reset() }
        isJukeboxEnabled = activeServerProvider.getActiveServer().jukeboxByDefault

        mediaControllerFuture.addListener({
            controller = mediaControllerFuture.get()

            Timber.i("MediaController Instance received")

            listeners = object : Player.Listener {

                /*
                 * Log all events
                 */
                override fun onEvents(player: Player, events: Player.Events) {
                    for (i in 0 until events.size()) {
                        Timber.i("Media3 Event, event type: %s", events[i])
                    }
                }

                /*
                 * This will be called everytime the playlist has changed.
                 * We run the event through RxBus in order to throttle them
                 */
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    RxBus.playlistPublisher.onNext(playlist.map(MediaItem::toTrack))
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    playerStateChangedHandler()
                    publishPlaybackState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playerStateChangedHandler()
                    publishPlaybackState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    clearBookmark()
                    cachedMediaItem = mediaItem
                    publishPlaybackState()
                }

                /*
                 * If the same item is contained in a playlist multiple times directly after each
                 * other, Media3 on emits a PositionDiscontinuity event.
                 * Can be removed if https://github.com/androidx/media/issues/68 is fixed.
                 */
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    playerStateChangedHandler()
                    publishPlaybackState()
                }
            }

            controller?.addListener(listeners)

            onCreated()

            Timber.i("MediaPlayerController creation complete")

            // controller?.play()
        }, MoreExecutors.directExecutor())

        rxBusSubscription += RxBus.activeServerChangeObservable.subscribe {
            // Update the Jukebox state when the active server has changed
            isJukeboxEnabled = activeServerProvider.getActiveServer().jukeboxByDefault
        }

        rxBusSubscription += RxBus.throttledPlaylistObservable.subscribe {
            // Even though Rx should launch on the main thread it doesn't always :(
            mainScope.launch {
                serializeCurrentSession()
            }
        }

        rxBusSubscription += RxBus.throttledPlayerStateObservable.subscribe {
            // Even though Rx should launch on the main thread it doesn't always :(
            mainScope.launch {
                serializeCurrentSession()
            }
        }

        rxBusSubscription += RxBus.stopServiceCommandObservable.subscribe {
            // Clear the widget when we stop the service
            updateWidget(null)
        }

        rxBusSubscription += RxBus.shutdownCommandObservable.subscribe {
            playbackStateSerializer.serializeNow(
                playlist.map { it.toTrack() },
                currentMediaItemIndex,
                playerPosition,
                isShufflePlayEnabled,
                repeatMode
            )
            clear(false)
            onDestroy()
        }

        created = true
        Timber.i("MediaPlayerController started")
    }

    private fun playerStateChangedHandler() {

        val currentPlaying = controller?.currentMediaItem?.toTrack() ?: return

        when (playbackState) {
            Player.STATE_READY -> {
                if (isPlaying) {
                    scrobbler.scrobble(currentPlaying, false)
                }
            }
            Player.STATE_ENDED -> {
                scrobbler.scrobble(currentPlaying, true)
            }
        }

        // Update widget
        updateWidget(currentPlaying)
    }

    private fun clearBookmark() {
        // This method is called just before we update the cachedMediaItem,
        // so in fact cachedMediaItem will refer to the track that has just finished.
        if (cachedMediaItem != null) {
            val song = cachedMediaItem!!.toTrack()
            if (song.bookmarkPosition > 0 && Settings.shouldClearBookmark) {
                val musicService = getMusicService()
                try {
                    musicService.deleteBookmark(song.id)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun publishPlaybackState() {
        val newState = RxBus.StateWithTrack(
            track = currentMediaItem?.let { it.toTrack() },
            index = currentMediaItemIndex,
            isPlaying = isPlaying,
            state = playbackState
        )
        RxBus.playerStatePublisher.onNext(newState)
        Timber.i("New PlaybackState: %s", newState)
    }

    private fun updateWidget(song: Track?) {
        val context = UApp.applicationContext()

        UltrasonicAppWidgetProvider4X1.instance?.notifyChange(context, song, isPlaying, false)
        UltrasonicAppWidgetProvider4X2.instance?.notifyChange(context, song, isPlaying, true)
        UltrasonicAppWidgetProvider4X3.instance?.notifyChange(context, song, isPlaying, false)
        UltrasonicAppWidgetProvider4X4.instance?.notifyChange(context, song, isPlaying, false)
    }

    fun onDestroy() {
        if (!created) return

        // First stop listening to events
        rxBusSubscription.dispose()
        controller?.removeListener(listeners)

        // Shutdown the rest
        val context = UApp.applicationContext()
        externalStorageMonitor.onDestroy()
        context.stopService(Intent(context, DownloadService::class.java))
        downloader.onDestroy()
        created = false
        Timber.i("MediaPlayerController destroyed")
    }

    @Synchronized
    fun restore(
        state: PlaybackState,
        autoPlay: Boolean,
        newPlaylist: Boolean
    ) {
        val insertionMode = if (newPlaylist) InsertionMode.CLEAR
        else InsertionMode.APPEND

        addToPlaylist(
            state.songs,
            cachePermanently = false,
            autoPlay = false,
            shuffle = false,
            insertionMode = insertionMode
        )

        repeatMode = state.repeatMode
        isShufflePlayEnabled = state.shufflePlay

        if (state.currentPlayingIndex != -1) {
            if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.skip(
                    state.currentPlayingIndex,
                    state.currentPlayingPosition / 1000
                )
            } else {
                seekTo(state.currentPlayingIndex, state.currentPlayingPosition)
            }

            prepare()

            if (autoPlay) {
                play()
            }

            autoPlayStart = false
        }
    }

    @Synchronized
    fun play(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.play()
    }

    @Synchronized
    fun play() {
        if (jukeboxMediaPlayer.isEnabled) {
            jukeboxMediaPlayer.start()
        } else {
            controller?.prepare()
            controller?.play()
        }
    }

    @Synchronized
    fun prepare() {
        controller?.prepare()
    }

    @Synchronized
    fun resumeOrPlay() {
        controller?.play()
    }

    @Synchronized
    fun togglePlayPause() {
        if (playbackState == Player.STATE_IDLE) autoPlayStart = true
        if (controller?.isPlaying == true) {
            controller?.pause()
        } else {
            controller?.play()
        }
    }

    @Synchronized
    fun seekTo(position: Int) {
        if (controller?.currentTimeline?.isEmpty != false) return
        Timber.i("SeekTo: %s", position)
        controller?.seekTo(position.toLong())
    }

    @Synchronized
    fun seekTo(index: Int, position: Int) {
        // This case would throw an exception in Media3. It can happen when an inconsistent state is saved.
        if (controller?.currentTimeline?.isEmpty != false ||
            index >= controller!!.currentTimeline.windowCount
        ) return
        Timber.i("SeekTo: %s %s", index, position)
        controller?.seekTo(index, position.toLong())
    }

    @Synchronized
    fun pause() {
        if (jukeboxMediaPlayer.isEnabled) {
            jukeboxMediaPlayer.stop()
        } else {
            controller?.pause()
        }
    }

    @Synchronized
    fun stop() {
        if (jukeboxMediaPlayer.isEnabled) {
            jukeboxMediaPlayer.stop()
        } else {
            controller?.stop()
        }
    }

    @Synchronized
    fun addToPlaylist(
        songs: List<Track>,
        cachePermanently: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean,
        insertionMode: InsertionMode
    ) {
        var insertAt = 0

        when (insertionMode) {
            InsertionMode.CLEAR -> clear()
            InsertionMode.APPEND -> insertAt = mediaItemCount
            InsertionMode.AFTER_CURRENT -> insertAt = currentMediaItemIndex + 1
        }

        val mediaItems: List<MediaItem> = songs.map {
            val result = it.toMediaItem()
            if (cachePermanently) result.setPin(true)
            result
        }

        controller?.addMediaItems(insertAt, mediaItems)

        jukeboxMediaPlayer.updatePlaylist()

        if (shuffle) isShufflePlayEnabled = true

        prepare()

        if (autoPlay) {
            play(0)
        }
    }

    @Synchronized
    fun downloadBackground(songs: List<Track?>?, save: Boolean) {
        if (songs == null) return
        val filteredSongs = songs.filterNotNull()
        downloader.downloadBackground(filteredSongs, save)
    }

    fun stopJukeboxService() {
        jukeboxMediaPlayer.stopJukeboxService()
    }

    @set:Synchronized
    var isShufflePlayEnabled: Boolean
        get() = controller?.shuffleModeEnabled == true
        set(enabled) {
            controller?.shuffleModeEnabled = enabled
            // Changing Shuffle may change the playlist, so the next tracks may need to be downloaded
            downloader.checkDownloads()
        }

    @Synchronized
    fun toggleShuffle(): Boolean {
        isShufflePlayEnabled = !isShufflePlayEnabled
        return isShufflePlayEnabled
    }

    val bufferedPercentage: Int
        get() = controller?.bufferedPercentage ?: 0

    @Synchronized
    fun moveItemInPlaylist(oldPos: Int, newPos: Int) {
        controller?.moveMediaItem(oldPos, newPos)
    }

    @set:Synchronized
    var repeatMode: Int
        get() = controller?.repeatMode ?: 0
        set(newMode) {
            controller?.repeatMode = newMode
        }

    @Synchronized
    @JvmOverloads
    fun clear(serialize: Boolean = true) {

        controller?.clearMediaItems()

        if (controller != null && serialize) {
            playbackStateSerializer.serializeAsync(
                listOf(), -1, 0, isShufflePlayEnabled, repeatMode
            )
        }

        jukeboxMediaPlayer.updatePlaylist()
    }

    @Synchronized
    fun clearDownloads() {
        downloader.clearActiveDownloads()
        downloader.clearBackground()
    }

    @Synchronized
    fun removeIncompleteTracksFromPlaylist() {
        val list = playlist.toList()
        var removed = 0
        for ((index, item) in list.withIndex()) {
            val state = downloader.getDownloadState(item.toTrack())

            // The track is not downloaded, remove it
            if (state != DownloadStatus.DONE && state != DownloadStatus.PINNED) {
                removeFromPlaylist(index - removed)
                removed++
            }
        }
    }

    @Synchronized
    fun removeFromPlaylist(position: Int) {

        controller?.removeMediaItem(position)

        jukeboxMediaPlayer.updatePlaylist()
    }

    @Synchronized
    private fun serializeCurrentSession() {
        // Don't serialize invalid sessions
        if (currentMediaItemIndex == -1) return

        playbackStateSerializer.serializeAsync(
            songs = playlist.map { it.toTrack() },
            currentPlayingIndex = currentMediaItemIndex,
            currentPlayingPosition = playerPosition,
            isShufflePlayEnabled,
            repeatMode
        )
    }

    @Synchronized
    // TODO: Make it require not null
    fun delete(tracks: List<Track?>) {
        for (track in tracks.filterNotNull()) {
            downloader.delete(track)
        }
    }

    @Synchronized
    // TODO: Make it require not null
    fun unpin(tracks: List<Track?>) {
        for (track in tracks.filterNotNull()) {
            downloader.unpin(track)
        }
    }

    @Synchronized
    fun previous() {
        controller?.seekToPrevious()
    }

    @Synchronized
    operator fun next() {
        controller?.seekToNext()
    }

    @Synchronized
    fun reset() {
        controller?.clearMediaItems()
    }

    @get:Synchronized
    val playerPosition: Int
        get() {
            return if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.positionSeconds * 1000
            } else {
                controller?.currentPosition?.toInt() ?: 0
            }
        }

    @get:Synchronized
    val playerDuration: Int
        get() {
            return controller?.duration?.toInt() ?: return 0
        }

    val playbackState: Int
        get() = controller?.playbackState ?: 0

    val isPlaying: Boolean
        get() = controller?.isPlaying ?: false

    @set:Synchronized
    var isJukeboxEnabled: Boolean
        get() = jukeboxMediaPlayer.isEnabled
        set(jukeboxEnabled) {
            jukeboxMediaPlayer.isEnabled = jukeboxEnabled

            if (jukeboxEnabled) {
                jukeboxMediaPlayer.startJukeboxService()
                reset()

                // Cancel current downloads
                downloader.clearActiveDownloads()
            } else {
                jukeboxMediaPlayer.stopJukeboxService()
            }
        }

    /**
     * This function calls the music service directly and
     * therefore can't be called from the main thread
     */
    val isJukeboxAvailable: Boolean
        get() {
            try {
                val username = activeServerProvider.getActiveServer().userName
                return getMusicService().getUser(username).jukeboxRole
            } catch (all: Exception) {
                Timber.w(all, "Error getting user information")
            }
            return false
        }

    fun adjustJukeboxVolume(up: Boolean) {
        jukeboxMediaPlayer.adjustVolume(up)
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume
    }

    fun toggleSongStarred() {
        if (currentMediaItem == null) return
        val song = currentMediaItem!!.toTrack()

        controller?.setRating(
            HeartRating(!song.starred)
        ).let {
            Futures.addCallback(
                it,
                object : FutureCallback<SessionResult> {
                    override fun onSuccess(result: SessionResult?) {
                        // Trigger an update
                        // TODO Update Metadata of MediaItem...
                        // localMediaPlayer.setCurrentPlaying(localMediaPlayer.currentPlaying)
                        song.starred = !song.starred
                    }

                    override fun onFailure(t: Throwable) {
                        Toast.makeText(
                            context,
                            "There was an error updating the rating",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                MainThreadExecutor()
            )
        }
    }

    @Suppress("TooGenericExceptionCaught") // The interface throws only generic exceptions
    fun setSongRating(rating: Int) {
        if (!Settings.useFiveStarRating) return
        if (currentMediaItem == null) return
        val song = currentMediaItem!!.toTrack()
        song.userRating = rating
        Thread {
            try {
                getMusicService().setRating(song.id, rating)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }.start()
        // TODO this would be better handled with a Rx command
        // updateNotification()
    }

    val currentMediaItem: MediaItem?
        get() = controller?.currentMediaItem

    val currentMediaItemIndex: Int
        get() = controller?.currentMediaItemIndex ?: -1

    val mediaItemCount: Int
        get() = controller?.mediaItemCount ?: 0

    val playlistSize: Int
        get() = controller?.currentTimeline?.windowCount ?: 0

    val playlist: List<MediaItem>
        get() {
            return getPlayList(false)
        }

    val playlistInPlayOrder: List<MediaItem>
        get() {
            return getPlayList(controller?.shuffleModeEnabled ?: false)
        }

    fun getNextPlaylistItemsInPlayOrder(count: Int? = null): List<MediaItem> {
        return getPlayList(
            controller?.shuffleModeEnabled ?: false,
            controller?.currentMediaItemIndex,
            count
        )
    }

    private fun getPlayList(
        shuffle: Boolean,
        firstIndex: Int? = null,
        count: Int? = null
    ): List<MediaItem> {
        if (controller?.currentTimeline == null) return emptyList()
        if (controller!!.currentTimeline.windowCount < 1) return emptyList()
        val timeline = controller!!.currentTimeline

        val playlist: MutableList<MediaItem> = mutableListOf()
        var i = firstIndex ?: timeline.getFirstWindowIndex(false)
        if (i == C.INDEX_UNSET) return emptyList()

        while (i != C.INDEX_UNSET && (count != playlist.count())) {
            val window = timeline.getWindow(i, Timeline.Window())
            playlist.add(window.mediaItem)
            i = timeline.getNextWindowIndex(i, REPEAT_MODE_OFF, shuffle)
        }
        return playlist
    }

    val playListDuration: Long
        get() = playlist.fold(0) { i, file ->
            i + (file.mediaMetadata.extras?.getInt("duration") ?: 0)
        }

    init {
        Timber.i("MediaPlayerController instance initiated")
    }

    enum class InsertionMode {
        CLEAR, APPEND, AFTER_CURRENT
    }
}
