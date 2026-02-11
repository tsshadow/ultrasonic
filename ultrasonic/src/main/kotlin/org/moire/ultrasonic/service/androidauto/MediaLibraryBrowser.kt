/*
 * MediaLibraryBrowser.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service.androidauto

import TileInfo
import TileStorage
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PLAYLIST
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.buildMediaItem
import org.moire.ultrasonic.util.toMediaItem
import timber.log.Timber

/**
 * @class MediaLibraryBrowser
 * @brief Handles media browsing functionality in Android Auto.
 *
 * This class is responsible for managing media content, fetching and organizing albums, playlists,
 * artists, and other media items for Android Auto. It serves as the primary interface for retrieving
 * media items and structuring them for navigation.
 *
 * @details
 * - Provides access to root media items.
 * - Loads children for different media categories (albums, playlists, genres, etc.).
 * - Fetches and formats media metadata to be used in Android Auto.
 * - Uses coroutines for asynchronous data fetching.
 *
 * @note This class interacts with `MediaLibraryDataProvider` for data access and `MusicService`
 * for retrieving media items.
 */
@UnstableApi
class MediaLibraryBrowser(
    val mainScope: CoroutineScope,
    val serviceScope: CoroutineScope,
    val musicService: MusicService
) : MediaLibraryBase() {
    lateinit var dataProvider: MediaLibraryDataProvider

    /**
     * Called when a {@link MediaBrowser} requests the root {@link MediaItem} by {@link
     * MediaBrowser#getLibraryRoot(LibraryParams)}.
     *
     * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
     * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
     * {@link Futures#immediateFuture(Object)}.
     *
     * <p>The {@link LibraryResult#params} may differ from the given {@link LibraryParams params}
     * if the session can't provide a root that matches with the {@code params}.
     *
     * <p>To allow browsing the media library, return a {@link LibraryResult} with {@link
     * LibraryResult#RESULT_SUCCESS} and a root {@link MediaItem} with a valid {@link
     * MediaItem#mediaId}. The media id is required for the browser to get the children under the
     * root.
     *
     * <p>Interoperability: If this callback is called because a legacy {@link
     * android.support.v4.media.MediaBrowserCompat} has requested a {@link
     * androidx.media.MediaBrowserServiceCompat.BrowserRoot}, then the main thread may be blocked
     * until the returned future is done. If your service may be queried by a legacy {@link
     * android.support.v4.media.MediaBrowserCompat}, you should ensure that the future completes
     * quickly to avoid blocking the main thread for a long period of time.
     *
     * @param session The session for this event.
     * @param browser The browser information.
     * @param params The optional parameters passed by the browser.
     * @return A pending result that will be resolved with a root media item.
     * @see SessionCommand#COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT
     */
    fun getLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Timber.i("onGetLibraryRoot session: %s, browser: %s", session, browser)
        return Futures.immediateFuture(
            LibraryResult.ofItem(
                buildMediaItem(
                    "Root Folder",
                    MEDIA_ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_FOLDER_MIXED
                ),
                params
            )
        )
    }

    fun getItem(
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Timber.i("onGetItem")

        val tracks = dataProvider.tracksFromMediaId(mediaId)
        val mediaItem = tracks?.firstOrNull()?.toMediaItem()
        // TODO:
        // Create LRU Cache of MediaItems, fill it in the other calls
        // and retrieve it here.

        return if (mediaItem != null) {
            Futures.immediateFuture(
                LibraryResult.ofItem(mediaItem, null)
            )
        } else {
            Futures.immediateFuture(
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            )
        }
    }

    private fun getBookmarks(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getBookmarks")
        val mediaItems: MutableList<MediaItem> = ArrayList()
        return mainScope.future {
            val bookmarks = serviceScope.future {
                callWithErrorHandling { musicService.getBookmarks() }
            }.await()

            if (bookmarks != null) {
                val songs = Util.getSongsFromBookmarks(bookmarks)

                songs.getTracks().map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_BOOKMARK_ITEM, song.id).joinToString("|")
                        )
                    )
                }
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getSongsForShare(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getSongsForShare")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val shares = serviceScope.future {
                callWithErrorHandling { musicService.getShares(false) }
            }.await()

            val selectedShare = shares?.firstOrNull { share -> share.id == id }
            if (selectedShare != null) {
                if (selectedShare.getEntries().count() > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SHARE_ITEM, id).joinToString("|"))
                }

                selectedShare.getEntries().map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_SHARE_SONG_ITEM, id, song.id).joinToString("|")
                        )
                    )
                }
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    fun getChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.i("getChildren session: %s, browser: %s, params: %s", session, browser, params)
        return loadChildren(parentId, page, pageSize)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun loadChildren(
        parentId: String,
        page: Int,
        pageSize: Int
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("AutoMediaBrowserService onLoadChildren called. ParentId: %s", parentId)
        // ✅ Cap and sanitize page/pageSize
        val safePage = if (page < 0) 0 else page
        val safePageSize = if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) DEFAULT_PAGE_SIZE else pageSize

        val parts = parentId.split('|')
        val action =
            parts.firstOrNull() ?: return emptyResult("Missing action in parentId: $parentId")

        return when (action) {
            MEDIA_ROOT_ID -> getRootItems()
            MEDIA_LIBRARY_ID -> getLibrary()
            MEDIA_SONGS_ID -> getSongsLibrary(UApp.applicationContext())
            MEDIA_LIVESETS_ID -> getLivesetsLibrary(UApp.applicationContext())
            MEDIA_ARTIST_ID -> getArtists()
            MEDIA_ARTIST_SECTION -> {
                val section = parts.getOrNull(INDEX_ID)
                if (section != null) getArtists(section) else emptyResult("Missing section in $parentId")
            }

            MEDIA_ALBUM_ID -> getAlbums(AlbumListType.SORTED_BY_NAME)
            MEDIA_ALBUM_PAGE_ID -> {
                val listType = parts.getOrNull(INDEX_ID)?.let { AlbumListType.fromName(it) }
                val lPage = parts.getOrNull(INDEX_NAME)?.toIntOrNull()
                if (listType != null && lPage != null) getAlbums(listType, lPage)
                else emptyResult("Invalid album page params in $parentId")
            }

            MEDIA_PLAYLIST_ID -> getPlaylists()
            MEDIA_ALBUM_FREQUENT_ID -> getAlbums(AlbumListType.FREQUENT)
            MEDIA_ALBUM_NEWEST_ID -> getAlbums(AlbumListType.NEWEST)
            MEDIA_ALBUM_RECENT_ID -> getAlbums(AlbumListType.RECENT)
            MEDIA_ALBUM_RANDOM_ID -> getAlbums(AlbumListType.RANDOM)
            MEDIA_ALBUM_STARRED_ID -> getAlbums(AlbumListType.STARRED)
            MEDIA_SONG_STARRED_ID -> getStarredSongs()
            MEDIA_SONG_RANDOM_ID -> {
                val length = parts.getOrNull(INDEX_LENGTH)
                if (length != null) getSongs(length = length, sortMethod = "Random")
                else emptyResult("Missing length in $parentId")
            }

            MEDIA_SONG_RECENT -> {
                val length = parts.getOrNull(INDEX_LENGTH)
                if (length != null) getSongs(length = length, sortMethod = "AddedDesc")
                else emptyResult("Missing length in $parentId")
            }

            MEDIA_GET_GENRES -> {
                val length = parts.getOrNull(INDEX_LENGTH)
                if (length != null) getGenres(length) else emptyResult("Missing length in $parentId")
            }

            MEDIA_GET_YEARS -> {
                val length = parts.getOrNull(INDEX_LENGTH)
                val genre = parts.getOrNull(INDEX_GENRE)
                if (length != null && genre != null) getYears(length, genre)
                else emptyResult("Missing length or genre in $parentId")
            }

            MEDIA_GET_SORT_METHOD -> {
                val length = parts.getOrNull(INDEX_LENGTH)
                val genre = parts.getOrNull(INDEX_GENRE)
                val year = parts.getOrNull(INDEX_YEAR)
                if (length != null && genre != null && year != null) getSortMethod(
                    length,
                    genre,
                    year
                )
                else emptyResult("Missing sort params in $parentId")
            }

            MEDIA_GET_SONGS_BY_GENRE -> {
                val length = parts.getOrNull(INDEX_LENGTH)
                val genreList = parts.getOrNull(INDEX_GENRE)
                    ?.takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?: emptyList()

                val yearList = parts.getOrNull(INDEX_YEAR)
                    ?.takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                    ?: emptyList()

                val sortMethod = parts.getOrNull(INDEX_SORT_METHOD)
                val festivalLineup = parts.getOrNull(INDEX_FESTIVAL_LINEUP)
                val ratingMin = parts.getOrNull(INDEX_RATING_MIN)?.toIntOrNull() ?: 0
                val ratingMax = parts.getOrNull(INDEX_RATING_MAX)?.toIntOrNull() ?: 5

                if (length != null && sortMethod != null) {
                    getGenre(
                        length = length,
                        genres = genreList,
                        years = yearList,
                        sortMethod = sortMethod,
                        page = safePage,
                        pageSize = safePageSize,
                        festivalLineup = festivalLineup,
                        ratingMin = ratingMin,
                        ratingMax = ratingMax,
                    )
                } else {
                    emptyResult("Invalid genre/song filter in $parentId")
                }
            }

            MEDIA_SHARE_ID -> getShares()
            MEDIA_BOOKMARK_ID -> getBookmarks()
            MEDIA_PODCAST_ID -> getPodcasts()
            MEDIA_PLAYLIST_ITEM -> {
                val playlistId = parts.getOrNull(INDEX_ID)
                val name = parts.getOrNull(INDEX_NAME)
                if (playlistId != null && name != null) getPlaylist(playlistId, name)
                else emptyResult("Invalid playlist item in $parentId")
            }

            MEDIA_ARTIST_ITEM -> {
                val artistId = parts.getOrNull(INDEX_ID)
                val artistName = parts.getOrNull(INDEX_NAME)
                if (artistId != null && artistName != null) getAlbumsForArtist(artistId, artistName)
                else emptyResult("Invalid artist item in $parentId")
            }

            MEDIA_ALBUM_ITEM -> {
                val albumId = parts.getOrNull(INDEX_ID)
                val albumName = parts.getOrNull(INDEX_NAME)
                if (albumId != null && albumName != null) getSongsForAlbum(albumId, albumName)
                else emptyResult("Invalid album item in $parentId")
            }

            MEDIA_SHARE_ITEM -> {
                val shareId = parts.getOrNull(INDEX_ID)
                if (shareId != null) getSongsForShare(shareId)
                else emptyResult("Missing shareId in $parentId")
            }

            MEDIA_PODCAST_ITEM -> {
                val podcastId = parts.getOrNull(INDEX_ID)
                if (podcastId != null) getPodcastEpisodes(podcastId)
                else emptyResult("Missing podcastId in $parentId")
            }

            else -> emptyResult("Unknown action in $parentId")
        }
    }

    private fun getRootItems(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        mediaItems.add(
            R.string.music_library_label,
            MEDIA_LIBRARY_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_MIXED,
            icon = R.drawable.ic_library
        )

        mediaItems.add(
            "Songs",
            MEDIA_SONGS_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_PLAYLISTS,
            icon = R.drawable.ic_stat_play
        )

        mediaItems.add(
            "Livesets",
            MEDIA_LIVESETS_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_PLAYLISTS,
            icon = R.drawable.ic_menu_browse
        )

        mediaItems.add(
            R.string.playlist_label,
            MEDIA_PLAYLIST_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_PLAYLISTS,
            icon = R.drawable.ic_menu_playlists
        )

        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null))
    }

    private fun getLibrary(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("GetLibrary")
        val mediaItems: MutableList<MediaItem> = ArrayList()
        val presets = listOf(
            TileInfo("Recent Songs"),
            TileInfo("Random Songs", sortMethod = "Random"),
            TileInfo("Recent Livesets", length = "long"),
            TileInfo("Random Livesets", sortMethod = "Random", length = "long"),
            TileInfo("Starred Songs", ratingMin = 5)
        )

        presets.mapTo(mediaItems) { it.toMediaItem() }

        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null))
    }

    private fun getSongsLibrary(context: Context): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getSongsLibrary")

        val mediaItems: MutableList<MediaItem> = ArrayList()
        // Default hardcoded presets
        val presets = listOf(
            TileInfo("Search"),
            TileInfo("Recent"),
            TileInfo("Random", sortMethod = "Random"),
            TileInfo("Starred", ratingMin = 5),
        )

        // Add hardcoded presets
        presets.mapTo(mediaItems) { it.toMediaItem() }

        // Load user-defined tiles from storage and filter favorites
        val savedFavoriteTiles = TileStorage.loadTiles(context, "song")
            .filter { it.favorite }

        // Add favorite user-defined tiles
        savedFavoriteTiles.mapTo(mediaItems) { it.toMediaItem() }

        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null))
    }

    private fun getLivesetsLibrary(context: Context): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getSongsLibrary")

        val mediaItems: MutableList<MediaItem> = ArrayList()
        // Default hardcoded presets
        val presets = listOf(
            TileInfo("Search", length = "long"),
            TileInfo("Recent", length = "long"),
            TileInfo("Random", sortMethod = "Random", length = "long"),
            TileInfo("Starred", ratingMin = 5, length = "long"),
        )

        // Add hardcoded presets
        presets.mapTo(mediaItems) { it.toMediaItem() }

        // Load user-defined tiles from storage and filter favorites
        val savedFavoriteTiles = TileStorage.loadTiles(context, "liveset")
            .filter { it.favorite }

        // Add favorite user-defined tiles
        savedFavoriteTiles.mapTo(mediaItems) { it.toMediaItem() }

        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null))
    }

    private fun getArtists(
        section: String? = null
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        // It seems double scoping is required: Media3 requires the Main thread, network operations with musicService forbid the Main thread...
        return mainScope.future {
            var childMediaId: String = MEDIA_ARTIST_ITEM

            var artists = serviceScope.future {
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    // TODO this list can be big so we're not refreshing.
                    //  Maybe a refresh menu item can be added
                    callWithErrorHandling { musicService.getArtists(false) }
                } else {
                    // This will be handled at getSongsForAlbum, which supports navigation
                    childMediaId = MEDIA_ALBUM_ITEM
                    callWithErrorHandling { musicService.getIndexes(musicFolderId, false) }
                }
            }.await()

            if (artists != null) {
                if (section != null) {
                    artists = artists.filter { artist ->
                        getSectionFromName(artist.name ?: "") == section
                    }
                }

                // If there are too many artists, create alphabetic index of them
                if (section == null && artists.count() > DISPLAY_LIMIT) {
                    val index = mutableListOf<String>()
                    // TODO This sort should use ignoredArticles somehow...
                    artists = artists.sortedBy { artist -> artist.name }
                    artists.map { artist ->
                        val currentSection = getSectionFromName(artist.name ?: "")
                        if (!index.contains(currentSection)) {
                            index.add(currentSection)
                            mediaItems.add(
                                currentSection,
                                listOf(MEDIA_ARTIST_SECTION, currentSection).joinToString("|")
                            )
                        }
                    }
                } else {
                    artists.map { artist ->
                        mediaItems.add(
                            artist.name ?: "",
                            listOf(childMediaId, artist.id, artist.name).joinToString("|")
                        )
                    }
                }
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getAlbumsForArtist(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getAlbumsForArtist")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val albums = serviceScope.future {
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    callWithErrorHandling { musicService.getAlbumsOfArtist(id, name, false) }
                } else {
                    callWithErrorHandling {
                        musicService.getMusicDirectory(id, name, false).getAlbums()
                    }
                }
            }.await()

            albums?.map { album ->
                mediaItems.add(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                        .joinToString("|")
                )
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getSongsForAlbum(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getSongsForAlbum")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs =
                serviceScope.future { dataProvider.listSongsInMusicService(id, name) }.await()

            if (songs != null) {
                if (songs.getChildren(includeDirs = true, includeFiles = false).isEmpty() &&
                    songs.getChildren(includeDirs = false, includeFiles = true).isNotEmpty()
                ) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_ALBUM_ITEM, id, name).joinToString("|"))
                }

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getChildren().take(DISPLAY_LIMIT).toMutableList()

                items.sortWith { o1, o2 ->
                    if (o1.isDirectory && o2.isDirectory) {
                        (o1.title ?: "").compareTo(o2.title ?: "")
                    } else if (o1.isDirectory) {
                        -1
                    } else {
                        1
                    }
                }

                items.map { item ->
                    if (item.isDirectory) {
                        mediaItems.add(
                            item.title ?: "",
                            listOf(MEDIA_ALBUM_ITEM, item.id, item.name).joinToString("|")
                        )
                    } else if (item is Track) {
                        mediaItems.add(
                            item.toMediaItem(
                                listOf(
                                    MEDIA_ALBUM_SONG_ITEM,
                                    id,
                                    name,
                                    item.id
                                ).joinToString("|")
                            )
                        )
                    }
                }
            }

            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getAlbums(
        type: AlbumListType,
        page: Int? = null
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getAlbums")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val offset = (page ?: 0) * DISPLAY_LIMIT

            val albums = serviceScope.future {
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    callWithErrorHandling {
                        musicService.getAlbumList2(
                            type,
                            DISPLAY_LIMIT,
                            offset,
                            null
                        )
                    }
                } else {
                    callWithErrorHandling {
                        musicService.getAlbumList(
                            type,
                            DISPLAY_LIMIT,
                            offset,
                            null
                        )
                    }
                }
            }.await()

            albums?.map { album ->
                mediaItems.add(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                        .joinToString("|")
                )
            }

            if ((albums?.size ?: 0) >= DISPLAY_LIMIT) {
                mediaItems.add(
                    R.string.search_more,
                    listOf(MEDIA_ALBUM_PAGE_ID, type.typeName, (page ?: 0) + 1).joinToString("|"),
                    null
                )
            }

            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getPlaylists(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getPlaylists")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val playlists = serviceScope.future {
                callWithErrorHandling { musicService.getPlaylists(true) }
            }.await()

            playlists?.map { playlist ->
                mediaItems.add(
                    playlist.name,
                    listOf(MEDIA_PLAYLIST_ITEM, playlist.id, playlist.name)
                        .joinToString("|"),
                    mediaType = MEDIA_TYPE_PLAYLIST
                )
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getPlaylist(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getPlaylist")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val content = serviceScope.future {
                callWithErrorHandling { musicService.getPlaylist(id, name) }
            }.await()

            if (content != null) {
                if (content.size > 1) {
                    mediaItems.addPlayAllItem(
                        listOf(MEDIA_PLAYLIST_ITEM, id, name).joinToString("|")
                    )
                }

                // Playlist should be cached as it may contain random elements
                dataProvider.playlistCache = content.getTracks()
                dataProvider.playlistCache!!.take(DISPLAY_LIMIT).map { item ->
                    mediaItems.add(
                        item.toMediaItem(
                            listOf(
                                MEDIA_PLAYLIST_SONG_ITEM,
                                id,
                                name,
                                item.id
                            ).joinToString("|")
                        )
                    )
                }
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getSongs(
        length: String?,
        sortMethod: String?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        val filters = Filters()
        if (length != null) {
            filters.add(Filter("LENGTH", length))
        }

        return mainScope.future {
            val songs = serviceScope.future {
                callWithErrorHandling {
                    musicService.getSongs(
                        filters,
                        null,
                        null,
                        count = maxSongs,
                        0,
                        sortMethod ?: "AddedDesc"
                    )
                }
            }.await()

            if (songs != null) {
                if (songs.size > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_RANDOM_ID).joinToString("|"))
                }

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getTracks()
                dataProvider.randomSongsCache = items
                items.map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_SONG_RANDOM_ITEM, song.id).joinToString("|")
                        )
                    )
                }
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getGenres(
        length: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        Timber.i("getGenres:length=$length")
        return mainScope.future {
            var genres = serviceScope.future {
                callWithErrorHandling { musicService.getGenres(true, null, length) }
            }.await()


            if (genres != null) {
                genres = genres.sortedByDescending { genre -> genre.songCount }
            }

            genres?.forEach {
                mediaItems.add(
                    it.name,
                    "$MEDIA_GET_YEARS|$length|${it.name}",
                    R.string.main_genres_title,
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_PLAYLIST
                )
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getYears(
        length: String,
        genre: String?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        Timber.i("getYears: genre=$genre length=$length")
        return mainScope.future {
            var years = serviceScope.future {
                callWithErrorHandling {
                    musicService.getTags(
                        true, "YEAR",
                        year = null,
                        length = length
                    )
                }
            }.await()

            if (years != null) {
                years = years.sortedByDescending { y -> y.name }
            }
            mediaItems.add(
                "All",
                "$MEDIA_GET_SORT_METHOD|$length|$genre|",
                R.string.main_genres_title,
                isBrowsable = true,
                mediaType = MEDIA_TYPE_PLAYLIST
            )
            years?.forEach {
                mediaItems.add(
                    it.name,
                    "$MEDIA_GET_SORT_METHOD|$length|$genre|${it.name}",
                    R.string.main_genres_title,
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_PLAYLIST
                )

            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getSortMethod(
        length: String?,
        genre: String?,
        year: String?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        Timber.i("getSortMethod: genre=$genre length=$length year=$year")
        return mainScope.future {

            val sortMethods = mapOf(
                "Willekeurig" to "Random",
                "Release datum" to "DateDescAndRelease",
                "Recent toegevoegd" to "AddedDesc",
                "Recent aangepast" to "LastWrittenDesc"
            )

            sortMethods.forEach { (name, value) ->
                mediaItems.add(
                    name,
                    "$MEDIA_GET_SONGS_BY_GENRE|$length|$genre|$year|$value",
                    R.string.main_genres_title,
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_PLAYLIST
                )
            }

            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getGenre(
        length: String,
        genres: List<String>,
        years: List<Int>,
        sortMethod: String,
        festivalLineup: String? = null,
        page: Int,
        pageSize: Int,
        ratingMin: Int? = 0,
        ratingMax: Int? = 5
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()
        Timber.i("getGenre: genres=$genres years=$years length=$length page=$page pageSize=$pageSize")

        return mainScope.future {
            val songs = serviceScope.future {
                val filters = Filters()

                if (genres.isNotEmpty()) {
                    filters.add(Filter("GENRE", genres))
                }

                filters.add(Filter("LENGTH", length))

                if (years.isNotEmpty()) {
                    filters.add(Filter("YEAR", years))
                }

                val offset = page * pageSize

                callWithErrorHandling {
                    musicService.getSongs(
                        filters = filters,
                        ratingMin = ratingMin,
                        ratingMax = ratingMax,
                        count = pageSize,
                        offset = offset,
                        sortMethod = sortMethod,
                        festivalLineup = festivalLineup
                    )
                }
            }.await()

            if (songs != null) {
                if (songs.size > 1 && page == 0) {
                    mediaItems.addPlayAllItem(
                        listOf(MEDIA_SONG_RANDOM_ID).joinToString("|")
                    )
                }
                val items = songs.getTracks()
                dataProvider.randomSongsCache = items
                items.map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_SONG_RANDOM_ITEM, song.id).joinToString("|")
                        )
                    )
                }
            }

            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }



    private fun getStarredSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                dataProvider.listStarredSongsInMusicService()
            }.await()

            if (songs != null) {
                if (songs.songs.count() > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_STARRED_ID).joinToString("|"))
                }

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.songs.take(DISPLAY_LIMIT)
                dataProvider.starredSongsCache = items
                items.map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_SONG_STARRED_ITEM, song.id).joinToString("|")
                        )
                    )
                }
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getPodcastEpisodes(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getPodcastEpisodes")
        val mediaItems: MutableList<MediaItem> = ArrayList()
        return mainScope.future {
            val episodes = serviceScope.future {
                callWithErrorHandling { musicService.getPodcastEpisodes(id) }
            }.await()

            if (episodes != null) {
                if (episodes.getTracks().count() > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_PODCAST_ITEM, id).joinToString("|"))
                }

                episodes.getTracks().map { episode ->
                    mediaItems.add(
                        episode.toMediaItem(
                            listOf(MEDIA_PODCAST_EPISODE_ITEM, id, episode.id)
                                .joinToString("|")
                        )
                    )
                }
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getPodcasts(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getPodcasts")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val podcasts = serviceScope.future {
                callWithErrorHandling { musicService.getPodcastsChannels(false) }
            }.await()

            podcasts?.map { podcast ->
                mediaItems.add(
                    podcast.title ?: "",
                    listOf(MEDIA_PODCAST_ITEM, podcast.id).joinToString("|"),
                    mediaType = MEDIA_TYPE_FOLDER_MIXED
                )
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().uppercaseChar()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }


    private fun getShares(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getShares")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val shares = serviceScope.future {
                callWithErrorHandling { musicService.getShares(false) }
            }.await()

            shares?.map { share ->
                mediaItems.add(
                    share.name ?: "",
                    listOf(MEDIA_SHARE_ITEM, share.id)
                        .joinToString("|"),
                    mediaType = MEDIA_TYPE_FOLDER_MIXED
                )
            }
            return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
        }
    }

    private fun TileInfo.toMediaItem(): MediaItem {
        val context = UApp.applicationContext()

        // Avoid passing empty genre/year lists
        val genreValue = genre?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: ""
        val yearValue = year?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: ""

        val mediaId = when {
            title.equals("Random", ignoreCase = true) -> "$MEDIA_SONG_RANDOM_ID|$length"
            title.equals("Recent", ignoreCase = true) -> "$MEDIA_SONG_RECENT|$length"
            title.equals("Starred", ignoreCase = true) -> "$MEDIA_SONG_STARRED_ID|$length"
            title.equals("Search", ignoreCase = true) -> "$MEDIA_GET_GENRES|$length"
            else -> "$MEDIA_GET_SONGS_BY_GENRE|$length|$genreValue|$yearValue|$sortMethod|$festivalLineup|$ratingMin|$ratingMax"
        }

        val groupName = context.getString(
            if (length == "long") R.string.main_livesets_title
            else R.string.main_songs_title
        )

        return buildMediaItem(
            title = this.title,
            mediaId = mediaId,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST,
            group = groupName
        )
    }

    companion object {
        private const val MAX_PAGE_SIZE = 500
        private const val DEFAULT_PAGE_SIZE = 50

        private const val INDEX_LENGTH = 1
        private const val INDEX_GENRE = 2
        private const val INDEX_YEAR = 3
        private const val INDEX_SORT_METHOD = 4
        private const val INDEX_FESTIVAL_LINEUP = 5
        private const val INDEX_RATING_MIN = 6
        private const val INDEX_RATING_MAX = 7

        private const val INDEX_ID = 1
        private const val INDEX_NAME = 2
    }
}
