/*
 * DownloadTask.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.MetaDatabase
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.FileUtil.copyWithProgress
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber

private const val MAX_RETRIES = 5
private const val REFRESH_INTERVAL = 50

class DownloadTask(
    private val item: DownloadableTrack,
    private val scope: CoroutineScope,
    private val stateChangedCallback: (DownloadableTrack, DownloadState, progress: Int?) -> Unit
) : KoinComponent {
    private val musicService = MusicServiceFactory.getMusicService()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var job: Job? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var lastPostTime: Long = 0

    val track: DownloadableTrack
        get() = item

    private fun checkIfExists(): Boolean {
        if (Storage.isPathExists(item.pinnedFile)) {
            Timber.i("%s already exists. Skipping.", item.pinnedFile)
            stateChangedCallback(item, DownloadState.PINNED, null)
            return true
        }

        if (Storage.isPathExists(item.completeFile)) {
            var newStatus: DownloadState = DownloadState.DONE
            if (item.pinned) {
                Storage.rename(
                    item.completeFile,
                    item.pinnedFile
                )
                newStatus = DownloadState.PINNED
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
            stateChangedCallback(item, newStatus, null)
            return true
        }

        return false
    }

    fun download() {
        stateChangedCallback(item, DownloadState.DOWNLOADING, null)

        val fileLength = Storage.getFromPath(item.partialFile)?.length ?: 0

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

        val len = inputStream!!.copyWithProgress(outputStream!!) { totalBytesCopied ->
            // Add previous existing file length for correct display when resuming
            publishProgressUpdate(fileLength + totalBytesCopied)
        }

        Timber.i("Downloaded %d bytes to %s", len, item.partialFile)

        inputStream?.close()
        outputStream?.flush()
        outputStream?.close()
    }

    private fun publishProgressUpdate(totalBytesCopied: Long) {
        // Check if we are cancelled...
        if (job?.isCancelled == true) {
            throw CancellationException()
        }

        // Manual throttling to avoid overloading Rx
        if (SystemClock.elapsedRealtime() - lastPostTime > REFRESH_INTERVAL) {
            lastPostTime = SystemClock.elapsedRealtime()

            // If the file size is unknown we can only provide null as the progress
            val size = item.track.size ?: 0
            val progress = if (size <= 0) {
                null
            } else {
                (totalBytesCopied * 100 / (size)).toInt()
            }

            stateChangedCallback(
                item,
                DownloadState.DOWNLOADING,
                progress
            )
        }
    }

    private fun afterDownload() {
        try {
            item.track.cacheMetadataAndArtwork()
        } catch (ignore: Exception) {
            Timber.w(ignore)
        }

        if (item.pinned) {
            Storage.rename(
                item.partialFile,
                item.pinnedFile
            )
            Timber.i("Renamed file to ${item.pinnedFile}")
            stateChangedCallback(item, DownloadState.PINNED, null)
            Util.scanMedia(item.pinnedFile)
        } else {
            Storage.rename(
                item.partialFile,
                item.completeFile
            )
            Timber.i("Renamed file to ${item.completeFile}")
            stateChangedCallback(item, DownloadState.DONE, null)
        }
    }

    private fun onCompletion(e: Throwable?) {
        if (e is CancellationException) {
            Timber.w(e, "CompletionHandler ${item.pinnedFile}")
            stateChangedCallback(item, DownloadState.CANCELLED, null)
        } else if (e != null) {
            Timber.w(e, "CompletionHandler ${item.pinnedFile}")
            if (item.tryCount < MAX_RETRIES) {
                stateChangedCallback(item, DownloadState.RETRYING, null)
            } else {
                stateChangedCallback(item, DownloadState.FAILED, null)
            }
        }
        inputStream.safeClose()
        outputStream.safeClose()
    }

    private fun exceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            Timber.w(exception, "Exception in DownloadTask ${item.pinnedFile}")
            Storage.delete(item.completeFile)
            Storage.delete(item.pinnedFile)
        }
    }

    fun start() {
        Timber.i("Launching new Job ${item.pinnedFile}")
        job = scope.launch(exceptionHandler()) {
            if (!checkIfExists() && isActive) {
                download()
                afterDownload()
            }
        }

        job!!.invokeOnCompletion(::onCompletion)
    }

    fun cancel() {
        job?.cancel()
    }

    private fun Track.cacheMetadataAndArtwork() {
        val onlineDB = activeServerProvider.getActiveMetaDatabase()
        val offlineDB = activeServerProvider.offlineMetaDatabase

        var artistId: String? = if (artistId.isNullOrEmpty()) null else artistId
        val albumId: String? = if (albumId.isNullOrEmpty()) null else albumId

        var album: Album? = null
        var directArtist: Artist? = null
        var compilationArtist: Artist? = null

        // Sometime in compilation albums, the individual tracks won't have an Artist id
        // In this case, try to get the ArtistId of the album...
        if (artistId == null && albumId != null) {
            album = musicService.getAlbum(albumId, null, false)
            artistId = album?.artistId
        }

        // Cache the artist
        if (artistId != null)
            directArtist = cacheArtist(onlineDB, offlineDB, artistId)

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
                    compilationArtist = cacheArtist(onlineDB, offlineDB, album.artistId!!)
            }
        }

        // Now cache the track data
        offlineDB.trackDao().insert(this)

        // Download the largest size that we can display in the UI
        imageLoaderProvider.executeOn { imageLoader ->
            imageLoader.cacheCoverArt(this)
            // Cache small copies of the Artist picture
            directArtist?.let { imageLoader.cacheArtistPicture(it) }
            compilationArtist?.let { imageLoader.cacheArtistPicture(it) }
        }
    }

    private fun cacheArtist(
        onlineDB: MetaDatabase,
        offlineDB: MetaDatabase,
        artistId: String
    ): Artist? {
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

        return artist
    }
}
