/*
 * UltrasonicIntentReceiver.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import timber.log.Timber

class UltrasonicIntentReceiver : BroadcastReceiver() {
    private val lifecycleSupport = inject<MediaPlayerLifecycleSupport>(
        MediaPlayerLifecycleSupport::class.java
    )

    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action
        Timber.i("Received Ultrasonic Intent: %s", intentAction)
        try {
            lifecycleSupport.value.receiveIntent(intent)
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
        } catch (_: Exception) {
            // Ignored.
        }
    }
}
