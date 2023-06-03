/*
 * CustomNotificationProvider.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import org.koin.core.component.KoinComponent

@UnstableApi
class CustomNotificationProvider(ctx: Context) :
    DefaultMediaNotificationProvider(ctx),
    KoinComponent {

    // By default the skip buttons are not shown in compact view.
    // We add the COMMAND_KEY_COMPACT_VIEW_INDEX to show them
    // See also: https://github.com/androidx/media/issues/410
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
