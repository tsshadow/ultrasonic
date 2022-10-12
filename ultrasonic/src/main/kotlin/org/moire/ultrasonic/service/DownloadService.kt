/*
 * MediaPlayerService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.util.PriorityQueue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadState.Companion.isFinalState
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.FileUtil.getCompleteFile
import org.moire.ultrasonic.util.FileUtil.getPartialFile
import org.moire.ultrasonic.util.FileUtil.getPinnedFile
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.SimpleServiceBinder
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.stopForegroundRemoveNotification
import timber.log.Timber

private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic"
private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service"
private const val NOTIFICATION_ID = 3033

private const val CHECK_INTERVAL = 5000L

/**
 * Android Foreground service which is used to download tracks even when the app is not visible
 *
 * "A foreground service is a service that the user is
 * actively aware of and isn’t a candidate for the system to kill when low on memory."
 *
 * TODO: Migrate this to use the Media3 DownloadHelper
 */
class DownloadService : Service(), KoinComponent {
    private val storageMonitor: ExternalStorageMonitor by inject()
    private val binder: IBinder = SimpleServiceBinder(this)

    private var isInForeground = false
    private var wifiLock: WifiManager.WifiLock? = null
    private var isShuttingDown = false
    private var retrying = false

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        // Create Notification Channel
        createNotificationChannel()
        updateNotification()

        if (wifiLock == null) {
            wifiLock = Util.createWifiLock(toString())
            wifiLock?.acquire()
        }

        startFuture?.set(this)
        Timber.i("DownloadService created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        startFuture = null

        isShuttingDown = true
        isInForeground = false
        stopForegroundRemoveNotification()

        wifiLock?.release()
        wifiLock = null

        clearDownloads()
        observableDownloads.value = listOf()

        Timber.i("DownloadService destroyed")
    }

    fun addTracks(tracks: List<DownloadableTrack>) {
        downloadQueue.addAll(tracks)
        tracks.forEach { postState(it.track, DownloadState.QUEUED) }
        processNextTracks()
    }

    private fun processNextTracks() {
        retrying = false
        if (
            !Util.isNetworkConnected() ||
            !Util.isExternalStoragePresent() ||
            !storageMonitor.isExternalStorageAvailable
        ) {
            retryProcessNextTracks()
            return
        }

        Timber.v("DownloadService processNextTracks checking downloads")
        var listChanged = false

        // Fill up active List with waiting tasks
        while (activelyDownloading.size < Settings.parallelDownloads && downloadQueue.size > 0) {
            val task = downloadQueue.remove()
            val downloadTask = DownloadTask(task) { downloadableTrack, downloadState, progress ->
                downloadStateChangedCallback(downloadableTrack, downloadState, progress)
            }
            activelyDownloading[task] = downloadTask
            FileUtil.createDirectoryForParent(task.pinnedFile)
            activelyDownloading[task]?.start()

            listChanged = true
        }

        // Stop Executor service when done downloading
        if (activelyDownloading.isEmpty()) {
            stopSelf()
        }

        if (listChanged) {
            updateLiveData()
        }
    }

    private fun retryProcessNextTracks() {
        if (isShuttingDown || retrying) return
        retrying = true
        Handler(Looper.getMainLooper()).postDelayed(
            { if (retrying) processNextTracks() },
            CHECK_INTERVAL
        )
    }

    private fun downloadStateChangedCallback(
        item: DownloadableTrack,
        downloadState: DownloadState,
        progress: Int?
    ) {
        postState(item.track, downloadState, progress)

        if (downloadState.isFinalState()) {
            activelyDownloading.remove(item)
            processNextTracks()
        }

        when (downloadState) {
            DownloadState.FAILED -> {
                downloadQueue.remove(item)
                failedList.add(item)
            }
            DownloadState.RETRYING -> {
                item.tryCount++
                downloadQueue.add(item)
            }
            else -> {}
        }
    }

