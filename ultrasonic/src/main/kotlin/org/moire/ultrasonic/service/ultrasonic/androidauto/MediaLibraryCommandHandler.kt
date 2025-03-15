package org.moire.ultrasonic.service.ultrasonic.androidauto

import android.os.Build
import android.os.Bundle
import androidx.car.app.connection.CarConnection
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.service.PlaybackService
import timber.log.Timber

/**
 * @class MediaLibraryCommandHandler
 * @brief Manages media playback commands and custom UI controls.
 *
 * This class is responsible for handling playback-related commands such as play, pause, shuffle, and repeat.
 * It also manages custom command buttons in the Android Auto media session, including the heart (favorite) button
 * and repeat mode settings.
 *
 * @details
 * - Builds and updates media player buttons dynamically based on playback state.
 * - Observes car connection state changes to adjust repeat modes accordingly.
 * - Provides helper methods for constructing custom command buttons.
 * - Supports playback state modifications such as shuffle and repeat toggling.
 *
 * @note This class interacts with `MediaSession` and `PlaybackService` to control media playback.
 *
 */
class MediaLibraryCommandHandler {
    private val placeholderButton = getPlaceholderButton()

    var heartIsCurrentlyOn = false
    public var customRepeatModeSet = false

    lateinit var allCustomCommands: List<CommandButton>

    lateinit var defaultCustomCommands: List<CommandButton>

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

    private lateinit var shuffleButton: CommandButton

    private lateinit var repeatOffButton: CommandButton
    private lateinit var repeatOneButton: CommandButton
    private lateinit var repeatAllButton: CommandButton

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



    fun initialize() {
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


    fun configureRepeatMode(player: Player) {
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

    fun shuffleCurrentPlaylist(player: Player) {
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

    public fun MediaSession.updateCustomCommands() {
        setCustomLayout(
            buildCustomCommands(
                heartIsCurrentlyOn,
                canShuffle()
            )
        )
    }
}