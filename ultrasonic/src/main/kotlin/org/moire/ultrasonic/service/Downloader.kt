/*
 * Downloader.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock as SystemClock
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.PriorityQueue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.MetaDatabase
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.CancellableTask
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.FileUtil.getCompleteFile
import org.moire.ultrasonic.util.FileUtil.getPartialFile
import org.moire.ultrasonic.util.FileUtil.getPinnedFile
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.safeClose
import org.moire.ultrasonic.util.shouldBePinned
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

/**
 * This class is responsible for maintaining the playlist and downloading
 * its items from the network to the filesystem.
 *
 * TODO: Move entirely to subclass the Media3.DownloadService
 */
class Downloader(
    private val storageMonitor: ExternalStorageMonitor,
) : KoinComponent {

    // Dependencies
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val mediaController: MediaPlayerController by inject()

    var started: Boolean = false
    var shouldStop: Boolean = false
    var isPolling: Boolean = false

    private val downloadQueue = PriorityQueue<DownloadableTrack>()
    private val activelyDownloading = mutableMapOf<DownloadableTrack, DownloadTask>()
    private val failedList = mutableListOf<DownloadableTrack>()

    // The generic list models expect a LiveData, so even though we are using Rx for many events
    // surrounding playback the list of Downloads is published as LiveData.
    val observableDownloads = MutableLiveData<List<Track>>()

    private var handler: Handler = Handler(Looper.getMainLooper())
    private var wifiLock: WifiManager.WifiLock? = null

    private var backgroundPriorityCounter = 100

    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

    init {
        Timber.i("Init called")
        // Check downloads if the playlist changed
        rxBusSubscription += RxBus.playlistObservable.subscribe {
            Timber.v("Playlist has changed, checking Downloads...")
            checkDownloads()
        }
    }

    private var downloadChecker = object : Runnable {
        override fun run() {
            try {
                Timber.w("Checking Downloads")
                checkDownloadsInternal()
            } catch (all: Exception) {
                Timber.e(all, "checkDownloads() failed.")
            } finally {
                if (!isPolling) {
                    isPolling = true
                    if (!shouldStop) {
                        Handler(Looper.getMainLooper()).postDelayed(this, CHECK_INTERVAL)
                    } else {
                        shouldStop = false
                        isPolling = false
                    }
                }
            }
        }
    }

    fun onDestroy() {
        stop()
        rxBusSubscription.dispose()
        clearBackground()
        observableDownloads.value = listOf()
        Timber.i("Downloader destroyed")
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true

        // Start our loop
        handler.postDelayed(downloadChecker, 100)

        if (wifiLock == null) {
            wifiLock = Util.createWifiLock(toString())
            wifiLock?.acquire()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        shouldStop = true
        wifiLock?.release()
        wifiLock = null
        handler.postDelayed(
            Runnable { DownloadService.runningInstance?.notifyDownloaderStopped() },
            100
        )
        Timber.i("Downloader stopped")
    }

    fun checkDownloads() {
        if (!started) {
            start()
        } else {
            try {
                handler.postDelayed(downloadChecker, 100)
            } catch (all: Exception) {
                Timber.w(
                    all,
                    "checkDownloads() can't run, maybe the Downloader is shutting down..."
                )
            }
        }
    }

    @Suppress("ComplexMethod", "ComplexCondition")
    @Synchronized
    private fun checkDownloadsInternal() {
        if (!Util.isExternalStoragePresent() || !storageMonitor.isExternalStorageAvailable) {
            return
        }

        if (JukeboxMediaPlayer.running.get() || !Util.isNetworkConnected()) {
            return
        }

        Timber.v("Downloader checkDownloadsInternal checking downloads")

        var listChanged = false
        val playlist = mediaController.getNextPlaylistItemsInPlayOrder(Settings.preloadCount)
        var priority = 0

        for (item in playlist) {
            val track = item.toTrack()

            // Add file to queue if not in one of the queues already.
            if (getDownloadState(track) == DownloadStatus.IDLE) {
                listChanged = true

                // If a track is already in the manual download queue,
                // and is now due to be played soon we add it to the queue with high priority instead.
                val existingItem = downloadQueue.firstOrNull { it.track.id == track.id }
                if (existingItem != null) {
                    existingItem.priority = priority + 1
                    continue
                }

                // Set correct priority (the lower the number, the higher the priority)
                downloadQueue.add(DownloadableTrack(track, item.shouldBePinned(), 0, priority++))
                postState(track, DownloadStatus.QUEUED)
            }
        }

        // Fill up active List with waiting tasks
        while (activelyDownloading.size < Settings.parallelDownloads && downloadQueue.size > 0) {
            val task = downloadQueue.remove()
            val downloadTask = DownloadTask(task)
            activelyDownloading[task] = downloadTask
            startDownloadOnService(task)

            listChanged = true
        }

        // Stop Executor service when done downloading
        if (activelyDownloading.isEmpty()) {
            stop()
        }

        if (listChanged) {
            updateLiveData()
        }
    }

    private fun updateLiveData() {
        observableDownloads.postValue(downloads)
    }

    private fun startDownloadOnService(track: DownloadableTrack) {
        DownloadService.executeOnStartedDownloadService {
            FileUtil.createDirectoryForParent(track.pinnedFile)
            activelyDownloading[track]?.start()
            Timber.v("startDownloadOnService started downloading file ${track.completeFile}")
        }
    }

    /*
    * Returns a list of all DownloadFiles that are currently downloading or waiting for download,
    */
    @get:Synchronized
    val downloads: List<Track>
        get() {
            val temp: MutableList<Track> = ArrayList()
            temp.addAll(activelyDownloading.keys.map { x -> x.track })
            temp.addAll(downloadQueue.map { x -> x.track })
            return temp.distinct().sorted()
        }

    @Synchronized
    fun clearBackground() {
        // Clear the pending queue
        while (!downloadQueue.isEmpty()) {
            postState(downloadQueue.remove().track, DownloadStatus.IDLE)
        }

        // Cancel all active downloads with a low priority
        for (key in activelyDownloading.keys) {
            if (key.priority >= 100) {
                activelyDownloading[key]?.cancel()
                activelyDownloading.remove(key)
            }
        }

        backgroundPriorityCounter = 100
    }

    @Synchronized
    fun clearActiveDownloads() {
        // Cancel all active downloads
        for (download in activelyDownloading) {
            download.value.cancel()
        }
        activelyDownloading.clear()
        updateLiveData()
    }

    @Synchronized
    fun downloadBackground(tracks: List<Track>, save: Boolean) {
        // By using the counter we ensure that the songs are added in the correct order
        for (track in tracks) {
            if (downloadQueue.any { t -> t.track.id == track.id } ||
                activelyDownloading.any { t -> t.key.track.id == track.id }
            ) continue
            val file = DownloadableTrack(track, save, 0, backgroundPriorityCounter++)
            downloadQueue.add(file)
            postState(track, DownloadStatus.QUEUED)
        }

        Timber.v("downloadBackground Checking Downloads")
        checkDownloads()
    }

    fun delete(track: Track) {
        cancelDownload(track)
        Storage.delete(track.getPartialFile())
        Storage.delete(track.getCompleteFile())
        Storage.delete(track.getPinnedFile())
        postState(track, DownloadStatus.IDLE)
        Util.scanMedia(track.getPinnedFile())
    }

    private fun cancelDownload(track: Track) {
        val key = activelyDownloading.keys.singleOrNull { it.track.id == track.id } ?: return
        activelyDownloading[key]?.cancel()
    }

    fun unpin(track: Track) {
        val pinnedFile = track.getPinnedFile()
        if (!Storage.isPathExists(pinnedFile)) return
        val file = Storage.getFromPath(track.getPinnedFile()) ?: return
        Storage.rename(file, track.getCompleteFile())
        postState(track, DownloadStatus.DONE)
    }

    @Suppress("ReturnCount")
    fun getDownloadState(track: Track): DownloadStatus {
        if (Storage.isPathExists(track.getCompleteFile())) return DownloadStatus.DONE
        if (Storage.isPathExists(track.getPinnedFile())) return DownloadStatus.PINNED
        if (downloads.any { it.id == track.id }) return DownloadStatus.QUEUED

        val key = activelyDownloading.keys.firstOrNull { it.track.id == track.id }
        if (key != null) {
            if (key.tryCount > 0) return DownloadStatus.RETRYING
            return DownloadStatus.DOWNLOADING
        }
        if (failedList.any { it.track.id == track.id }) return DownloadStatus.FAILED
        return DownloadStatus.IDLE
    }

    companion object {
        const val CHECK_INTERVAL = 5000L
        const val MAX_RETRIES = 5
        const val REFRESH_INTERVAL = 50
    }

    private fun postState(track: Track, state: DownloadStatus, progress: Int? = null) {
        RxBus.trackDownloadStatePublisher.onNext(
            RxBus.TrackDownloadState(
                track.id,
                state,
                progress
            )
        )
    }

    private inner class DownloadTask(private val item: DownloadableTrack) :
        CancellableTask() {
        val musicService = MusicServiceFactory.getMusicService()

        @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth", "TooGenericExceptionThrown")
        override fun execute() {

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                if (Storage.isPathExists(item.pinnedFile)) {
                    Timber.i("%s already exists. Skipping.", item.pinnedFile)
                    postState(item.track, DownloadStatus.PINNED)
                    return
                }

                if (Storage.isPathExists(item.completeFile)) {
                    var newStatus: DownloadStatus = DownloadStatus.DONE
                    if (item.pinned) {
                        Storage.rename(
                            item.completeFile,
                            item.pinnedFile
                        )
                        newStatus = DownloadStatus.PINNED
                    } else {
                        Timber.i(
                            "%s already exists. Skipping.",
                            item.completeFile
                        )
                    }

                    // Hidden feature: If track is toggled between pinned/saved, refresh the metadata..
                    try {
                        item.track.cacheMetadataAndArtwork()
                    } catch (ignore: Exception) {
                        Timber.w(ignore)
                    }
                    postState(item.track, newStatus)
                    return
                }

                postState(item.track, DownloadStatus.DOWNLOADING)

                // Some devices seem to throw error on partial file which doesn't exist
                val needsDownloading: Boolean
                val duration = item.track.duration
                val fileLength = Storage.getFromPath(item.partialFile)?.length ?: 0

                needsDownloading = (duration == null || duration == 0 || fileLength == 0L)

                if (needsDownloading) {
                    // Attempt partial HTTP GET, appending to the file if it exists.
                    val (inStream, isPartial) = musicService.getDownloadInputStream(
                        item.track, fileLength,
                        Settings.maxBitRate,
                        item.pinned
                    )

                    inputStream = inStream

                    if (isPartial) {
                        Timber.i("Executed partial HTTP GET, skipping %d bytes", fileLength)
                    }

                    outputStream = Storage.getOrCreateFileFromPath(item.partialFile)
                        .getFileOutputStream(isPartial)

                    var lastPostTime: Long = 0
                    val len = inputStream.copyTo(outputStream) { totalBytesCopied ->
                        // Manual throttling to avoid overloading Rx
                        if (SystemClock.elapsedRealtime() - lastPostTime > REFRESH_INTERVAL) {
                            lastPostTime = SystemClock.elapsedRealtime()
                            postState(
                                item.track,
                                DownloadStatus.DOWNLOADING,
                                (totalBytesCopied * 100 / (item.track.size ?: 1)).toInt()
                            )
                        }
                    }

                    Timber.i("Downloaded %d bytes to %s", len, item.partialFile)

                    inputStream.close()
                    outputStream.flush()
                    outputStream.close()

                    if (isCancelled) {
                        postState(item.track, DownloadStatus.CANCELLED)
                        throw RuntimeException(
                            String.format(
                                Locale.ROOT, "Download of '%s' was cancelled",
                                item
                            )
                        )
                    }

                    try {
                        item.track.cacheMetadataAndArtwork()
                    } catch (ignore: Exception) {
                        Timber.w(ignore)
                    }
                }

                if (item.pinned) {
                    Storage.rename(
                        item.partialFile,
                        item.pinnedFile
                    )
                    postState(item.track, DownloadStatus.PINNED)
                    Util.scanMedia(item.pinnedFile)
                } else {
                    Storage.rename(
                        item.partialFile,
                        item.completeFile
                    )
                    postState(item.track, DownloadStatus.DONE)
                }
            } catch (all: Exception) {
                outputStream.safeClose()
                Storage.delete(item.completeFile)
                Storage.delete(item.pinnedFile)
                if (!isCancelled) {
                    if (item.tryCount < MAX_RETRIES) {
                        postState(item.track, DownloadStatus.RETRYING)
                        item.tryCount++
                        activelyDownloading.remove(item)
                        downloadQueue.add(item)
                    } else {
                        postState(item.track, DownloadStatus.FAILED)
                        activelyDownloading.remove(item)
                        downloadQueue.remove(item)
                        failedList.add(item)
                    }
                    Timber.w(all, "Failed to download '%s'.", item)
                }
            } finally {
                activelyDownloading.remove(item)
                inputStream.safeClose()
                outputStream.safeClose()
                CacheCleaner().cleanSpace()
                Timber.v("DownloadTask checking downloads")
                checkDownloads()
            }
        }

        override fun toString(): String {
            return String.format(Locale.ROOT, "DownloadTask (%s)", item)
        }

        private fun Track.cacheMetadataAndArtwork() {
            val onlineDB = activeServerProvider.getActiveMetaDatabase()
            val offlineDB = activeServerProvider.offlineMetaDatabase

            var artistId: String? = if (artistId.isNullOrEmpty()) null else artistId
            val albumId: String? = if (albumId.isNullOrEmpty()) null else albumId

            var album: Album? = null

            // Sometime in compilation albums, the individual tracks won't have an Artist id
            // In this case, try to get the ArtistId of the album...
            if (artistId == null && albumId != null) {
                album = musicService.getAlbum(albumId, null, false)
                artistId = album?.artistId
            }

            // Cache the artist
            if (artistId != null)
                cacheArtist(onlineDB, offlineDB, artistId)

            // Now cache the album
            if (albumId != null) {
                if (album == null) {
                    // This is a cached call
                    val albums = musicService.getAlbumsOfArtist(artistId!!, null, false)
                    album = albums.find { it.id == albumId }
                }

                if (album != null) {
                    // Often the album entity returned from the server won't have the path set.
                    if (album.path.isNullOrEmpty()) album.path = FileUtil.getParentPath(path)

                    offlineDB.albumDao().insert(album)

                    // If the album is a Compilation, also cache the Album artist
                    if (album.artistId != null && album.artistId != artistId)
                        cacheArtist(onlineDB, offlineDB, album.artistId!!)
                }
            }

            // Now cache the track data
            offlineDB.trackDao().insert(this)

            // Download the largest size that we can display in the UI
            imageLoaderProvider.getImageLoader().cacheCoverArt(this)
        }

        private fun cacheArtist(onlineDB: MetaDatabase, offlineDB: MetaDatabase, artistId: String) {
            var artist: Artist? = onlineDB.artistDao().get(artistId)

            // If we are downloading a new album, and the user has not visited the Artists list
            // recently, then the artist won't be in the database.
            if (artist == null) {
                val artists: List<Artist> = musicService.getArtists(true)
                artist = artists.find {
                    it.id == artistId
                }
            }

            // If we have found an artist, cache it.
            if (artist != null) {
                offlineDB.artistDao().insert(artist)
            }
        }

        @Throws(IOException::class)
        fun InputStream.copyTo(out: OutputStream, onCopy: (totalBytesCopied: Long) -> Any): Long {
            var bytesCopied: Long = 0
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytes = read(buffer)
            while (!isCancelled && bytes >= 0) {
                out.write(buffer, 0, bytes)
                bytesCopied += bytes
                onCopy(bytesCopied)
                bytes = read(buffer)
            }
            return bytesCopied
        }
    }

    private class DownloadableTrack(
        val track: Track,
        val pinned: Boolean,
        var tryCount: Int,
        var priority: Int
    ) : Identifiable {
        val pinnedFile = track.getPinnedFile()
        val partialFile = track.getPartialFile()
        val completeFile = track.getCompleteFile()
        override val id: String
            get() = track.id

        override fun compareTo(other: Identifiable) = compareTo(other as DownloadableTrack)
        fun compareTo(other: DownloadableTrack): Int {
            return priority.compareTo(other.priority)
        }
    }
}

enum class DownloadStatus {
    IDLE, QUEUED, DOWNLOADING, RETRYING, FAILED, CANCELLED, DONE, PINNED, UNKNOWN
}
