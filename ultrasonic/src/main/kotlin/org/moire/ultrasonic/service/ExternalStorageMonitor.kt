/*
 * ExternalStorageMonitor.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import timber.log.Timber

/**
 * Monitors the state of the mobile's external storage
 */
class ExternalStorageMonitor {
    private var ejectEventReceiver: BroadcastReceiver? = null
    var isExternalStorageAvailable = true
        private set

    fun onCreate(ejectedCallback: Runnable) {
        // Stop when SD card is ejected.
        ejectEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isExternalStorageAvailable = Intent.ACTION_MEDIA_MOUNTED == intent.action
                if (!isExternalStorageAvailable) {
                    Timber.i("External media is ejecting. Stopping playback.")
                    ejectedCallback.run()
                } else {
                    Timber.i("External media is available.")
                }
            }
        }
        val ejectFilter = IntentFilter(Intent.ACTION_MEDIA_EJECT)
        ejectFilter.addAction(Intent.ACTION_MEDIA_MOUNTED)
        ejectFilter.addDataScheme("file")
        applicationContext().registerReceiver(ejectEventReceiver, ejectFilter)
    }

    fun onDestroy() {
        // avoid race conditions
        try {
            applicationContext().unregisterReceiver(ejectEventReceiver)
        } catch (ignored: Exception) {
        }
    }
}
