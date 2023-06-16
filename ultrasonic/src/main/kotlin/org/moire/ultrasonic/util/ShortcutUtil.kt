/*
 * ShortcutUtil.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity

object ShortcutUtil {
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun registerShortcuts(activity: Activity) {
        val shortcutIntent = Intent(activity, NavigationActivity::class.java).apply {
            action = Constants.INTENT_PLAY_RANDOM_SONGS
        }

        val shortcut = ShortcutInfo.Builder(activity, "shortcut_play_random_songs")
            .setShortLabel(activity.getString(R.string.shortcut_play_random_songs_short))
            .setLongLabel(activity.getString(R.string.shortcut_play_random_songs_long))
            .setIcon(Icon.createWithResource(activity, R.drawable.media_shuffle))
            .setIntent(shortcutIntent)
            .build()

        val shortcutManager = activity.getSystemService(ShortcutManager::class.java)
        shortcutManager?.dynamicShortcuts = listOf(shortcut)
    }
}
