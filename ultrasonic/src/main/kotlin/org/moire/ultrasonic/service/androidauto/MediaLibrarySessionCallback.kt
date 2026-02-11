/*
 * MediaLibrarySessionCallback.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service.androidauto

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.PlaybackService
import org.moire.ultrasonic.service.PlaybackStateSerializer
import org.moire.ultrasonic.service.RatingManager
import org.moire.ultrasonic.service.toMediaItemsWithStartPosition
import org.moire.ultrasonic.util.toMediaItem
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

/**
 * @class MediaLibrarySessionCallback
 * @brief Handles media session interactions for Android Auto.
 *
 * This class manages communication between the media library and the playback session, handling
 * custom commands, media browsing requests, and playback state updates for Android Auto.
 *
 * @details
 * - Implements `MediaLibraryService.MediaLibrarySession.Callback` for media session events.
 * - Manages playback state resumption and custom command handling.
 * - Delegates media browsing to `MediaLibraryBrowser`.
 * - Interfaces with `MediaLibraryCommandHandler` for playback controls.
 * - Uses coroutines to handle asynchronous operations efficiently.
 *
 * @note This class interacts with `MediaLibraryDataProvider`, `MediaLibraryPlaybackController`,
 * and `MediaLibraryBrowser` to provide a seamless media experience.
 *
 */
