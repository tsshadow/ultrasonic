package org.moire.ultrasonic.service.ultrasonic.androidauto

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PLAYLIST
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.ultrasonic.androidauto.MediaLibraryBase.Companion.add
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.ifNotNull
import org.moire.ultrasonic.util.buildMediaItem
import org.moire.ultrasonic.util.toMediaItem
import timber.log.Timber
import java.util.Calendar

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
class MediaLibraryBrowser(
    val mainScope: CoroutineScope,
    val serviceScope: CoroutineScope,
    val musicService: MusicService
) : MediaLibraryBase() {
    lateinit var dataProvider: MediaLibraryDataProvider

    private val isOffline get() = ActiveServerProvider.isOffline()
    private val activeServerProvider: ActiveServerProvider by inject(ActiveServerProvider::class.java)
    private val musicFolderId get() = activeServerProvider.getActiveServer().musicFolderId

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
        Timber.i("onGetLibraryRoot")
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
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
        Timber.i("onGetChildren")
        return loadChildren(parentId)
    }

    @Suppress("ReturnCount", "ComplexMethod")
    fun loadChildren(
        parentId: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("onLoadChildren")
        Timber.d("AutoMediaBrowserService onLoadChildren called. ParentId: %s", parentId)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val lastYear = Calendar.getInstance().get(Calendar.YEAR) - 1
        val parentIdParts = parentId.split('|')

        return when (parentIdParts.first()) {
            MEDIA_ROOT_ID -> getRootItems()
            MEDIA_LIBRARY_ID -> getLibrary()
            MEDIA_ARTIST_ID -> getArtists()
            MEDIA_ARTIST_SECTION -> getArtists(parentIdParts[1])
            MEDIA_ALBUM_ID -> getAlbums(AlbumListType.SORTED_BY_NAME)
            MEDIA_ALBUM_PAGE_ID -> getAlbums(
                AlbumListType.fromName(parentIdParts[1]),
                parentIdParts[2].toInt()
            )

            MEDIA_PLAYLIST_ID -> getPlaylists()
            MEDIA_ALBUM_FREQUENT_ID -> getAlbums(AlbumListType.FREQUENT)
            MEDIA_ALBUM_NEWEST_ID -> getAlbums(AlbumListType.NEWEST)
            MEDIA_ALBUM_RECENT_ID -> getAlbums(AlbumListType.RECENT)
            MEDIA_ALBUM_RANDOM_ID -> getAlbums(AlbumListType.RANDOM)
            MEDIA_ALBUM_STARRED_ID -> getAlbums(AlbumListType.STARRED)
            MEDIA_SONG_RANDOM_ID -> getRandomSongs()
            MEDIA_SONG_RECENT -> getRecentSongs()
            MEDIA_LIVESET_RANDOM_ID -> getRandomLivesets()
            MEDIA_LIVESET_RECENT -> getRecentLivesets()

            // Genre -> songs
            MEDIA_GENRES_SONGS -> getGenres(null, "short")
            MEDIA_GENRE_SONGS -> getGenre(parentIdParts[1], null, "short")
            MEDIA_GENRES_SONGS_THIS_YEAR -> getGenres(year, "short")
            MEDIA_GENRES_SONGS_LAST_YEAR -> getGenres(lastYear, "short")
            MEDIA_GENRE_SONGS_THIS_YEAR -> getGenre(parentIdParts[1], year, "short")
            MEDIA_GENRE_SONGS_LAST_YEAR -> getGenre(parentIdParts[1], lastYear , "short")

            // Genre -> livesets
            MEDIA_GENRES_LIVESETS -> getGenres(null, "long")
            MEDIA_GENRE_LIVESETS -> getGenre(parentIdParts[1], null, "long")
            MEDIA_GENRES_LIVESETS_THIS_YEAR -> getGenres(year, "long")
            MEDIA_GENRES_LIVESETS_LAST_YEAR -> getGenres(lastYear, "long")
            MEDIA_GENRE_LIVESETS_THIS_YEAR -> getGenre(parentIdParts[1], year, "long")
            MEDIA_GENRE_LIVESETS_LAST_YEAR -> getGenre(parentIdParts[1], lastYear, "long")

            MEDIA_SONG_STARRED_ID -> getStarredSongs()
            MEDIA_SHARE_ID -> getShares()
            MEDIA_BOOKMARK_ID -> getBookmarks()
            MEDIA_PODCAST_ID -> getPodcasts()
            MEDIA_PLAYLIST_ITEM -> getPlaylist(parentIdParts[1], parentIdParts[2])
            MEDIA_ARTIST_ITEM -> getAlbumsForArtist(
                parentIdParts[1],
                parentIdParts[2]
            )

            MEDIA_ALBUM_ITEM -> getSongsForAlbum(parentIdParts[1], parentIdParts[2])
            MEDIA_SHARE_ITEM -> getSongsForShare(parentIdParts[1])
            MEDIA_PODCAST_ITEM -> getPodcastEpisodes(parentIdParts[1])
            else -> Futures.immediateFuture(LibraryResult.ofItemList(listOf(), null))
        }
    }

    private fun getRootItems(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        if (!isOffline) {
            mediaItems.add(
                R.string.music_library_label,
                MEDIA_LIBRARY_ID,
                null,
                isBrowsable = true,
                mediaType = MEDIA_TYPE_FOLDER_MIXED,
                icon = R.drawable.ic_library
            )
        }

        mediaItems.add(
            R.string.main_artists_title,
            MEDIA_ARTIST_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_ARTISTS,
            icon = R.drawable.ic_artist
        )

        if (!isOffline) {
            mediaItems.add(
                R.string.main_albums_title,
                MEDIA_ALBUM_ID,
                null,
                isBrowsable = true,
                mediaType = MEDIA_TYPE_FOLDER_ALBUMS,
                icon = R.drawable.ic_menu_browse
            )
        }

        mediaItems.add(
            R.string.playlist_label,
            MEDIA_PLAYLIST_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_PLAYLISTS,
            icon = R.drawable.ic_menu_playlists
        )

        return Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, null))
    }

    private fun getLibrary(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("GetLibrary")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        // Genres
        mediaItems.add(
            R.string.main_title_all_songs,
            MEDIA_GENRES_SONGS,
            R.string.main_genres_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        mediaItems.add(
            R.string.main_title_songs_this_year,
            MEDIA_GENRES_SONGS_THIS_YEAR,
            R.string.main_genres_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        mediaItems.add(
            R.string.main_title_songs_last_year,
            MEDIA_GENRES_SONGS_LAST_YEAR,
            R.string.main_genres_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        mediaItems.add(
            R.string.main_title_all_livesets,
            MEDIA_GENRES_LIVESETS,
            R.string.main_genres_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        mediaItems.add(
            R.string.main_title_livesets_this_year,
            MEDIA_GENRES_LIVESETS_THIS_YEAR,
            R.string.main_genres_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        mediaItems.add(
            R.string.main_title_livesets_last_year,
            MEDIA_GENRES_LIVESETS_LAST_YEAR,
            R.string.main_genres_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )

        // Songs
        mediaItems.add(
            R.string.main_songs_random,
            MEDIA_SONG_RANDOM_ID,
            R.string.main_songs_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        mediaItems.add(
            R.string.main_songs_recent,
            MEDIA_SONG_RECENT,
            R.string.main_songs_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        // Livesets
        mediaItems.add(
            R.string.main_songs_random,
            MEDIA_LIVESET_RANDOM_ID,
            R.string.main_livesets_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )
        mediaItems.add(
            R.string.main_songs_recent,
            MEDIA_LIVESET_RECENT,
            R.string.main_livesets_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )

        mediaItems.add(
            R.string.main_songs_starred,
            MEDIA_SONG_STARRED_ID,
            R.string.main_songs_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )

        // Albums
        mediaItems.add(
            R.string.main_albums_newest,
            MEDIA_ALBUM_NEWEST_ID,
            R.string.main_albums_title
        )

        mediaItems.add(
            R.string.main_albums_recent,
            MEDIA_ALBUM_RECENT_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_frequent,
            MEDIA_ALBUM_FREQUENT_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_random,
            MEDIA_ALBUM_RANDOM_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_starred,
            MEDIA_ALBUM_STARRED_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        // Other
        mediaItems.add(R.string.button_bar_shares, MEDIA_SHARE_ID, null)
        mediaItems.add(R.string.button_bar_bookmarks, MEDIA_BOOKMARK_ID, null)
        mediaItems.add(R.string.button_bar_podcasts, MEDIA_PODCAST_ID, null)

        return Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, null))
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getSongsForAlbum(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("getSongsForAlbum")
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future { dataProvider.listSongsInMusicService(id, name) }.await()

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

            return@future LibraryResult.ofItemList(mediaItems, null)
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

            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }


    private fun getRandomSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                callWithErrorHandling { musicService.getRandomSongs(DISPLAY_LIMIT) }
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getRecentSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                callWithErrorHandling {
                    musicService.getSongs(
                        Filters(Filter("LENGTH", "short")),
                        null,
                        null,
                        maxSongs,
                        0,
                        "AddedDesc"
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getRandomLivesets(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                callWithErrorHandling {
                    musicService.getSongs(
                        Filters(Filter("LENGTH", "long")),
                        null,
                        null,
                        maxSongs,
                        0,
                        "Random"
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getRecentLivesets(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                callWithErrorHandling {
                    musicService.getSongs(
                        Filters(Filter("LENGTH", "long")),
                        null,
                        null,
                        maxSongs,
                        0,
                        "AddedDesc"
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getGenres(
        year: Int?,
        length: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        Timber.i("getGenres: year=$year length=$length")
        return mainScope.future {
            var genres = serviceScope.future {
                callWithErrorHandling { musicService.getGenres(true, year, length) }
            }.await()

            val mediaIdPrefix = if (year == null) {
                if (length == "short") {
                    MEDIA_GENRE_SONGS
                } else {
                    MEDIA_GENRE_LIVESETS
                }
            } else {
                if (length == "short") {
                    if (year == Calendar.getInstance().get(Calendar.YEAR)) {
                        MEDIA_GENRE_SONGS_THIS_YEAR
                    } else {
                        MEDIA_GENRE_SONGS_LAST_YEAR
                    }
                } else {
                    if (year == Calendar.getInstance().get(Calendar.YEAR)) {
                        MEDIA_GENRE_LIVESETS_THIS_YEAR
                    } else {
                        MEDIA_GENRE_LIVESETS_LAST_YEAR
                    }
                }
            }
            Timber.i("getGenres: mediaIdPrefix=$mediaIdPrefix $year")

            if (genres != null) {
                genres = genres.sortedByDescending { Genre -> Genre.songCount }
            }

            genres?.forEach {
                mediaItems.add(
                    it.name + " " + it.songCount,
                    mediaIdPrefix + "|" + it.name,
                    R.string.main_genres_title,
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_PLAYLIST
                )

            }
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getGenre(
        genre: String,
        year: Int?,
        length: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        Timber.i("getGenre: genre=$genre year=$year length=$length")
        return mainScope.future {
            val songs = serviceScope.future {
                val filters = Filters(Filter("GENRE", genre))
                filters.add(Filter("LENGTH", length))
                year.ifNotNull { filters.add(Filter("YEAR", year.toString())) }
                val sortMethod = if (year !== null) "AddedDesc" else "Random"

                callWithErrorHandling {
                    musicService.getSongs(
                        filters,
                        null,
                        null,
                        maxSongs,
                        0,
                        sortMethod
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }
}