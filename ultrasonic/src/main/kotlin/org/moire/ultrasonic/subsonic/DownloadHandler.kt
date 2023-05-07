/*
 * DownloadHandler.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.subsonic

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.util.Collections
import java.util.LinkedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Retrieves a list of songs and adds them to the now playing list
 */
@Suppress("LongParameterList")
class DownloadHandler(
    val mediaPlayerController: MediaPlayerController,
    val networkAndStorageChecker: NetworkAndStorageChecker
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val maxSongs = 500

    /**
     * Exception Handler for Coroutines
     */
    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Handler(Looper.getMainLooper()).post {
            Timber.w(exception)
        }
    }

    // TODO: Use coroutine here (with proper exception handler)
    fun download(
        fragment: Fragment,
        append: Boolean,
        save: Boolean,
        autoPlay: Boolean,
        playNext: Boolean,
        shuffle: Boolean,
        songs: List<Track>,
        playlistName: String?,
    ) {
        val onValid = Runnable {
            // TODO: The logic here is different than in the controller...
            val insertionMode = when {
                playNext -> MediaPlayerController.InsertionMode.AFTER_CURRENT
                append -> MediaPlayerController.InsertionMode.APPEND
                else -> MediaPlayerController.InsertionMode.CLEAR
            }

            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.addToPlaylist(
                songs,
                save,
                autoPlay,
                shuffle,
                insertionMode
            )

            if (playlistName != null) {
                mediaPlayerController.suggestedPlaylistName = playlistName
            }
            if (autoPlay) {
                if (Settings.shouldTransitionOnPlayback) {
                    fragment.findNavController().popBackStack(R.id.playerFragment, true)
                    fragment.findNavController().navigate(R.id.playerFragment)
                }
            } else if (save) {
                Util.toast(
                    fragment.context,
                    fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_pinned,
                        songs.size,
                        songs.size
                    )
                )
            } else if (playNext) {
                Util.toast(
                    fragment.context,
                    fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_play_next,
                        songs.size,
                        songs.size
                    )
                )
            } else if (append) {
                Util.toast(
                    fragment.context,
                    fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_added,
                        songs.size,
                        songs.size
                    )
                )
            }
        }
        onValid.run()
    }

    fun downloadPlaylist(
        fragment: Fragment,
        id: String,
        name: String?,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean
    ) {
        downloadRecursively(
            fragment,
            id,
            name,
            isShare = false,
            isDirectory = false,
            save = save,
            append = append,
            autoPlay = autoplay,
            shuffle = shuffle,
            background = background,
            playNext = playNext,
            unpin = unpin,
            isArtist = false
        )
    }

    fun downloadShare(
        fragment: Fragment,
        id: String,
        name: String?,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean
    ) {
        downloadRecursively(
            fragment,
            id,
            name,
            isShare = true,
            isDirectory = false,
            save = save,
            append = append,
            autoPlay = autoplay,
            shuffle = shuffle,
            background = background,
            playNext = playNext,
            unpin = unpin,
            isArtist = false
        )
    }

    fun downloadRecursively(
        fragment: Fragment,
        id: String?,
        save: Boolean,
        append: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean,
        isArtist: Boolean
    ) {
        if (id.isNullOrEmpty()) return
        downloadRecursively(
            fragment,
            id,
            "",
            isShare = false,
            isDirectory = true,
            save = save,
            append = append,
            autoPlay = autoPlay,
            shuffle = shuffle,
            background = background,
            playNext = playNext,
            unpin = unpin,
            isArtist = isArtist
        )
    }

    private fun downloadRecursively(
        fragment: Fragment,
        id: String,
        name: String?,
        isShare: Boolean,
        isDirectory: Boolean,
        save: Boolean,
        append: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean,
        isArtist: Boolean
    ) {
        // Launch the Job
        val job = launch(exceptionHandler) {
            val songs: MutableList<Track> =
                getTracksFromServer(isArtist, id, isDirectory, name, isShare)

            withContext(Dispatchers.Main) {
                addTracksToMediaController(
                    songs,
                    background,
                    unpin,
                    append,
                    playNext,
                    save,
                    autoPlay,
                    shuffle,
                    fragment
                )
            }
        }

        // Create the dialog
        val builder = InfoDialog.Builder(fragment.requireContext())
        builder.setTitle(R.string.background_task_wait)
        builder.setMessage(R.string.background_task_loading)
        builder.setOnCancelListener { job.cancel() }
        builder.setPositiveButton(R.string.common_cancel) { _, i -> job.cancel() }
        val dialog = builder.create()
        dialog.show()

        job.invokeOnCompletion {
            dialog.dismiss()
            if (it != null && it !is CancellationException) {
                Util.toast(
                    fragment.requireContext(),
                    CommunicationError.getErrorMessage(it, fragment.requireContext())
                )
            }
        }
    }

    private fun addTracksToMediaController(
        songs: MutableList<Track>,
        background: Boolean,
        unpin: Boolean,
        append: Boolean,
        playNext: Boolean,
        save: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean,
        fragment: Fragment
    ) {
        if (songs.isEmpty()) return
        if (Settings.shouldSortByDisc) {
            Collections.sort(songs, EntryByDiscAndTrackComparator())
        }
        networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
        if (!background) {
            if (unpin) {
                DownloadService.unpin(songs)
            } else {
                val insertionMode = when {
                    append -> MediaPlayerController.InsertionMode.APPEND
                    playNext -> MediaPlayerController.InsertionMode.AFTER_CURRENT
                    else -> MediaPlayerController.InsertionMode.CLEAR
                }
                mediaPlayerController.addToPlaylist(
                    songs,
                    save,
                    autoPlay,
                    shuffle,
                    insertionMode
                )
                if (
                    !append &&
                    Settings.shouldTransitionOnPlayback
                ) {
                    fragment.findNavController().popBackStack(
                        R.id.playerFragment,
                        true
                    )
                    fragment.findNavController().navigate(R.id.playerFragment)
                }
            }
        } else {
            if (unpin) {
                DownloadService.unpin(songs)
            } else {
                DownloadService.download(songs, save)
            }
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
        if (!isOffline() && isArtist && Settings.shouldUseId3Tags) {
            getSongsForArtist(id, songs)
        } else {
            if (isDirectory) {
                root = if (!isOffline() && Settings.shouldUseId3Tags)
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
            val root: MusicDirectory = if (
                !isOffline() &&
                Settings.shouldUseId3Tags
            ) musicService.getAlbumAsDir(id1, title, false)
            else musicService.getMusicDirectory(id1, title, false)
            getSongsRecursively(root, songs)
        }
    }

    @Throws(Exception::class)
    private fun getSongsForArtist(
        id: String,
        songs: MutableCollection<Track>
    ) {
        if (songs.size > maxSongs) {
            return
        }
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
    }
}
