/*
 * Constants.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.util

object Constants {
    // Character encoding used throughout.
    const val UTF_8 = "UTF-8"

    // REST protocol version and client ID.
    // Note: Keep it as low as possible to maintain compatibility with older servers.
    const val REST_PROTOCOL_VERSION = "1.7.0"
    const val REST_CLIENT_ID = "Ultrasonic"

    // Legacy names for intent extras, in those fragments which don't use SafeArgs yet.
    const val INTENT_ARTIST = "subsonic.artist"
    const val INTENT_TITLE = "subsonic.title"
    const val INTENT_AUTOPLAY = "subsonic.playall"
    const val INTENT_QUERY = "subsonic.query"
    const val INTENT_ALBUM_LIST_TYPE = "subsonic.albumlisttype"
    const val INTENT_SHOW_PLAYER = "subsonic.showplayer"

    // Names for Intent Actions
    const val CMD_PROCESS_KEYCODE = "org.moire.ultrasonic.CMD_PROCESS_KEYCODE"
    const val CMD_PLAY = "org.moire.ultrasonic.CMD_PLAY"
    const val CMD_RESUME_OR_PLAY = "org.moire.ultrasonic.CMD_RESUME_OR_PLAY"
    const val CMD_TOGGLEPAUSE = "org.moire.ultrasonic.CMD_TOGGLEPAUSE"
    const val CMD_PAUSE = "org.moire.ultrasonic.CMD_PAUSE"
    const val CMD_STOP = "org.moire.ultrasonic.CMD_STOP"
    const val CMD_PREVIOUS = "org.moire.ultrasonic.CMD_PREVIOUS"
    const val CMD_NEXT = "org.moire.ultrasonic.CMD_NEXT"

    // Legacy Preferences keys
    // Warning: Don't add any new here!
    // Use setting_keys.xml
    const val PREFERENCES_KEY_USE_FIVE_STAR_RATING = "use_five_star_rating"
    const val PREFERENCE_VALUE_ALL = 0
    const val PREFERENCE_VALUE_A2DP = 1
    const val PREFERENCE_VALUE_DISABLED = 2

    const val FILENAME_PLAYLIST_SER = "downloadstate.ser"
    const val ALBUM_ART_FILE = "folder.jpeg"
    const val RESULT_CLOSE_ALL = 1337
}