@OptIn(UnstableApi::class)
class MediaLibrarySessionCallback :
    MediaLibraryBase(),
    MediaLibraryService.MediaLibrarySession.Callback,
    KoinComponent {

    private val commandHandler: MediaLibraryCommandHandler = MediaLibraryCommandHandler()
    private val searchCache = mutableMapOf<Pair<String, String>, List<MediaItem>>() // (packageName, query) -> items

    private val playbackStateSerializer: PlaybackStateSerializer by inject()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val mainScope = CoroutineScope(Dispatchers.Main)


    private val musicService get() = MusicServiceFactory.getMusicService()
    private var dataProvider: MediaLibraryDataProvider =
        MediaLibraryDataProvider(serviceScope = serviceScope, musicService = musicService)
    private var playbackController: MediaLibraryPlaybackController =
        MediaLibraryPlaybackController(serviceScope = serviceScope, musicService = musicService)
    private var mediaLibrarBrowser: MediaLibraryBrowser = MediaLibraryBrowser(
        mainScope = mainScope,
        serviceScope = serviceScope,
        musicService = musicService
    )

    init {

        playbackController.dataProvider = dataProvider
        dataProvider.playbackController = playbackController
        mediaLibrarBrowser.dataProvider = dataProvider
        commandHandler.initialize()
    }


    @OptIn(UnstableApi::class)
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

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (controller.controllerVersion != 0) {
            // Let Media3 controller (for instance the MediaNotificationProvider)
            // know about the custom layout right after it connected.
            with(session) {
                setCustomLayout(
                    commandHandler.buildCustomCommands(
                        session,
                        commandHandler.canShuffleWrapper(session)
                    )
                )
            }
        }
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
                commandHandler.updateCustomHeartButton(session, isHeart = true)
            }

            PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF -> {
                customCommandFuture = onSetRating(session, controller, HeartRating(false))
                commandHandler.updateCustomHeartButton(session, isHeart = false)
            }

            PlaybackService.CUSTOM_COMMAND_SHUFFLE -> {
                customCommandFuture = Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
                commandHandler.shuffleCurrentPlaylist(session.player)
            }

            PlaybackService.CUSTOM_COMMAND_REPEAT_MODE -> {
                customCommandFuture = Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
                commandHandler.customRepeatModeSet = true

                session.player.setNextRepeatMode()
                commandHandler.updateCustomCommandsWrapper(session)
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
        Timber.d("onSetRating")
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
        Timber.d("onSetRating")
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
        Timber.d("onAddMediaItems")

        if (mediaItems.isEmpty()) return Futures.immediateFuture(mediaItems)
        // Return early if its a search
        if (mediaItems[0].requestMetadata.searchQuery != null) {
            return playbackController.playFromSearch(mediaItems[0].requestMetadata.searchQuery!!)
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
            playbackController.onAddLegacyAutoItems(mediaItems)
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return this.mediaLibrarBrowser.getLibraryRoot(
            session, browser, params
        )
    }

    @OptIn(UnstableApi::class)
    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return mediaLibrarBrowser.getChildren(session, browser, parentId, page, pageSize, params)
    }

    @OptIn(UnstableApi::class)
    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return mediaLibrarBrowser.getItem(mediaId)
    }


    @OptIn(UnstableApi::class)
    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        Timber.d("onSearch query=%s from=%s", query, browser.packageName)

        return serviceScope.future {
            try {
                val items = getSearchItems(query)
                searchCache[browser.packageName to query] = items

                session.notifySearchResultChanged(browser, query, items.size, params)

                LibraryResult.ofVoid()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Error during search")
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("onGetSearchResult query=%s page=%d size=%d", query, page, pageSize)

        return serviceScope.future {
            val all = searchCache[browser.packageName to query] ?: getSearchItems(query)
            val from = if (pageSize > 0) (page * pageSize).coerceAtMost(all.size) else 0
            val to = if (pageSize > 0) (from + pageSize).coerceAtMost(all.size) else all.size
            val pageItems = if (from < to) all.subList(from, to) else emptyList()

            Timber.d("onGetSearchResult: total=%d from=%d to=%d returning=%d",
                all.size, from, to, pageItems.size)
            pageItems.forEachIndexed { i, item ->
                Timber.d("result[%d]: id=%s title=%s browsable=%s playable=%s mediaType=%s",
                    i,
                    item.mediaId,
                    item.mediaMetadata.title,
                    item.mediaMetadata.isBrowsable,
                    item.mediaMetadata.isPlayable,
                    item.mediaMetadata.mediaType
                )
            }

            LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), /*params=*/null)
        }
    }

    private fun getSearchItems(query: String): List<MediaItem> {
        Timber.d("getSearchItems: %s", query)
        val mediaItems = mutableListOf<MediaItem>()

        val searchResult = callWithErrorHandling {
            musicService.search(SearchCriteria(query, /*artists*/10, /*albums*/10, /*songs*/10))
        }

        if (searchResult != null) {
            // Artists (browsable folders)
            searchResult.artists.forEach { artist ->
                mediaItems.add(
                    buildFolderItem(
                        title = artist.name ?: "",
                        mediaId = listOf(MEDIA_ARTIST_ITEM, artist.id, artist.name).joinToString("|"),
                        folderMixed = true
                    )
                )
            }

            // Albums (browsable or playable depending on je model)
            searchResult.albums.forEach { album ->
                mediaItems.add(
                    buildFolderItem(
                        title = album.title ?: (album.name ?: ""),
                        mediaId = listOf(MEDIA_ALBUM_ITEM, album.id, album.name ?: album.title ?: "").joinToString("|"),
                        folderMixed = false
                    )
                )
            }

            // Songs (playable)
            dataProvider.searchSongsCache = searchResult.songs
            searchResult.songs.map { song ->
                mediaItems.add(
                    song.toMediaItem(
                        listOf(MEDIA_SEARCH_SONG_ITEM, song.id).joinToString("|")
                    )
                )
            }
        }

        return mediaItems
    }

    private fun buildFolderItem(
        title: String,
        mediaId: String,
        folderMixed: Boolean
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false) // <- belangrijk voor Android Auto
            .setMediaType(
                if (folderMixed) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
            )
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            // .setUri(null as Uri?)  // <- weglaten; onnodig en soms problematisch
            .build()
    }

    fun getCommandHelper(): MediaLibraryCommandHandler {
        return commandHandler
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        Timber.i("onConnect")

        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()

        for (commandButton in commandHandler.allCustomCommands) {
            // Add custom command to available session commands.
            commandButton.sessionCommand?.let { availableSessionCommands.add(it) }
        }

        commandHandler.configureRepeatMode(session.player)

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }
}
