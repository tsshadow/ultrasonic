package org.moire.ultrasonic.subsonic

import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.util.Util

/**
 * Utility class for checking the availability of the network and storage
 */
class NetworkAndStorageChecker {
    fun warnIfNetworkOrStorageUnavailable() {
        if (!Util.isExternalStoragePresent()) {
            Util.toast(R.string.select_album_no_sdcard, true, UApp.applicationContext())
        } else if (!isOffline() && !Util.hasUsableNetwork()) {
            Util.toast(R.string.select_album_no_network, true, UApp.applicationContext())
        }
    }
}
