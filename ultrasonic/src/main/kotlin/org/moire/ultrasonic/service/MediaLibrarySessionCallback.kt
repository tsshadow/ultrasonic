/*
 * MediaLibrarySessionCallback.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Build
import android.os.Bundle
import androidx.car.app.connection.CarConnection
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PLAYLIST
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.ifNotNull
import org.moire.ultrasonic.util.buildMediaItem
import org.moire.ultrasonic.util.toMediaItem
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber
import java.util.Calendar

private const val MEDIA_ROOT_ID = "MEDIA_ROOT_ID"
private const val MEDIA_ALBUM_ID = "MEDIA_ALBUM_ID"
private const val MEDIA_ALBUM_PAGE_ID = "MEDIA_ALBUM_PAGE_ID"
private const val MEDIA_ALBUM_NEWEST_ID = "MEDIA_ALBUM_NEWEST_ID"
private const val MEDIA_ALBUM_RECENT_ID = "MEDIA_ALBUM_RECENT_ID"
private const val MEDIA_ALBUM_FREQUENT_ID = "MEDIA_ALBUM_FREQUENT_ID"
private const val MEDIA_ALBUM_RANDOM_ID = "MEDIA_ALBUM_RANDOM_ID"
private const val MEDIA_ALBUM_STARRED_ID = "MEDIA_ALBUM_STARRED_ID"
private const val MEDIA_SONG_RANDOM_ID = "MEDIA_SONG_RANDOM_ID"
private const val MEDIA_SONG_RECENT = "MEDIA_SONG_RECENT"
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

// Genres -> songs
private const val MEDIA_GENRES_SONGS = "MEDIA_GENRES_SONGS"
private const val MEDIA_GENRE_SONGS = "MEDIA_GENRE_SONGS"
private const val MEDIA_GENRES_SONGS_LAST_YEAR = "MEDIA_GENRES_SONGS_LAST_YEAR"
private const val MEDIA_GENRE_SONGS_LAST_YEAR = "MEDIA_GENRE_SONGS_LAST_YEAR"
// Genres -> Livesets
private const val MEDIA_GENRES_LIVESETS = "MEDIA_GENRES_LIVESETS"
private const val MEDIA_GENRE_LIVESETS = "MEDIA_GENRE_LIVESETS"
private const val MEDIA_GENRES_LIVESETS_LAST_YEAR = "MEDIA_GENRES_LIVESETS_LAST_YEAR"
private const val MEDIA_GENRE_LIVESETS_LAST_YEAR = "MEDIA_GENRE_LIVESETS_LAST_YEAR"

// Moods -> songs
//private const val MEDIA_MOODS_SONGS = "MEDIA_MOODS_SONGS"
//private const val MEDIA_MOOD_SONGS = "MEDIA_MOOD_SONGS"
//private const val MEDIA_MOODS_SONGS_LAST_YEAR = "MEDIA_MOODS_SONGS_LAST_YEAR"
//private const val MEDIA_MOOD_SONGS_LAST_YEAR = "MEDIA_MOOD_SONGS_LAST_YEAR"
//// Moods -> Livesets
//private const val MEDIA_MOODS_LIVESETS = "MEDIA_MOODS_LIVESETS"
//private const val MEDIA_MOOD_LIVESETS = "MEDIA_MOOD_LIVESETS"
//private const val MEDIA_MOODS_LIVESETS_LAST_YEAR = "MEDIA_MOODS_LIVESETS_LAST_YEAR"
//private const val MEDIA_MOOD_LIVESETS_LAST_YEAR = "MEDIA_MOOD_LIVESETS_LAST_YEAR"

// Currently the display limit for long lists is 100 items
private const val DISPLAY_LIMIT = 100
private const val SEARCH_LIMIT = 10

// List of available custom SessionCommands
const val PLAY_COMMAND = "play "

/**
 * MediaBrowserService implementation for e.g. Android Auto
 */