    private fun updateLiveData() {
        val temp: MutableList<Track> = ArrayList()
        temp.addAll(activelyDownloading.keys.map { x -> x.track })
        temp.addAll(downloadQueue.map { x -> x.track })
        observableDownloads.postValue(temp.distinct().sorted())
    }

    private fun clearDownloads() {
        // Clear the pending queue
        while (!downloadQueue.isEmpty()) {
            postState(downloadQueue.remove().track, DownloadState.IDLE)
        }
        // Cancel all active downloads
        for (download in activelyDownloading) {
            download.value.cancel()
        }
        activelyDownloading.clear()
        updateLiveData()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // The suggested importance of a startForeground service notification is IMPORTANCE_LOW
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )

            channel.lightColor = android.R.color.holo_blue_dark
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)

            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // We should use a single notification builder, otherwise the notification may not be updated
    // Set some values that never change
    private val notificationBuilder: NotificationCompat.Builder by lazy {
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ultrasonic)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(Util.getPendingIntentToShowPlayer(this))
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun updateNotification() {

        val notification = buildForegroundNotification()

        if (isInForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            } else {
                val manager = NotificationManagerCompat.from(this)
                manager.notify(NOTIFICATION_ID, notification)
            }
            Timber.v("Updated notification")
        } else {
            startForeground(NOTIFICATION_ID, notification)
            isInForeground = true
            Timber.v("Created Foreground notification")
        }
    }

    /**
     * This method builds a notification, reusing the Notification Builder if possible
     */
    @Suppress("SpreadOperator")
    private fun buildForegroundNotification(): Notification {
        notificationBuilder.setContentTitle(getString(R.string.notification_downloading_title))
        return notificationBuilder.build()
    }

    @Suppress("MagicNumber", "NestedBlockDepth")
    companion object {

        private var startFuture: SettableFuture<DownloadService>? = null

        private val downloadQueue = PriorityQueue<DownloadableTrack>()
        private val activelyDownloading = mutableMapOf<DownloadableTrack, DownloadTask>()
        private val failedList = mutableListOf<DownloadableTrack>()

        // The generic list models expect a LiveData, so even though we are using Rx for many events
        // surrounding playback the list of Downloads is published as LiveData.
        val observableDownloads = MutableLiveData<List<Track>>()

        private var backgroundPriorityCounter = 100

        fun download(
            tracks: List<Track>,
            save: Boolean,
            isHighPriority: Boolean = false
        ) {
            // First handle and filter out those tracks that are already completed
            var filteredTracks: List<Track>
            if (save) {
                tracks.filter { Storage.isPathExists(it.getCompleteFile()) }.forEach { track ->
                    Storage.getFromPath(track.getCompleteFile())?.let {
                        try {
                            Storage.rename(it, track.getPinnedFile())
                        } catch (ignored: FileAlreadyExistsException) {
                            // Play console has revealed a crash when for some reason both files exist
                            Storage.delete(it.path)
                        }
                        postState(track, DownloadState.PINNED)
                    }
                }
                filteredTracks = tracks.filter { !Storage.isPathExists(it.getPinnedFile()) }
            } else {
                tracks.filter { Storage.isPathExists(it.getPinnedFile()) }.forEach { track ->
                    Storage.getFromPath(track.getPinnedFile())?.let {
                        try {
                            Storage.rename(it, track.getCompleteFile())
                        } catch (ignored: FileAlreadyExistsException) {
                            // Play console has revealed a crash when for some reason both files exist
                            Storage.delete(it.path)
                        }
                        postState(track, DownloadState.DONE)
                    }
                }
                filteredTracks = tracks.filter { !Storage.isPathExists(it.getCompleteFile()) }
            }

            // Update Pinned flag of items in progress
            downloadQueue.filter { item -> tracks.any { it.id == item.id } }
                .forEach { it.pinned = save }
            activelyDownloading.filter { item -> tracks.any { it.id == item.key.id } }
                .forEach { it.key.pinned = save }
            failedList.filter { item -> tracks.any { it.id == item.id } }
                .forEach { it.pinned = save }

            filteredTracks = filteredTracks.filter {
                !downloadQueue.any { t ->
                    t.track.id == it.id
                } && !activelyDownloading.any { t ->
                    t.key.track.id == it.id
                }
            }

            // The remainder tracks should be added to the download queue
            // By using the counter we ensure that the songs are added in the correct order
            var priority = 0
            val tracksToDownload =
                filteredTracks.map {
                    DownloadableTrack(
                        it,
                        save,
                        0,
                        if (isHighPriority) priority++ else backgroundPriorityCounter++
                    )
                }

            if (tracksToDownload.isNotEmpty()) addTracks(tracksToDownload)
        }

        fun requestStop() {
            val context = UApp.applicationContext()
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
            failedList.clear()
        }

        fun delete(track: Track) {

            downloadQueue.singleOrNull { it.id == track.id }?.let { downloadQueue.remove(it) }
            failedList.singleOrNull { it.id == track.id }?.let { downloadQueue.remove(it) }
            cancelDownload(track)

            Storage.delete(track.getPartialFile())
            Storage.delete(track.getCompleteFile())
            Storage.delete(track.getPinnedFile())
            postState(track, DownloadState.IDLE)
            Util.scanMedia(track.getPinnedFile())
        }

        fun unpin(track: Track) {
            // Update Pinned flag of items in progress
            downloadQueue.singleOrNull { it.id == track.id }?.pinned = false
            activelyDownloading.keys.singleOrNull { it.id == track.id }?.pinned = false
            failedList.singleOrNull { it.id == track.id }?.pinned = false

            val pinnedFile = track.getPinnedFile()
            if (!Storage.isPathExists(pinnedFile)) return
            val file = Storage.getFromPath(track.getPinnedFile()) ?: return
            try {
                Storage.rename(file, track.getCompleteFile())
            } catch (ignored: FileAlreadyExistsException) {
                // Play console has revealed a crash when for some reason both files exist
                Storage.delete(file.path)
            }
            postState(track, DownloadState.DONE)
        }

        @Suppress("ReturnCount")
        fun getDownloadState(track: Track): DownloadState {
            if (Storage.isPathExists(track.getCompleteFile())) return DownloadState.DONE
            if (Storage.isPathExists(track.getPinnedFile())) return DownloadState.PINNED
            if (activelyDownloading.any { it.key.id == track.id }) return DownloadState.QUEUED
            if (downloadQueue.any { it.id == track.id }) return DownloadState.QUEUED

            val key = activelyDownloading.keys.firstOrNull { it.track.id == track.id }
            if (key != null) {
                if (key.tryCount > 0) return DownloadState.RETRYING
                return DownloadState.DOWNLOADING
            }
            if (failedList.any { it.track.id == track.id }) return DownloadState.FAILED
            return DownloadState.IDLE
        }

        private fun addTracks(tracks: List<DownloadableTrack>) {
            val serviceFuture = startFuture ?: requestStart()
            serviceFuture.addListener({
                val service = serviceFuture.get()
                service.addTracks(tracks)
                Timber.i("Added tracks to DownloadService")
            }, MoreExecutors.directExecutor())
        }

        private fun cancelDownload(track: Track) {
            val key = activelyDownloading.keys.singleOrNull { it.track.id == track.id } ?: return
            activelyDownloading[key]?.cancel()
        }

        private fun postState(track: Track, state: DownloadState, progress: Int? = null) {
            RxBus.trackDownloadStatePublisher.onNext(
                RxBus.TrackDownloadState(
                    track.id,
                    state,
                    progress
                )
            )
        }

        private fun requestStart(): ListenableFuture<DownloadService> {
            val future = SettableFuture.create<DownloadService>()
            startFuture = future
            startService()
            return future
        }

        private fun startService() {
            val context = UApp.applicationContext()
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
