/*
 * CustomNotificationProvider.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.playback

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.HeartRating
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.util.toTrack

@UnstableApi
class CustomNotificationProvider(ctx: Context) :
    DefaultMediaNotificationProvider(ctx),
    KoinComponent {

    /*
    * It is currently not possible to edit a MediaItem after creation so the isRated value
    * is stored in the track.starred value. See https://github.com/androidx/media/issues/33
    * TODO: Once the bug is fixed remove this circular reference!
    */
    private val mediaPlayerManager by inject<MediaPlayerManager>()

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        val tmp: MutableList<CommandButton> = mutableListOf()
        /*
        * TODO:
        * It is currently not possible to edit a MediaItem after creation so the isRated value
        * is stored in the track.starred value
        * See https://github.com/androidx/media/issues/33
        */
        val rating = mediaPlayerManager.currentMediaItem?.toTrack()?.starred?.let {
            HeartRating(
                it
            )
        }
        if (rating is HeartRating) {
            tmp.add(
                CommandButton.Builder()
                    .setDisplayName("Love")
                    .setIconResId(
                        if (rating.isHeart) R.drawable.ic_star_full
                        else R.drawable.ic_star_hollow
                    )
                    .setSessionCommand(
                        SessionCommand(
                            SESSION_CUSTOM_SET_RATING,
                            HeartRating(rating.isHeart).toBundle()
                        )
                    )
                    .setExtras(HeartRating(rating.isHeart).toBundle())
                    .setEnabled(true)
                    .build()
            )
        }
        return super.addNotificationActions(
            mediaSession,
            ImmutableList.copyOf((mediaButtons + tmp)),
            builder,
            actionFactory
        )
    }

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        playWhenReady: Boolean
    ): ImmutableList<CommandButton> {
        val commands = super.getMediaButtons(session, playerCommands, customLayout, playWhenReady)

        commands.forEachIndexed { index, command ->
            command.extras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index)
        }

        return commands
    }
}