@Suppress("TooManyFunctions", "LargeClass", "UnusedPrivateMember")
class MediaLibrarySessionCallback :
    MediaLibraryService.MediaLibrarySession.Callback,
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()
    private val playbackStateSerializer: PlaybackStateSerializer by inject()

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

    private val placeholderButton = getPlaceholderButton()

    private var heartIsCurrentlyOn = false
    private var customRepeatModeSet = false

    // This button is used for an unstarred track, and its action will star the track
    private val heartButtonToggleOn =
        getHeartCommandButton(
            SessionCommand(
                PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON,
                Bundle.EMPTY
            ),
            willHeart = true
        )

    // This button is used for an starred track, and its action will star the track
    private val heartButtonToggleOff =
        getHeartCommandButton(
            SessionCommand(
                PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF,
                Bundle.EMPTY
            ),
            willHeart = false
        )

    private val shuffleButton: CommandButton

    private val repeatOffButton: CommandButton
    private val repeatOneButton: CommandButton
    private val repeatAllButton: CommandButton

    private val allCustomCommands: List<CommandButton>

    val defaultCustomCommands: List<CommandButton>

    init {
        val shuffleCommand = SessionCommand(PlaybackService.CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY)
        shuffleButton = getShuffleCommandButton(shuffleCommand)

        val repeatCommand = SessionCommand(PlaybackService.CUSTOM_COMMAND_REPEAT_MODE, Bundle.EMPTY)
        repeatOffButton = getRepeatModeButton(repeatCommand, Player.REPEAT_MODE_OFF)
        repeatOneButton = getRepeatModeButton(repeatCommand, Player.REPEAT_MODE_ONE)
        repeatAllButton = getRepeatModeButton(repeatCommand, Player.REPEAT_MODE_ALL)

        allCustomCommands = listOf(
            heartButtonToggleOn,
            heartButtonToggleOff,
            shuffleButton,
            repeatOffButton,
            repeatOneButton,
            repeatAllButton
        )

        defaultCustomCommands = listOf(heartButtonToggleOn, shuffleButton, repeatOffButton)
    }

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
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_FOLDER_MIXED
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

        for (commandButton in allCustomCommands) {
            // Add custom command to available session commands.
            commandButton.sessionCommand?.let { availableSessionCommands.add(it) }
        }

        configureRepeatMode(session.player)

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch {
            val state = playbackStateSerializer.deserializeNow()
            if (state != null) {
                result.set(state.toMediaItemsWithStartPosition())
                withContext(Dispatchers.Main) {
                    mediaSession.player.shuffleModeEnabled = state.shufflePlay
                    mediaSession.player.repeatMode = state.repeatMode
                }
            }
        }
        return result
    }

    private fun configureRepeatMode(player: Player) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Timber.d("Car app library available, observing CarConnection")

            val originalRepeatMode = player.repeatMode

            var lastCarConnectionType = -1

            CarConnection(UApp.applicationContext()).type.observeForever {
                if (lastCarConnectionType == it) {
                    return@observeForever
                }

                lastCarConnectionType = it

                Timber.d("CarConnection type changed to %s", it)

                when (it) {
                    CarConnection.CONNECTION_TYPE_PROJECTION ->
                        if (!customRepeatModeSet) {
                            Timber.d("[CarConnection] Setting repeat mode to ALL")
                            player.repeatMode = Player.REPEAT_MODE_ALL
                            customRepeatModeSet = true
                        }

                    CarConnection.CONNECTION_TYPE_NOT_CONNECTED ->
                        if (customRepeatModeSet) {
                            Timber.d("[CarConnection] Resetting repeat mode")
                            player.repeatMode = originalRepeatMode
                            customRepeatModeSet = false
                        }
                }
            }
        } else {
            Timber.d("Car app library not available")
        }
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (controller.controllerVersion != 0) {
            // Let Media3 controller (for instance the MediaNotificationProvider)
            // know about the custom layout right after it connected.
            with(session) {
                setCustomLayout(session.buildCustomCommands(canShuffle = canShuffle()))
            }
        }
    }

    private fun getHeartCommandButton(sessionCommand: SessionCommand, willHeart: Boolean) =
        CommandButton.Builder()
            .setDisplayName(
                if (willHeart) {
                    "Love"
                } else {
                    "Dislike"
                }
            )
            .setIconResId(
                if (willHeart) {
                    R.drawable.ic_star_hollow
                } else {
                    R.drawable.ic_star_full
                }
            )
            .setSessionCommand(sessionCommand)
            .setEnabled(true)
            .build()

    private fun getShuffleCommandButton(sessionCommand: SessionCommand) = CommandButton.Builder()
        .setDisplayName("Shuffle")
        .setIconResId(R.drawable.media_shuffle)
        .setSessionCommand(sessionCommand)
        .setEnabled(true)
        .build()

    private fun getPlaceholderButton() = CommandButton.Builder()
        .setDisplayName("Placeholder")
        .setIconResId(android.R.color.transparent)
        .setSessionCommand(
            SessionCommand(
                PlaybackService.CUSTOM_COMMAND_PLACEHOLDER,
                Bundle.EMPTY
            )
        )
        .setEnabled(false)
        .build()

    private fun getRepeatModeButton(sessionCommand: SessionCommand, repeatMode: Int) =
        CommandButton.Builder()
            .setDisplayName(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat One"
                    Player.REPEAT_MODE_ALL -> "Repeat All"
                    else -> "Repeat None"
                }
            )
            .setIconResId(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.media_repeat_one
                    Player.REPEAT_MODE_ALL -> R.drawable.media_repeat_all
                    else -> R.drawable.media_repeat_off
                }
            )
            .setSessionCommand(sessionCommand)
            .setEnabled(true)
            .build()

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
        Timber.i("onCustomCommand %s", customCommand.customAction)
        var customCommandFuture: ListenableFuture<SessionResult>? = null

        when (customCommand.customAction) {
            PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON -> {
                customCommandFuture = onSetRating(session, controller, HeartRating(true))
                updateCustomHeartButton(session, isHeart = true)
            }

            PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF -> {
                customCommandFuture = onSetRating(session, controller, HeartRating(false))
                updateCustomHeartButton(session, isHeart = false)
            }

            PlaybackService.CUSTOM_COMMAND_SHUFFLE -> {
                customCommandFuture = Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
                shuffleCurrentPlaylist(session.player)
            }

            PlaybackService.CUSTOM_COMMAND_REPEAT_MODE -> {
                customCommandFuture = Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
                customRepeatModeSet = true

                session.player.setNextRepeatMode()
                session.updateCustomCommands()
            }

            else -> {
                Timber.d(
                    "CustomCommand not recognized %s with extra %s",
                    customCommand.customAction,
                    customCommand.customExtras.toString()
                )
            }
        }

        return customCommandFuture
            ?: super.onCustomCommand(
                session,
                controller,
                customCommand,
                args
            )
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        val mediaItem = session.player.currentMediaItem

        if (mediaItem != null) {
            if (rating is HeartRating) {
                mediaItem.toTrack().starred = rating.isHeart
            } else if (rating is StarRating) {
                mediaItem.toTrack().userRating = rating.starRating.toInt()
            }
            return onSetRating(
                session,
                controller,
                mediaItem.mediaId,
                rating
            )
        }

        return super.onSetRating(session, controller, rating)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        // TODO: Through this methods it is possible to set a rating on an arbitrary MediaItem.
        // Right now the ratings are submitted, yet the underlying track is only updated when
        // coming from the other onSetRating(session, controller, rating)
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
        if (mediaItems[0].requestMetadata.searchQuery != null) {
            return playFromSearch(mediaItems[0].requestMetadata.searchQuery!!)
        }

        val updatedMediaItems: List<MediaItem> =
            mediaItems.mapNotNull { mediaItem ->
                if (mediaItem.requestMetadata.mediaUri != null) {
                    mediaItem.buildUpon()
                        .setUri(mediaItem.requestMetadata.mediaUri)
                        .build()
                } else {
                    null
                }
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

    @Suppress("ReturnCount", "ComplexMethod")
    private fun onLoadChildren(
        parentId: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("AutoMediaBrowserService onLoadChildren called. ParentId: %s", parentId)
        val year = Calendar.getInstance().get(Calendar.YEAR)
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

            // Genre -> songs
            MEDIA_GENRES_SONGS -> getGenres(null, "short")
            MEDIA_GENRE_SONGS -> getGenre(parentIdParts[1], null, "short")
            MEDIA_GENRES_SONGS_LAST_YEAR -> getGenres(year, "short")
            MEDIA_GENRE_SONGS_LAST_YEAR -> getGenre(parentIdParts[1],year, "short")
            // Genre -> livesets
            MEDIA_GENRES_LIVESETS -> getGenres(null, "long")
            MEDIA_GENRE_LIVESETS -> getGenre(parentIdParts[1], null, "long")
            MEDIA_GENRES_LIVESETS_LAST_YEAR -> getGenres(year, "long")
            MEDIA_GENRE_LIVESETS_LAST_YEAR -> getGenre(parentIdParts[1],year, "long")

//            // Mood -> songs
//            MEDIA_MOODS_SONGS -> getMoods(null, "short")
//            MEDIA_MOOD_SONGS -> getMood(parentIdParts[1],null, "short")
//            MEDIA_MOODS_SONGS_LAST_YEAR -> getMoods(year, "short")
//            MEDIA_MOOD_SONGS_LAST_YEAR -> getMood(parentIdParts[1], year, "short")
//            // Mood -> livesets
//            MEDIA_MOODS_LIVESETS -> getMoods(null, "long")
//            MEDIA_MOOD_LIVESETS -> getMood(parentIdParts[1],null, "long")
//            MEDIA_MOODS_LIVESETS_LAST_YEAR -> getMoods(year, "long")
//            MEDIA_MOOD_LIVESETS_LAST_YEAR -> getMood(parentIdParts[1], year, "long")

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

    private fun playFromSearch(query: String): ListenableFuture<List<MediaItem>> {
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
            R.string.main_title_livesets_last_year,
            MEDIA_GENRES_LIVESETS_LAST_YEAR,
            R.string.main_genres_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )

//        // Moods
//        mediaItems.add(
//            R.string.main_title_all_songs,
//            MEDIA_MOODS_SONGS,
//            R.string.main_moods_title,
//            isBrowsable = true,
//            mediaType = MEDIA_TYPE_PLAYLIST
//        )
//        mediaItems.add(
//            R.string.main_title_songs_last_year,
//            MEDIA_MOODS_SONGS_LAST_YEAR,
//            R.string.main_moods_title,
//            isBrowsable = true,
//            mediaType = MEDIA_TYPE_PLAYLIST
//        )
//        mediaItems.add(
//            R.string.main_title_all_livesets,
//            MEDIA_MOODS_LIVESETS,
//            R.string.main_moods_title,
//            isBrowsable = true,
//            mediaType = MEDIA_TYPE_PLAYLIST
//        )
//        mediaItems.add(
//            R.string.main_title_livesets_last_year,
//            MEDIA_MOODS_LIVESETS_LAST_YEAR,
//            R.string.main_moods_title,
//            isBrowsable = true,
//            mediaType = MEDIA_TYPE_PLAYLIST
//        )

        // Songs
        mediaItems.add(
            R.string.main_songs_random,
            MEDIA_SONG_RANDOM_ID,
            R.string.main_songs_title,
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
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future { listSongsInMusicService(id, name) }.await()

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
                    mediaType = MEDIA_TYPE_FOLDER_MIXED
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
                    mediaType = MEDIA_TYPE_FOLDER_MIXED
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
                if (songs.songs.count() > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_STARRED_ID).joinToString("|"))
                }

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
                if (songs.size > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_RANDOM_ID).joinToString("|"))
                }

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

    private fun getRecentSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = serviceScope.future {
                callWithErrorHandling { musicService.getSongs(Filters(), null, null, DISPLAY_LIMIT, 0, "LastWritten") }
            }.await()

            if (songs != null) {
                if (songs.size > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_RANDOM_ID).joinToString("|"))
                }

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

    private fun getGenres(year: Int?, length: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
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
                    MEDIA_GENRE_SONGS_LAST_YEAR
                } else {
                    MEDIA_GENRE_LIVESETS_LAST_YEAR
                }
            }
            Timber.i("getGenres: mediaIdPrefix=$mediaIdPrefix $year")

            if (genres != null) {
                genres = genres.sortedByDescending { Genre -> Genre.songCount  }
            }

            genres?.forEach {
                mediaItems.add(
                    it.name + " " + it.songCount,
                    mediaIdPrefix + "|" + it.name,
                    R.string.main_genres_title,
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_PLAYLIST)

            }
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getGenre(genre: String, year: Int?, length: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        Timber.i("getGenre: genre=$genre year=$year length=$length")
        return mainScope.future {
            val songs = serviceScope.future {
                val filters = Filters(Filter("GENRE", genre))
                filters.add(Filter("LENGTH", length))
                year.ifNotNull { filters.add(Filter("YEAR", year.toString()))}
                callWithErrorHandling { musicService.getSongs(filters, null, null, 100000, 0, "LastWritten") }
            }.await()

            if (songs != null) {

                if (songs.size > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_RANDOM_ID).joinToString("|"))
                }

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
        mediaType: Int = MEDIA_TYPE_MIXED,
        isBrowsable: Boolean = false
    ) {
        val mediaItem = buildMediaItem(
            title,
            mediaId,
            isPlayable = false,
            isBrowsable = isBrowsable,
            mediaType = mediaType
        )

        this.add(mediaItem)
    }

    @Suppress("LongParameterList")
    private fun MutableList<MediaItem>.add(
        resId: Int,
        mediaId: String,
        groupNameId: Int?,
        isBrowsable: Boolean = true,
        mediaType: Int = MEDIA_TYPE_FOLDER_MIXED,
        icon: Int? = null
    ) {
        val applicationContext = UApp.applicationContext()

        val mediaItem = buildMediaItem(
            applicationContext.getString(resId),
            mediaId,
            isPlayable = !isBrowsable,
            isBrowsable = isBrowsable,
            imageUri = if (icon != null) {
                Util.getUriToDrawable(applicationContext, icon)
            } else {
                null
            },
            group = if (groupNameId != null) {
                applicationContext.getString(groupNameId)
            } else {
                null
            },
            mediaType = mediaType
        )

        this.add(mediaItem)
    }
    @Suppress("LongParameterList")
    private fun MutableList<MediaItem>.add(
        resId: String,
        mediaId: String,
        groupNameId: Int?,
        isBrowsable: Boolean = true,
        mediaType: Int = MEDIA_TYPE_FOLDER_MIXED,
        icon: Int? = null
    ){
        val applicationContext = UApp.applicationContext()

        val mediaItem = buildMediaItem(
            resId,
            mediaId,
            isPlayable = !isBrowsable,
            isBrowsable = isBrowsable,
            imageUri = if (icon != null) {
                Util.getUriToDrawable(applicationContext, icon)
            } else {
                null
            },
            group = if (groupNameId != null) {
                applicationContext.getString(groupNameId)
            } else {
                null
            },
            mediaType = mediaType
        )

        this.add(mediaItem)
    }

    private fun MutableList<MediaItem>.addPlayAllItem(mediaId: String) {
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

    private fun Player.setNextRepeatMode() {
        repeatMode =
            when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
    }

    private fun MediaSession.updateCustomCommands() {
        setCustomLayout(
            buildCustomCommands(
                heartIsCurrentlyOn,
                canShuffle()
            )
        )
    }

    fun updateCustomHeartButton(session: MediaSession, isHeart: Boolean) {
        with(session) {
            setCustomLayout(
                buildCustomCommands(
                    isHeart = isHeart,
                    canShuffle = canShuffle()
                )
            )
        }
    }

    private fun MediaSession.canShuffle() = player.mediaItemCount > 2

    private fun MediaSession.buildCustomCommands(
        isHeart: Boolean = false,
        canShuffle: Boolean = false
    ): ImmutableList<CommandButton> {
        Timber.d("building custom commands (isHeart = %s, canShuffle = %s)", isHeart, canShuffle)

        heartIsCurrentlyOn = isHeart

        return ImmutableList.copyOf(
            buildList {
                // placeholder must come first here because if there is no next button the first
                // custom command button is place right next to the play/pause button
                if (
                    player.repeatMode != Player.REPEAT_MODE_ALL &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1
                ) {
                    add(placeholderButton)
                }

                // due to the previous placeholder this heart button will always appear to the left
                // of the default playback items
                add(
                    if (isHeart) {
                        heartButtonToggleOff
                    } else {
                        heartButtonToggleOn
                    }
                )

                // both the shuffle and the active repeat mode button will end up in the overflow
                // menu if both are available at the same time
                if (canShuffle) {
                    add(shuffleButton)
                }

                add(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ONE -> repeatOneButton
                        Player.REPEAT_MODE_ALL -> repeatAllButton
                        else -> repeatOffButton
                    }
                )
            }.asIterable()
        )
    }

    private fun shuffleCurrentPlaylist(player: Player) {
        Timber.d("shuffleCurrentPlaylist")

        // 3 was chosen because that leaves at least two other songs to be shuffled around
        @Suppress("MagicNumber")
        if (player.mediaItemCount < 3) {
            return
        }

        val mediaItemsToShuffle = mutableListOf<MediaItem>()

        for (i in 0 until player.currentMediaItemIndex) {
            mediaItemsToShuffle += player.getMediaItemAt(i)
        }

        for (i in player.currentMediaItemIndex + 1 until player.mediaItemCount) {
            mediaItemsToShuffle += player.getMediaItemAt(i)
        }

        player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        player.removeMediaItems(0, player.currentMediaItemIndex)

        player.addMediaItems(mediaItemsToShuffle.shuffled())
    }
}
