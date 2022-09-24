/*
 * JukeboxNotificationActionFactory.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import org.moire.ultrasonic.app.UApp

/**
 * This class creates Intents and Actions to be used with the Media Notification
 * of the Jukebox Service
 */
@SuppressLint("UnsafeOptInUsageError")
class JukeboxNotificationActionFactory : MediaNotification.ActionFactory {
    override fun createMediaAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        command: Int
    ): NotificationCompat.Action {
        return NotificationCompat.Action(
            icon, title, createMediaActionPendingIntent(mediaSession, command.toLong())
        )
    }

    override fun createCustomAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        customAction: String,
        extras: Bundle
    ): NotificationCompat.Action {
        return NotificationCompat.Action(
            icon, title, null
        )
    }

    override fun createCustomActionFromCustomCommandButton(
        mediaSession: MediaSession,
        customCommandButton: CommandButton
    ): NotificationCompat.Action {
        return NotificationCompat.Action(null, null, null)
    }

    @Suppress("MagicNumber")
    override fun createMediaActionPendingIntent(
        mediaSession: MediaSession,
        command: Long
    ): PendingIntent {
        val keyCode: Int = toKeyCode(command)
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        intent.component = ComponentName(UApp.applicationContext(), JukeboxMediaPlayer::class.java)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return if (Util.SDK_INT >= 26 && command == Player.COMMAND_PLAY_PAUSE.toLong()) {
            return PendingIntent.getForegroundService(
                UApp.applicationContext(), keyCode, intent, PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                UApp.applicationContext(),
                keyCode,
                intent,
                if (Util.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            )
        }
    }

    private fun toKeyCode(action: @Player.Command Long): Int {
        return when (action.toInt()) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            Player.COMMAND_STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            Player.COMMAND_SEEK_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            Player.COMMAND_SEEK_BACK -> KeyEvent.KEYCODE_MEDIA_REWIND
            Player.COMMAND_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }
}
