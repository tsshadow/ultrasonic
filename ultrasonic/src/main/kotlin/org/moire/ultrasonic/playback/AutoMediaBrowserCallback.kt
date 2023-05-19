/*
 * AutoMediaBrowserCallback.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.FOLDER_TYPE_ALBUMS
import androidx.media3.common.MediaMetadata.FOLDER_TYPE_ARTISTS
import androidx.media3.common.MediaMetadata.FOLDER_TYPE_MIXED
import androidx.media3.common.MediaMetadata.FOLDER_TYPE_PLAYLISTS
import androidx.media3.common.MediaMetadata.FOLDER_TYPE_TITLES
import androidx.media3.common.Rating
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RatingManager
import org.moire.ultrasonic.util.MainThreadExecutor
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.buildMediaItem
import org.moire.ultrasonic.util.toMediaItem
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

private const val MEDIA_ROOT_ID = "MEDIA_ROOT_ID"
private const val MEDIA_ALBUM_ID = "MEDIA_ALBUM_ID"
private const val MEDIA_ALBUM_PAGE_ID = "MEDIA_ALBUM_PAGE_ID"
private const val MEDIA_ALBUM_NEWEST_ID = "MEDIA_ALBUM_NEWEST_ID"
private const val MEDIA_ALBUM_RECENT_ID = "MEDIA_ALBUM_RECENT_ID"
private const val MEDIA_ALBUM_FREQUENT_ID = "MEDIA_ALBUM_FREQUENT_ID"
private const val MEDIA_ALBUM_RANDOM_ID = "MEDIA_ALBUM_RANDOM_ID"
private const val MEDIA_ALBUM_STARRED_ID = "MEDIA_ALBUM_STARRED_ID"
private const val MEDIA_SONG_RANDOM_ID = "MEDIA_SONG_RANDOM_ID"
private const val MEDIA_SONG_STARRED_ID = "MEDIA_SONG_STARRED_ID"
private const val MEDIA_ARTIST_ID = "MEDIA_ARTIST_ID"
private const val MEDIA_LIBRARY_ID = "MEDIA_LIBRARY_ID"
private const val MEDIA_PLAYLIST_ID = "MEDIA_PLAYLIST_ID"
private const val MEDIA_SHARE_ID = "MEDIA_SHARE_ID"
private const val MEDIA_BOOKMARK_ID = "MEDIA_BOOKMARK_ID"
private const val MEDIA_PODCAST_ID = "MEDIA_PODCAST_ID"
private const val MEDIA_ALBUM_ITEM = "MEDIA_ALBUM_ITEM"
private const val MEDIA_PLAYLIST_SONG_ITEM = "MEDIA_PLAYLIST_SONG_ITEM"
private const val MEDIA_PLAYLIST_ITEM = "MEDIA_PLAYLIST_ITEM"
private const val MEDIA_ARTIST_ITEM = "MEDIA_ARTIST_ITEM"
private const val MEDIA_ARTIST_SECTION = "MEDIA_ARTIST_SECTION"
private const val MEDIA_ALBUM_SONG_ITEM = "MEDIA_ALBUM_SONG_ITEM"
private const val MEDIA_SONG_STARRED_ITEM = "MEDIA_SONG_STARRED_ITEM"
private const val MEDIA_SONG_RANDOM_ITEM = "MEDIA_SONG_RANDOM_ITEM"
private const val MEDIA_SHARE_ITEM = "MEDIA_SHARE_ITEM"
private const val MEDIA_SHARE_SONG_ITEM = "MEDIA_SHARE_SONG_ITEM"
private const val MEDIA_BOOKMARK_ITEM = "MEDIA_BOOKMARK_ITEM"
private const val MEDIA_PODCAST_ITEM = "MEDIA_PODCAST_ITEM"
private const val MEDIA_PODCAST_EPISODE_ITEM = "MEDIA_PODCAST_EPISODE_ITEM"
private const val MEDIA_SEARCH_SONG_ITEM = "MEDIA_SEARCH_SONG_ITEM"

// Currently the display limit for long lists is 100 items
private const val DISPLAY_LIMIT = 100
private const val SEARCH_LIMIT = 10

// List of available custom SessionCommands
const val SESSION_CUSTOM_SET_RATING = "SESSION_CUSTOM_SET_RATING"
const val PLAY_COMMAND = "play "

/**
 * MediaBrowserService implementation for e.g. Android Auto
 */
