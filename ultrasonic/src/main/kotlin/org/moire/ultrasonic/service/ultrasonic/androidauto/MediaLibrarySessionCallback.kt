/*
 * MediaLibrarySessionCallback.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service.ultrasonic.androidauto

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
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
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.PlaybackService
import org.moire.ultrasonic.service.PlaybackStateSerializer
import org.moire.ultrasonic.service.RatingManager
import org.moire.ultrasonic.service.toMediaItemsWithStartPosition
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
@Suppress("TooManyFunctions", "LargeClass", "UnusedPrivateMember")
class MediaLibrarySessionCallback :
    MediaLibraryBase(),
    MediaLibraryService.MediaLibrarySession.Callback,
    KoinComponent {

    private val commandHandler: MediaLibraryCommandHandler = MediaLibraryCommandHandler()

    private val playbackStateSerializer: PlaybackStateSerializer by inject()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val mainScope = CoroutineScope(Dispatchers.Main)


    private val musicService get() = MusicServiceFactory.getMusicService()
    private var dataProvider: MediaLibraryDataProvider =
        MediaLibraryDataProvider(serviceScope = serviceScope, musicService = musicService)
    private var playbackController: MediaLibraryPlaybackController =
        MediaLibraryPlaybackController(serviceScope = serviceScope, musicService = musicService)
    private var browser: MediaLibraryBrowser = MediaLibraryBrowser(
        mainScope = mainScope,
        serviceScope = serviceScope,
        musicService = musicService
    )

    init {

        playbackController.dataProvider = dataProvider
        dataProvider.playbackController = playbackController
        browser.dataProvider = dataProvider
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
//                setCustomLayout(session.buildCustomCommands(canShuffle = canShuffle()))
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
//                session.updateCustomCommands()
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
        return this.browser.getLibraryRoot(
            session, browser, params
        );
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
