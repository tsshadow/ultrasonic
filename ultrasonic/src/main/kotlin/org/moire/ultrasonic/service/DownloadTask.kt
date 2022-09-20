/*
 * DownloadTask.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.SystemClock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.MetaDatabase
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.CancellableTask
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber

private const val MAX_RETRIES = 5
private const val REFRESH_INTERVAL = 50

class DownloadTask(
    private val item: DownloadableTrack,
    private val stateChangedCallback: (DownloadableTrack, DownloadState, progress: Int?) -> Unit
) :
    CancellableTask(), KoinComponent {
    val musicService = MusicServiceFactory.getMusicService()

    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth", "TooGenericExceptionThrown")
    override fun execute() {

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            if (Storage.isPathExists(item.pinnedFile)) {
                Timber.i("%s already exists. Skipping.", item.pinnedFile)
                stateChangedCallback(item, DownloadState.PINNED, null)
                return
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
                return
            }

            stateChangedCallback(item, DownloadState.DOWNLOADING, null)

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
                        stateChangedCallback(
                            item,
                            DownloadState.DOWNLOADING,
                            (totalBytesCopied * 100 / (item.track.size ?: 1)).toInt()
                        )
                    }
                }

                Timber.i("Downloaded %d bytes to %s", len, item.partialFile)

                inputStream.close()
                outputStream.flush()
                outputStream.close()

                if (isCancelled) {
                    stateChangedCallback(item, DownloadState.CANCELLED, null)
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
                stateChangedCallback(item, DownloadState.PINNED, null)
                Util.scanMedia(item.pinnedFile)
            } else {
                Storage.rename(
                    item.partialFile,
                    item.completeFile
                )
                stateChangedCallback(item, DownloadState.DONE, null)
            }
        } catch (all: Exception) {
            outputStream.safeClose()
            Storage.delete(item.completeFile)
            Storage.delete(item.pinnedFile)
            if (!isCancelled) {
                if (item.tryCount < MAX_RETRIES) {
                    stateChangedCallback(item, DownloadState.RETRYING, null)
                } else {
                    stateChangedCallback(item, DownloadState.FAILED, null)
                }
                Timber.w(all, "Failed to download '%s'.", item)
            }
        } finally {
            inputStream.safeClose()
            outputStream.safeClose()
            CacheCleaner().cleanSpace()
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