@Suppress("TooManyFunctions", "LargeClass", "UnusedPrivateMember")
@SuppressLint("UnsafeOptInUsageError")
class AutoMediaBrowserCallback(val libraryService: MediaLibraryService) :
    MediaLibraryService.MediaLibrarySession.Callback, KoinComponent {

    private val mediaPlayerManager by inject<MediaPlayerManager>()
    private val activeServerProvider: ActiveServerProvider by inject()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private var playlistCache: List<Track>? = null
    private var starredSongsCache: List<Track>? = null
    private var randomSongsCache: List<Track>? = null
    private var searchSongsCache: List<Track>? = null

    private val musicService get() = MusicServiceFactory.getMusicService()
    private val isOffline get() = ActiveServerProvider.isOffline()
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
    override fun onGetLibraryRoot(
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
                    folderType = FOLDER_TYPE_MIXED,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                ),
                params
            )
        )
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        Timber.i("onConnect")
        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()

        /*
        * TODO: Currently we need to create a custom session command, see https://github.com/androidx/media/issues/107
        * When this issue is fixed we should be able to remove this method again
        */
        availableSessionCommands.add(SessionCommand(SESSION_CUSTOM_SET_RATING, Bundle()))

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Timber.i("onGetItem")

        val tracks = tracksFromMediaId(mediaId)
        val mediaItem = tracks?.firstOrNull()?.toMediaItem()
        // TODO:
        // Create LRU Cache of MediaItems, fill it in the other calls
        // and retrieve it here.

        if (mediaItem != null) {
            return Futures.immediateFuture(
                LibraryResult.ofItem(mediaItem, null)
            )
        } else {
            return Futures.immediateFuture(
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.i("onLoadChildren")
        return onLoadChildren(parentId)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Timber.i("onCustomCommand")
        var customCommandFuture: ListenableFuture<SessionResult>? = null

        when (customCommand.customAction) {
            SESSION_CUSTOM_SET_RATING -> {
                /*
                * It is currently not possible to edit a MediaItem after creation so the isRated value
                * is stored in the track.starred value
                * See https://github.com/androidx/media/issues/33
                */
                val track = mediaPlayerManager.currentMediaItem?.toTrack()
                if (track != null) {
                    customCommandFuture = onSetRating(
                        session,
                        controller,
                        HeartRating(!track.starred)
                    )
                    Futures.addCallback(
                        customCommandFuture,
                        object : FutureCallback<SessionResult> {
                            override fun onSuccess(result: SessionResult) {
                                track.starred = !track.starred
                                // This needs to be called on the main Thread
                                // TODO: This is a looping reference
                                libraryService.onUpdateNotification(session)
                            }

                            override fun onFailure(t: Throwable) {
                                Toast.makeText(
                                    mediaPlayerManager.context,
                                    "There was an error updating the rating",
                                    LENGTH_SHORT
                                ).show()
                            }
                        },
                        MainThreadExecutor()
                    )
                }
            }
            else -> {
                Timber.d(
                    "CustomCommand not recognized %s with extra %s",
                    customCommand.customAction,
                    customCommand.customExtras.toString()
                )
            }
        }
        if (customCommandFuture != null)
            return customCommandFuture
        return super.onCustomCommand(session, controller, customCommand, args)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        if (session.player.currentMediaItem != null)
            return onSetRating(
                session,
                controller,
                session.player.currentMediaItem!!.mediaId,
                rating
            )
        return super.onSetRating(session, controller, rating)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        return serviceScope.future {
            Timber.i(controller.packageName)
            // This function even though its declared in AutoMediaBrowserCallback.kt is
            // actually called every time we set the rating on an MediaItem.
            // To avoid an event loop it does not emit a RatingUpdate event,
            // but calls the Manager directly
            RatingManager.instance.submitRating(
                RatingUpdate(
                    id = mediaId,
                    rating = rating
                )
            )
            return@future SessionResult(RESULT_SUCCESS)
        }
    }

    /*
     * For some reason the LocalConfiguration of MediaItem are stripped somewhere in ExoPlayer,
     * and thereby customarily it is required to rebuild it..
     * See also: https://stackoverflow.com/questions/70096715/adding-mediaitem-when-using-the-media3-library-caused-an-error
     */

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<List<MediaItem>> {

        Timber.i("onAddMediaItems")

        if (mediaItems.isEmpty()) return Futures.immediateFuture(mediaItems)
        // Return early if its a search
        if (mediaItems[0].requestMetadata.searchQuery != null)
            return playFromSearch(mediaItems[0].requestMetadata.searchQuery!!)

        val updatedMediaItems: List<MediaItem> =
            mediaItems.mapNotNull { mediaItem ->
                if (mediaItem.requestMetadata.mediaUri != null)
                    mediaItem.buildUpon()
                        .setUri(mediaItem.requestMetadata.mediaUri)
                        .build()
                else
                    null
            }

        return if (updatedMediaItems.isNotEmpty()) {
            Futures.immediateFuture(updatedMediaItems)
        } else {
            // Android Auto devices still only use the MediaId to identify the selected Items
            // They also only select a single item at once
            onAddLegacyAutoItems(mediaItems)
        }
    }

    @Suppress("MagicNumber", "ComplexMethod")
    private fun onAddLegacyAutoItems(
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val mediaIdParts = mediaItems.first().mediaId.split('|')

        val tracks = when (mediaIdParts.first()) {
            MEDIA_PLAYLIST_ITEM -> playPlaylist(mediaIdParts[1], mediaIdParts[2])
            MEDIA_PLAYLIST_SONG_ITEM -> playPlaylistSong(
                mediaIdParts[1], mediaIdParts[2], mediaIdParts[3]
            )
            MEDIA_ALBUM_ITEM -> playAlbum(mediaIdParts[1], mediaIdParts[2])
            MEDIA_ALBUM_SONG_ITEM -> playAlbumSong(
                mediaIdParts[1], mediaIdParts[2], mediaIdParts[3]
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
                mediaIdParts[1], mediaIdParts[2]
            )
            MEDIA_SEARCH_SONG_ITEM -> playSearch(mediaIdParts[1])
            else -> null
        }

        if (tracks != null) {
            return Futures.immediateFuture(
                tracks.map { track -> track.toMediaItem() }
                    .toMutableList()
            )
        }

        // Fallback to the original list
        return Futures.immediateFuture(mediaItems)
    }

    @Suppress("ReturnCount", "ComplexMethod")
    fun onLoadChildren(
        parentId: String,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("AutoMediaBrowserService onLoadChildren called. ParentId: %s", parentId)

        val parentIdParts = parentId.split('|')

        when (parentIdParts.first()) {
            MEDIA_ROOT_ID -> return getRootItems()
            MEDIA_LIBRARY_ID -> return getLibrary()
            MEDIA_ARTIST_ID -> return getArtists()
            MEDIA_ARTIST_SECTION -> return getArtists(parentIdParts[1])
            MEDIA_ALBUM_ID -> return getAlbums(AlbumListType.SORTED_BY_NAME)
            MEDIA_ALBUM_PAGE_ID -> return getAlbums(
                AlbumListType.fromName(parentIdParts[1]), parentIdParts[2].toInt()
            )
            MEDIA_PLAYLIST_ID -> return getPlaylists()
            MEDIA_ALBUM_FREQUENT_ID -> return getAlbums(AlbumListType.FREQUENT)
            MEDIA_ALBUM_NEWEST_ID -> return getAlbums(AlbumListType.NEWEST)
            MEDIA_ALBUM_RECENT_ID -> return getAlbums(AlbumListType.RECENT)
            MEDIA_ALBUM_RANDOM_ID -> return getAlbums(AlbumListType.RANDOM)
            MEDIA_ALBUM_STARRED_ID -> return getAlbums(AlbumListType.STARRED)
            MEDIA_SONG_RANDOM_ID -> return getRandomSongs()
            MEDIA_SONG_STARRED_ID -> return getStarredSongs()
            MEDIA_SHARE_ID -> return getShares()
            MEDIA_BOOKMARK_ID -> return getBookmarks()
            MEDIA_PODCAST_ID -> return getPodcasts()
            MEDIA_PLAYLIST_ITEM -> return getPlaylist(parentIdParts[1], parentIdParts[2])
            MEDIA_ARTIST_ITEM -> return getAlbumsForArtist(
                parentIdParts[1], parentIdParts[2]
            )
            MEDIA_ALBUM_ITEM -> return getSongsForAlbum(parentIdParts[1], parentIdParts[2])
            MEDIA_SHARE_ITEM -> return getSongsForShare(parentIdParts[1])
            MEDIA_PODCAST_ITEM -> return getPodcastEpisodes(parentIdParts[1])
            else -> return Futures.immediateFuture(LibraryResult.ofItemList(listOf(), null))
        }
    }

    private fun playFromSearch(
        query: String,
    ): ListenableFuture<List<MediaItem>> {

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
                            .joinToString("|"),
                        FOLDER_TYPE_ALBUMS
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

                searchSongsCache = searchResult.songs
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
            val tracks = tracksFromMediaId(firstItem.mediaId)
            Timber.i("Found media id: %s", firstItem.mediaId)
            val result = tracks?.map { it.toMediaItem() }
            Timber.i("Result size: %d", result?.size ?: 0)
            return@future result ?: listOf()
        }
    }

    @Suppress("MagicNumber", "ComplexMethod")
    private fun tracksFromMediaId(mediaId: String?): List<Track>? {
        Timber.d(
            "AutoMediaBrowserService onPlayFromMediaIdRequested called. mediaId: %s",
            mediaId
        )

        if (mediaId == null) return null
        val mediaIdParts = mediaId.split('|')

        // TODO Media Artist item is missing!!!
        return when (mediaIdParts.first()) {
            MEDIA_PLAYLIST_ITEM -> playPlaylist(mediaIdParts[1], mediaIdParts[2])
            MEDIA_PLAYLIST_SONG_ITEM -> playPlaylistSong(
                mediaIdParts[1], mediaIdParts[2], mediaIdParts[3]
            )
            MEDIA_ALBUM_ITEM -> playAlbum(mediaIdParts[1], mediaIdParts[2])
            MEDIA_ALBUM_SONG_ITEM -> playAlbumSong(
                mediaIdParts[1], mediaIdParts[2], mediaIdParts[3]
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
                mediaIdParts[1], mediaIdParts[2]
            )
            MEDIA_SEARCH_SONG_ITEM -> playSearch(mediaIdParts[1])
            else -> {
                listOf()
            }
        }
    }

    private fun playSearch(id: String): List<Track>? {
        // If there is no cache, we can't play the selected song.
        if (searchSongsCache != null) {
            val song = searchSongsCache!!.firstOrNull { x -> x.id == id }
            if (song != null) return listOf(song)
        }
        return null
    }

    private fun getRootItems(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        if (!isOffline)
            mediaItems.add(
                R.string.music_library_label,
                MEDIA_LIBRARY_ID,
                null,
                folderType = FOLDER_TYPE_MIXED,
                icon = R.drawable.ic_library
            )

        mediaItems.add(
            R.string.main_artists_title,
            MEDIA_ARTIST_ID,
            null,
            folderType = FOLDER_TYPE_ARTISTS,
            icon = R.drawable.ic_artist
        )

        if (!isOffline)
            mediaItems.add(
                R.string.main_albums_title,
                MEDIA_ALBUM_ID,
                null,
                folderType = FOLDER_TYPE_ALBUMS,
                icon = R.drawable.ic_menu_browse
            )

        mediaItems.add(
            R.string.playlist_label,
            MEDIA_PLAYLIST_ID,
            null,
            folderType = FOLDER_TYPE_PLAYLISTS,
            icon = R.drawable.ic_menu_playlists
        )

        return Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, null))
    }

    private fun getLibrary(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        // Songs
        mediaItems.add(
            R.string.main_songs_random,
            MEDIA_SONG_RANDOM_ID,
            R.string.main_songs_title,
            folderType = FOLDER_TYPE_TITLES
        )

        mediaItems.add(
            R.string.main_songs_starred,
            MEDIA_SONG_STARRED_ID,
            R.string.main_songs_title,
            folderType = FOLDER_TYPE_TITLES
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
            folderType = FOLDER_TYPE_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_frequent,
            MEDIA_ALBUM_FREQUENT_ID,
            R.string.main_albums_title,
            folderType = FOLDER_TYPE_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_random,
            MEDIA_ALBUM_RANDOM_ID,
            R.string.main_albums_title,
            folderType = FOLDER_TYPE_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_starred,
            MEDIA_ALBUM_STARRED_ID,
            R.string.main_albums_title,
            folderType = FOLDER_TYPE_ALBUMS
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
                if (section != null)
                    artists = artists.filter { artist ->
                        getSectionFromName(artist.name ?: "") == section
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
                                listOf(MEDIA_ARTIST_SECTION, currentSection).joinToString("|"),
                                FOLDER_TYPE_ARTISTS
                            )
                        }
                    }
                } else {
                    artists.map { artist ->
                        mediaItems.add(
                            artist.name ?: "",
                            listOf(childMediaId, artist.id, artist.name).joinToString("|"),
                            FOLDER_TYPE_ARTISTS
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
                        .joinToString("|"),
                    FOLDER_TYPE_ALBUMS
                )
            }
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getSongsForAlbum(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future { listSongsInMusicService(id, name) }.await()

            if (songs != null) {
                if (songs.getChildren(includeDirs = true, includeFiles = false).isEmpty() &&
                    songs.getChildren(includeDirs = false, includeFiles = true).isNotEmpty()
                )
                    mediaItems.addPlayAllItem(listOf(MEDIA_ALBUM_ITEM, id, name).joinToString("|"))

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getTracks().take(DISPLAY_LIMIT)
                items.map { item ->
                    if (item.isDirectory)
                        mediaItems.add(
                            item.title ?: "",
                            listOf(MEDIA_ALBUM_ITEM, item.id, item.name).joinToString("|"),
                            FOLDER_TYPE_TITLES
                        )
                    else
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
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getAlbums(
        type: AlbumListType,
        page: Int? = null
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val offset = (page ?: 0) * DISPLAY_LIMIT

            val albums = serviceScope.future {
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    callWithErrorHandling {
                        musicService.getAlbumList2(
                            type, DISPLAY_LIMIT, offset, null
                        )
                    }
                } else {
                    callWithErrorHandling {
                        musicService.getAlbumList(
                            type, DISPLAY_LIMIT, offset, null
                        )
                    }
                }
            }.await()

            albums?.map { album ->
                mediaItems.add(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                        .joinToString("|"),
                    FOLDER_TYPE_ALBUMS
                )
            }

            if ((albums?.size ?: 0) >= DISPLAY_LIMIT)
                mediaItems.add(
                    R.string.search_more,
                    listOf(MEDIA_ALBUM_PAGE_ID, type.typeName, (page ?: 0) + 1).joinToString("|"),
                    null
                )

            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getPlaylists(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
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
                    FOLDER_TYPE_PLAYLISTS
                )
            }
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getPlaylist(
        id: String,
        name: String,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val content = serviceScope.future {
                callWithErrorHandling { musicService.getPlaylist(id, name) }
            }.await()

            if (content != null) {
                if (content.size > 1)
                    mediaItems.addPlayAllItem(
                        listOf(MEDIA_PLAYLIST_ITEM, id, name).joinToString("|")
                    )

                // Playlist should be cached as it may contain random elements
                playlistCache = content.getTracks()
                playlistCache!!.take(DISPLAY_LIMIT).map { item ->
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

    private fun playPlaylist(id: String, name: String): List<Track>? {
        if (playlistCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content =
                serviceScope.future {
                    callWithErrorHandling { musicService.getPlaylist(id, name) }
                }.get()
            playlistCache = content?.getTracks()
        }
        if (playlistCache != null) return playlistCache!!
        return null
    }

    private fun playPlaylistSong(id: String, name: String, songId: String): List<Track>? {
        if (playlistCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content = serviceScope.future {
                callWithErrorHandling { musicService.getPlaylist(id, name) }
            }.get()
            playlistCache = content?.getTracks()
        }
        val song = playlistCache?.firstOrNull { x -> x.id == songId }
        if (song != null) return listOf(song)
        return null
    }

    private fun playAlbum(id: String, name: String?): List<Track>? {
        val songs = listSongsInMusicService(id, name)
        if (songs != null) return songs.getTracks()
        return null
    }

    private fun playAlbumSong(id: String, name: String?, songId: String): List<Track>? {
        val songs = listSongsInMusicService(id, name)
        val song = songs?.getTracks()?.firstOrNull { x -> x.id == songId }
        if (song != null) return listOf(song)
        return null
    }

    private fun getPodcasts(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val podcasts = serviceScope.future {
                callWithErrorHandling { musicService.getPodcastsChannels(false) }
            }.await()

            podcasts?.map { podcast ->
                mediaItems.add(
                    podcast.title ?: "",
                    listOf(MEDIA_PODCAST_ITEM, podcast.id).joinToString("|"),
                    FOLDER_TYPE_MIXED
                )
            }
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getPodcastEpisodes(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()
        return mainScope.future {
            val episodes = serviceScope.future {
                callWithErrorHandling { musicService.getPodcastEpisodes(id) }
            }.await()

            if (episodes != null) {
                if (episodes.getTracks().count() > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_PODCAST_ITEM, id).joinToString("|"))

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

    private fun playPodcast(id: String): List<Track>? {
        val episodes = serviceScope.future {
            callWithErrorHandling { musicService.getPodcastEpisodes(id) }
        }.get()
        if (episodes != null) {
            return episodes.getTracks()
        }
        return null
    }

    private fun playPodcastEpisode(id: String, episodeId: String): List<Track>? {
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

    private fun getBookmarks(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
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

    private fun playBookmark(id: String): List<Track>? {
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

    private fun getShares(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
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
                    FOLDER_TYPE_MIXED
                )
            }
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getSongsForShare(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val shares = serviceScope.future {
                callWithErrorHandling { musicService.getShares(false) }
            }.await()

            val selectedShare = shares?.firstOrNull { share -> share.id == id }
            if (selectedShare != null) {

                if (selectedShare.getEntries().count() > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_SHARE_ITEM, id).joinToString("|"))

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

    private fun playShare(id: String): List<Track>? {
        val shares = serviceScope.future {
            callWithErrorHandling { musicService.getShares(false) }
        }.get()
        val selectedShare = shares?.firstOrNull { share -> share.id == id }
        if (selectedShare != null) {
            return selectedShare.getEntries()
        }
        return null
    }

    private fun playShareSong(id: String, songId: String): List<Track>? {
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

    private fun getStarredSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                listStarredSongsInMusicService()
            }.await()

            if (songs != null) {
                if (songs.songs.count() > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_STARRED_ID).joinToString("|"))

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.songs.take(DISPLAY_LIMIT)
                starredSongsCache = items
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

    private fun playStarredSongs(): List<Track>? {
        if (starredSongsCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content = listStarredSongsInMusicService()
            starredSongsCache = content?.songs
        }
        if (starredSongsCache != null) return starredSongsCache!!
        return null
    }

    private fun playStarredSong(songId: String): List<Track>? {
        if (starredSongsCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            val content = listStarredSongsInMusicService()
            starredSongsCache = content?.songs
        }
        val song = starredSongsCache?.firstOrNull { x -> x.id == songId }
        if (song != null) return listOf(song)
        return null
    }

    private fun getRandomSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                callWithErrorHandling { musicService.getRandomSongs(DISPLAY_LIMIT) }
            }.await()

            if (songs != null) {
                if (songs.size > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_RANDOM_ID).joinToString("|"))

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getTracks()
                randomSongsCache = items
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

    private fun playRandomSongs(): List<Track>? {
        if (randomSongsCache == null) {
            // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
            // In this case we request a new set of random songs
            val content = serviceScope.future {
                callWithErrorHandling { musicService.getRandomSongs(DISPLAY_LIMIT) }
            }.get()
            randomSongsCache = content?.getTracks()
        }
        if (randomSongsCache != null) return randomSongsCache!!
        return null
    }

    private fun playRandomSong(songId: String): List<Track>? {
        // If there is no cache, we can't play the selected song.
        if (randomSongsCache != null) {
            val song = randomSongsCache!!.firstOrNull { x -> x.id == songId }
            if (song != null) return listOf(song)
        }
        return null
    }

    private fun listSongsInMusicService(id: String, name: String?): MusicDirectory? {
        return serviceScope.future {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                callWithErrorHandling { musicService.getAlbumAsDir(id, name, false) }
            } else {
                callWithErrorHandling { musicService.getMusicDirectory(id, name, false) }
            }
        }.get()
    }

    private fun listStarredSongsInMusicService(): SearchResult? {
        return serviceScope.future {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                callWithErrorHandling { musicService.getStarred2() }
            } else {
                callWithErrorHandling { musicService.getStarred() }
            }
        }.get()
    }

    private fun MutableList<MediaItem>.add(
        title: String,
        mediaId: String,
        folderType: Int
    ) {

        val mediaItem = buildMediaItem(
            title,
            mediaId,
            isPlayable = false,
            folderType = folderType
        )

        this.add(mediaItem)
    }

    @Suppress("LongParameterList")
    private fun MutableList<MediaItem>.add(
        resId: Int,
        mediaId: String,
        groupNameId: Int?,
        browsable: Boolean = true,
        folderType: Int = FOLDER_TYPE_MIXED,
        icon: Int? = null
    ) {
        val applicationContext = UApp.applicationContext()

        val mediaItem = buildMediaItem(
            applicationContext.getString(resId),
            mediaId,
            isPlayable = !browsable,
            folderType = folderType,
            group = if (groupNameId != null) {
                applicationContext.getString(groupNameId)
            } else null,
            imageUri = if (icon != null) {
                Util.getUriToDrawable(applicationContext, icon)
            } else null
        )

        this.add(mediaItem)
    }

    private fun MutableList<MediaItem>.addPlayAllItem(
        mediaId: String,
    ) {
        this.add(
            R.string.select_album_play_all,
            mediaId,
            null,
            false,
            icon = R.drawable.media_start
        )
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().uppercaseChar()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }

    private fun <T> callWithErrorHandling(function: () -> T): T? {
        // TODO Implement better error handling
        return try {
            function()
        } catch (all: Exception) {
            Timber.i(all)
            null
        }
    }
}
