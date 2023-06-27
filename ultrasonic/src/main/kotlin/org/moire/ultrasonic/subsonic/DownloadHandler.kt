/*
 * DownloadHandler.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.subsonic

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.util.LinkedList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.shouldUseId3Tags
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.executeTaskWithToast

/**
 * Retrieves a list of songs and adds them to the now playing list
 */
@Suppress("LongParameterList")
class DownloadHandler(
    val mediaPlayerManager: MediaPlayerManager,
    private val networkAndStorageChecker: NetworkAndStorageChecker
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val maxSongs = 500

    fun justDownload(
        action: DownloadAction,
        fragment: Fragment,
        id: String? = null,
        name: String? = "",
        isShare: Boolean = false,
        isDirectory: Boolean = true,
        isArtist: Boolean = false,
        tracks: List<Track>? = null
    ) {
        var successString: String? = null

        // Launch the Job
        executeTaskWithToast({
            val tracksToDownload: List<Track> = tracks
                ?: getTracksFromServer(isArtist, id!!, isDirectory, name, isShare)

            withContext(Dispatchers.Main) {
                // If we are just downloading tracks we don't need to add them to the controller
                when (action) {
                    DownloadAction.DOWNLOAD -> DownloadService.download(tracksToDownload, false)
                    DownloadAction.PIN -> DownloadService.download(tracksToDownload, true)
                    DownloadAction.UNPIN -> DownloadService.unpin(tracksToDownload)
                    DownloadAction.DELETE -> DownloadService.delete(tracksToDownload)
                }
                successString = when (action) {
                    DownloadAction.DOWNLOAD -> fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_downloaded,
                        tracksToDownload.size,
                        tracksToDownload.size
                    )
                    DownloadAction.UNPIN -> {
                        fragment.resources.getQuantityString(
                            R.plurals.select_album_n_songs_unpinned,
                            tracksToDownload.size,
                            tracksToDownload.size
                        )
                    }
                    DownloadAction.PIN -> {
                        fragment.resources.getQuantityString(
                            R.plurals.select_album_n_songs_pinned,
                            tracksToDownload.size,
                            tracksToDownload.size
                        )
                    }
                    DownloadAction.DELETE -> {
                        fragment.resources.getQuantityString(
                            R.plurals.select_album_n_songs_deleted,
                            tracksToDownload.size,
                            tracksToDownload.size
                        )
                    }
                }
            }
        }) { successString }
    }

    fun fetchTracksAndAddToController(
        fragment: Fragment,
        id: String,
        name: String? = "",
        isShare: Boolean = false,
        isDirectory: Boolean = true,
        append: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean = false,
        playNext: Boolean,
        isArtist: Boolean = false
    ) {
        var successString: String? = null
        // Launch the Job
        executeTaskWithToast({
            val songs: MutableList<Track> =
                getTracksFromServer(isArtist, id, isDirectory, name, isShare)

            withContext(Dispatchers.Main) {
                addTracksToMediaController(
                    songs = songs,
                    append = append,
                    playNext = playNext,
                    autoPlay = autoPlay,
                    shuffle = shuffle,
                    playlistName = null,
                    fragment = fragment
                )
                // Play Now doesn't get a Toast :)
                if (playNext) {
                    successString = fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_play_next,
                        songs.size,
                        songs.size
                    )
                } else if (append) {
                    successString = fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_added,
                        songs.size,
                        songs.size
                    )
                }
            }
        }) { successString }
    }

    fun addTracksToMediaController(
        songs: List<Track>,
        append: Boolean,
        playNext: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean = false,
        playlistName: String? = null,
        fragment: Fragment
    ) {
        if (songs.isEmpty()) return

        networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()

        val insertionMode = when {
            append -> MediaPlayerManager.InsertionMode.APPEND
            playNext -> MediaPlayerManager.InsertionMode.AFTER_CURRENT
            else -> MediaPlayerManager.InsertionMode.CLEAR
        }

        if (playlistName != null) {
            mediaPlayerManager.suggestedPlaylistName = playlistName
        }

        mediaPlayerManager.addToPlaylist(
            songs,
            autoPlay,
            shuffle,
            insertionMode
        )
        if (Settings.shouldTransitionOnPlayback && (!append || autoPlay)) {
            fragment.findNavController().popBackStack(R.id.playerFragment, true)
            fragment.findNavController().navigate(R.id.playerFragment)
        }
    }

    private fun getTracksFromServer(
        isArtist: Boolean,
        id: String,
        isDirectory: Boolean,
        name: String?,
        isShare: Boolean
    ): MutableList<Track> {
        val musicService = getMusicService()
        val songs: MutableList<Track> = LinkedList()
        val root: MusicDirectory
        if (shouldUseId3Tags() && isArtist) {
            return getSongsForArtist(id)
        } else {
            if (isDirectory) {
                root = if (shouldUseId3Tags())
                    musicService.getAlbumAsDir(id, name, false)
                else
                    musicService.getMusicDirectory(id, name, false)
            } else if (isShare) {
                root = MusicDirectory()
                val shares = musicService.getShares(true)
                // Filter the received shares by the given id, and get their entries
                val entries = shares.filter { it.id == id }.flatMap { it.getEntries() }
                root.addAll(entries)
            } else {
                root = musicService.getPlaylist(id, name!!)
            }
            getSongsRecursively(root, songs)
        }
        return songs
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    @Throws(Exception::class)
    private fun getSongsRecursively(
        parent: MusicDirectory,
        songs: MutableList<Track>
    ) {
        if (songs.size > maxSongs) {
            return
        }
        for (song in parent.getTracks()) {
            if (!song.isVideo) {
                songs.add(song)
            }
        }
        val musicService = getMusicService()
        for ((id1, _, _, title) in parent.getAlbums()) {
            val root: MusicDirectory = if (shouldUseId3Tags())
                musicService.getAlbumAsDir(id1, title, false)
            else
                musicService.getMusicDirectory(id1, title, false)
            getSongsRecursively(root, songs)
        }
    }

    @Throws(Exception::class)
    private fun getSongsForArtist(
        id: String
    ): MutableList<Track> {
        val songs: MutableList<Track> = LinkedList()
        val musicService = getMusicService()
        val artist = musicService.getAlbumsOfArtist(id, "", false)
        for ((id1) in artist) {
            val albumDirectory = musicService.getAlbumAsDir(
                id1,
                "",
                false
            )
            for (song in albumDirectory.getTracks()) {
                if (!song.isVideo) {
                    songs.add(song)
                }
            }
        }
        return songs
    }
}

enum class DownloadAction {
    DOWNLOAD, PIN, UNPIN, DELETE
}
