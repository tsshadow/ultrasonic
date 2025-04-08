/*
 * MediaLibraryPlaybackController.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service.androidauto

import androidx.media3.common.MediaItem
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.toMediaItem
import timber.log.Timber
/**
 * @class MediaLibraryPlaybackController
 * @brief Handles media playback functionality in Android Auto.
 *
 * This class is responsible for controlling media playback, including playing songs from playlists,
 * albums, starred lists, and search results. It interacts with `MusicService` to fetch and play media
 * items and caches previously retrieved songs to improve performance.
 *
 * @details
 * - Supports playback of random songs, starred songs, albums, and playlists.
 * - Implements caching mechanisms to avoid redundant API calls.
 * - Provides helper methods for retrieving and playing songs by media ID.
 * - Uses coroutines for asynchronous data fetching and execution.
 *
 * @note This class interacts with `MediaLibraryDataProvider` for data retrieval and `MusicService`
 * for accessing media content.
 */
class MediaLibraryPlaybackController(
    val serviceScope: CoroutineScope,
    val musicService: MusicService
) : MediaLibraryBase() {
    lateinit var dataProvider: MediaLibraryDataProvider


    fun playRandomSongs(): List<Track>? {
        if (dataProvider.randomSongsCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            // In this case we request a new set of random songs
            val content = serviceScope.future {
                callWithErrorHandling { musicService.getRandomSongs(maxSongs) }
            }.get()
            dataProvider.randomSongsCache = content?.getTracks()
        }
        if (dataProvider.randomSongsCache != null) return dataProvider.randomSongsCache!!
        return null
    }

    fun playRandomSong(songId: String): List<Track>? {
        // If there is no cache, we can't play the selected song.
        if (dataProvider.randomSongsCache != null) {
            val song = dataProvider.randomSongsCache!!.firstOrNull { x -> x.id == songId }
            if (song != null) return listOf(song)
        }
        return null
    }

    fun playStarredSongs(): List<Track>? {
        if (dataProvider.starredSongsCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content = dataProvider.listStarredSongsInMusicService()
            dataProvider.starredSongsCache = content?.songs
        }
        if (dataProvider.starredSongsCache != null) return dataProvider.starredSongsCache!!
        return null
    }

    fun playStarredSong(songId: String): List<Track>? {
        if (dataProvider.starredSongsCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content = dataProvider.listStarredSongsInMusicService()
            dataProvider.starredSongsCache = content?.songs
        }
        val song = dataProvider.starredSongsCache?.firstOrNull { x -> x.id == songId }
        if (song != null) return listOf(song)
        return null
    }


    fun playSearch(id: String): List<Track>? {
        // If there is no cache, we can't play the selected song.
        if (dataProvider.searchSongsCache != null) {
            val song = dataProvider.searchSongsCache!!.firstOrNull { x -> x.id == id }
            if (song != null) return listOf(song)
        }
        return null
    }


    fun playPlaylist(id: String, name: String): List<Track>? {
        Timber.d("playPlaylist")
        if (dataProvider.playlistCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content =
                serviceScope.future {
                    callWithErrorHandling { musicService.getPlaylist(id, name) }
                }.get()
            dataProvider.playlistCache = content?.getTracks()
        }
        if (dataProvider.playlistCache != null) return dataProvider.playlistCache!!
        return null
    }

    fun playPlaylistSong(id: String, name: String, songId: String): List<Track>? {
        Timber.d("playPlaylistSong")
        if (dataProvider.playlistCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content = serviceScope.future {
                callWithErrorHandling { musicService.getPlaylist(id, name) }
            }.get()
            dataProvider.playlistCache = content?.getTracks()
        }
        val song = dataProvider.playlistCache?.firstOrNull { x -> x.id == songId }
        if (song != null) return listOf(song)
        return null
    }

    fun playAlbum(id: String, name: String?): List<Track>? {
        Timber.d("playAlbum")
        val songs = dataProvider.listSongsInMusicService(id, name)
        if (songs != null) return songs.getTracks()
        return null
    }

    fun playAlbumSong(id: String, name: String?, songId: String): List<Track>? {
        Timber.d("playAlbumSong")
        val songs = dataProvider.listSongsInMusicService(id, name)
        val song = songs?.getTracks()?.firstOrNull { x -> x.id == songId }
        if (song != null) return listOf(song)
        return null
    }


    fun onAddLegacyAutoItems(
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        Timber.i("onAddLegacyAutoItems %s", mediaItems.first().mediaId)

        val mediaIdParts = mediaItems.first().mediaId.split('|')

        val tracks = when (mediaIdParts.first()) {
            MEDIA_PLAYLIST_ITEM -> playPlaylist(mediaIdParts[1], mediaIdParts[2])
            MEDIA_PLAYLIST_SONG_ITEM -> playPlaylistSong(
                mediaIdParts[1],
                mediaIdParts[2],
                mediaIdParts[3]
            )

            MEDIA_ALBUM_ITEM -> playAlbum(mediaIdParts[1], mediaIdParts[2])
            MEDIA_ALBUM_SONG_ITEM -> playAlbumSong(
                mediaIdParts[1],
                mediaIdParts[2],
                mediaIdParts[3]
            )

            MEDIA_SONG_STARRED_ID -> playStarredSongs()
            MEDIA_SONG_STARRED_ITEM -> playStarredSong(mediaIdParts[1])
            MEDIA_SONG_RANDOM_ID -> playRandomSongs()
            MEDIA_SONG_RANDOM_ITEM -> playRandomSong(mediaIdParts[1])
            MEDIA_SHARE_ITEM -> playShare(mediaIdParts[1])
            MEDIA_SHARE_SONG_ITEM -> playShareSong(mediaIdParts[1], mediaIdParts[2])
            MEDIA_BOOKMARK_ITEM -> playBookmark(mediaIdParts[1])
            MEDIA_PODCAST_ITEM -> playPodcast(mediaIdParts[1])
            MEDIA_PODCAST_EPISODE_ITEM -> playPodcastEpisode(
                mediaIdParts[1],
                mediaIdParts[2]
            )

            MEDIA_SEARCH_SONG_ITEM -> playSearch(mediaIdParts[1])
            else -> null
        }

        return tracks
            ?.let {
                Futures.immediateFuture(
                    it.map { track -> track.toMediaItem() }
                        .toMutableList()
                )
            }
            ?: Futures.immediateFuture(mediaItems)
    }


    fun playFromSearch(query: String): ListenableFuture<List<MediaItem>> {
        Timber.w("App state: %s", UApp.instance != null)

        Timber.i("AutoMediaBrowserService onSearch query: %s", query)
        val mediaItems: MutableList<MediaItem> = ArrayList()

        // Only accept query with pattern "play [Title]" or "[Title]"
        // Where [Title]: must be exactly matched
        // If no media with exact name found, play a random media instead
        val mediaTitle = if (query.startsWith(PLAY_COMMAND, ignoreCase = true)) {
            query.drop(PLAY_COMMAND.length)
        } else {
            query
        }

        return serviceScope.future {
            val criteria = SearchCriteria(mediaTitle, SEARCH_LIMIT, SEARCH_LIMIT, SEARCH_LIMIT)
            val searchResult = callWithErrorHandling { musicService.search(criteria) }

            // TODO Add More... button to categories
            if (searchResult != null) {
                searchResult.albums.map { album ->
                    mediaItems.add(
                        album.title ?: "",
                        listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                            .joinToString("|")
                    )
                }

                // TODO Commented out, as there is no playFromArtist function implemented yet.
//                searchResult.artists.map { artist ->
//                    mediaItems.add(
//                        artist.name ?: "",
//                        listOf(MEDIA_ARTIST_ITEM, artist.id, artist.name).joinToString("|"),
//                        FOLDER_TYPE_ARTISTS
//                    )
//                }

                dataProvider.searchSongsCache = searchResult.songs
                searchResult.songs.map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_SEARCH_SONG_ITEM, song.id).joinToString("|")
                        )
                    )
                }
            }

            // TODO This just picks the first result and plays it.
            // We could make this more advanced.
            val firstItem = mediaItems.first()
            val tracks = dataProvider.tracksFromMediaId(firstItem.mediaId)
            Timber.i("Found media id: %s", firstItem.mediaId)
            val result = tracks?.map { it.toMediaItem() }
            Timber.i("Result size: %d", result?.size ?: 0)
            return@future result ?: listOf()
        }
    }

    fun playPodcast(id: String): List<Track>? {
        Timber.d("playPodcast")
        val episodes = serviceScope.future {
            callWithErrorHandling { musicService.getPodcastEpisodes(id) }
        }.get()
        if (episodes != null) {
            return episodes.getTracks()
        }
        return null
    }

    fun playPodcastEpisode(id: String, episodeId: String): List<Track>? {
        Timber.d("playPodcastEpisode")
        val episodes = serviceScope.future {
            callWithErrorHandling { musicService.getPodcastEpisodes(id) }
        }.get()
        if (episodes != null) {
            val selectedEpisode = episodes
                .getTracks()
                .firstOrNull { episode -> episode.id == episodeId }
            if (selectedEpisode != null) return listOf(selectedEpisode)
        }
        return null
    }


    fun playBookmark(id: String): List<Track>? {
        Timber.d("playBookmark")
        val bookmarks = serviceScope.future {
            callWithErrorHandling { musicService.getBookmarks() }
        }.get()
        if (bookmarks != null) {
            val songs = Util.getSongsFromBookmarks(bookmarks)
            val song = songs.getTracks().firstOrNull { song -> song.id == id }
            if (song != null) return listOf(song)
        }
        return null
    }


    fun playShare(id: String): List<Track>? {
        Timber.d("playShare")
        val shares = serviceScope.future {
            callWithErrorHandling { musicService.getShares(false) }
        }.get()
        val selectedShare = shares?.firstOrNull { share -> share.id == id }
        if (selectedShare != null) {
            return selectedShare.getEntries()
        }
        return null
    }

    fun playShareSong(id: String, songId: String): List<Track>? {
        Timber.d("playShareSong")
        val shares = serviceScope.future {
            callWithErrorHandling { musicService.getShares(false) }
        }.get()
        val selectedShare = shares?.firstOrNull { share -> share.id == id }
        if (selectedShare != null) {
            val song = selectedShare.getEntries().firstOrNull { x -> x.id == songId }
            if (song != null) return listOf(song)
        }
        return null
    }

}