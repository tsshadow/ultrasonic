/*
 * MediaLibraryDataProvider.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service.androidauto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicService
import timber.log.Timber

/**
 * @class MediaLibraryDataProvider
 * @brief Provides data access and caching for the media library.
 *
 * This class is responsible for fetching media content from the backend (Subsonic API),
 * managing cached playlists and search results, and providing track data based on media IDs.
 *
 * It acts as a bridge between the media service and playback controller, ensuring efficient
 * data retrieval while reducing redundant API calls.
 *
 * @note This class caches playlists, starred songs, and search results to improve performance.
 */

class MediaLibraryDataProvider(
    private val serviceScope: CoroutineScope,
    val musicService: MusicService
):
    MediaLibraryBase() {
    lateinit var playbackController: MediaLibraryPlaybackController
    var playlistCache: List<Track>? = null
    var starredSongsCache: List<Track>? = null
    var randomSongsCache: List<Track>? = null
    var searchSongsCache: List<Track>? = null


    fun tracksFromMediaId(mediaId: String?): List<Track>? {
        Timber.d(
            "AutoMediaBrowserService onPlayFromMediaIdRequested called. mediaId: %s",
            mediaId
        )

        if (mediaId == null) return null
        val mediaIdParts = mediaId.split('|')

        // TODO Media Artist item is missing!!!
        return when (mediaIdParts.first()) {
            MEDIA_PLAYLIST_ITEM -> playbackController.playPlaylist(mediaIdParts[1], mediaIdParts[2])
            MEDIA_PLAYLIST_SONG_ITEM -> playbackController.playPlaylistSong(
                mediaIdParts[1],
                mediaIdParts[2],
                mediaIdParts[3]
            )

            MEDIA_ALBUM_ITEM -> playbackController.playAlbum(mediaIdParts[1], mediaIdParts[2])
            MEDIA_ALBUM_SONG_ITEM -> playbackController.playAlbumSong(
                mediaIdParts[1],
                mediaIdParts[2],
                mediaIdParts[3]
            )

            MEDIA_SONG_STARRED_ID -> playbackController.playStarredSongs()
            MEDIA_SONG_STARRED_ITEM -> playbackController.playStarredSong(mediaIdParts[1])
            MEDIA_SONG_RANDOM_ID -> playbackController.playRandomSongs()
            MEDIA_SONG_RANDOM_ITEM -> playbackController.playRandomSong(mediaIdParts[1])
            MEDIA_SHARE_ITEM -> playbackController.playShare(mediaIdParts[1])
            MEDIA_SHARE_SONG_ITEM -> playbackController.playShareSong(mediaIdParts[1], mediaIdParts[2])
            MEDIA_BOOKMARK_ITEM -> playbackController.playBookmark(mediaIdParts[1])
            MEDIA_PODCAST_ITEM -> playbackController.playPodcast(mediaIdParts[1])
            MEDIA_PODCAST_EPISODE_ITEM -> playbackController.playPodcastEpisode(
                mediaIdParts[1],
                mediaIdParts[2]
            )

            MEDIA_SEARCH_SONG_ITEM -> playbackController.playSearch(mediaIdParts[1])
            else -> {
                listOf()
            }
        }
    }


    fun listSongsInMusicService(id: String, name: String?): MusicDirectory? {
        return serviceScope.future {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                callWithErrorHandling { musicService.getAlbumAsDir(id, name, false) }
            } else {
                callWithErrorHandling { musicService.getMusicDirectory(id, name, false) }
            }
        }.get()
    }

    fun listStarredSongsInMusicService(): SearchResult? {
        return serviceScope.future {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                callWithErrorHandling { musicService.getStarred2() }
            } else {
                callWithErrorHandling { musicService.getStarred() }
            }
        }.get()
    }
}