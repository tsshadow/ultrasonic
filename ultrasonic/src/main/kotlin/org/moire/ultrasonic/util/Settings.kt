/*
 * Settings.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.regex.Pattern
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp

/**
 * Contains convenience functions for reading and writing preferences
 */
object Settings {
    private val PATTERN = Pattern.compile(":")

    @JvmStatic
    var theme by StringSetting(
        getKey(R.string.setting_key_theme),
        getKey(R.string.setting_key_theme_dark)
    )

    @JvmStatic
    val maxBitRate: Int
        get() {
            val network = Util.networkInfo()

            if (!network.connected) return 0

            if (network.unmetered) {
                return maxWifiBitRate
            } else {
                return maxMobileBitRate
            }
        }

    private var maxWifiBitRate
        by StringIntSetting(getKey(R.string.setting_key_max_bitrate_wifi))

    private var maxMobileBitRate
        by StringIntSetting(getKey(R.string.setting_key_max_bitrate_mobile))

    @JvmStatic
    val preloadCount: Int
        get() {
            val preferences = preferences
            val preloadCount =
                preferences.getString(getKey(R.string.setting_key_preload_count), "-1")!!
                    .toInt()
            return if (preloadCount == -1) Int.MAX_VALUE else preloadCount
        }

    val parallelDownloads by IntSetting(getKey(R.string.setting_key_parallel_downloads), 3)

    @JvmStatic
    val cacheSizeMB: Int
        get() {
            val preferences = preferences
            val cacheSize = preferences.getString(
                getKey(R.string.setting_key_cache_size),
                "-1"
            )!!.toInt()
            return if (cacheSize == -1) Int.MAX_VALUE else cacheSize
        }

    @JvmStatic
    var customCacheLocation by BooleanSetting(
        getKey(R.string.setting_key_custom_cache_location),
        false
    )

    @JvmStatic
    var cacheLocationUri by StringSetting(
        getKey(R.string.setting_key_cache_location), ""
    )

    @JvmStatic
    var isWifiRequiredForDownload by BooleanSetting(
        getKey(R.string.setting_key_wifi_required_for_download),
        false
    )

    @JvmStatic
    var shareOnServer by BooleanSetting(getKey(R.string.setting_key_share_on_server), true)

    @JvmStatic
    var shouldDisplayBitrateWithArtist by BooleanSetting(
        getKey(R.string.setting_key_display_bitrate_with_artist),
        true
    )

    @JvmStatic
    var shouldUseFolderForArtistName
        by BooleanSetting(getKey(R.string.setting_key_use_folder_for_album_artist), false)

    @JvmStatic
    var shouldShowTrackNumber
        by BooleanSetting(getKey(R.string.setting_key_show_track_number), false)

    @JvmStatic
    var defaultAlbums
        by StringIntSetting(getKey(R.string.setting_key_default_albums), "5")

    @JvmStatic
    var maxAlbums
        by StringIntSetting(getKey(R.string.setting_key_max_albums), "20")

    @JvmStatic
    var defaultSongs
        by StringIntSetting(getKey(R.string.setting_key_default_songs), "10")

    @JvmStatic
    var maxSongs
        by StringIntSetting(getKey(R.string.setting_key_max_songs), "25")

    @JvmStatic
    var maxArtists
        by StringIntSetting(getKey(R.string.setting_key_max_artists), "10")

    @JvmStatic
    var defaultArtists
        by StringIntSetting(getKey(R.string.setting_key_default_artists), "3")

    @JvmStatic
    var incrementTime
        by StringIntSetting(getKey(R.string.setting_key_increment_time), "5")

    @JvmStatic
    var mediaButtonsEnabled
        by BooleanSetting(getKey(R.string.setting_key_media_buttons), true)

    var resumePlayOnHeadphonePlug
        by BooleanSetting(R.string.setting_key_resume_play_on_headphones_plug, true)

    @JvmStatic
    var resumeOnBluetoothDevice by IntSetting(
        getKey(R.string.setting_key_resume_on_bluetooth_device),
        Constants.PREFERENCE_VALUE_DISABLED
    )

    @JvmStatic
    var pauseOnBluetoothDevice by IntSetting(
        getKey(R.string.setting_key_pause_on_bluetooth_device),
        Constants.PREFERENCE_VALUE_A2DP
    )

    @JvmStatic
    var showNowPlaying
        by BooleanSetting(getKey(R.string.setting_key_show_now_playing), true)

    @JvmStatic
    var shouldTransitionOnPlayback by BooleanSetting(
        getKey(R.string.setting_key_download_transition),
        true
    )

    @JvmStatic
    var showNowPlayingDetails
        by BooleanSetting(getKey(R.string.setting_key_show_now_playing_details), false)

    var scrobbleEnabled by BooleanSetting(getKey(R.string.setting_key_scrobble), false)

    // Normally you don't need to use these Settings directly,
    // use ActiveServerProvider.isID3Enabled() instead
    @JvmStatic
    var shouldUseId3Tags by BooleanSetting(getKey(R.string.setting_key_id3_tags), false)

    // See comment above.
    @JvmStatic
    var useId3TagsOffline by BooleanSetting(getKey(R.string.setting_key_id3_tags_offline), false)

    var activeServer by IntSetting(getKey(R.string.setting_key_server_instance), -1)

    var serverScaling by BooleanSetting(getKey(R.string.setting_key_server_scaling), false)

    var firstRunExecuted by BooleanSetting(getKey(R.string.setting_key_first_run_executed), false)

    val shouldShowArtistPicture
        by BooleanSetting(getKey(R.string.setting_key_show_artist_picture), false)

    @JvmStatic
    var chatRefreshInterval by StringIntSetting(
        getKey(R.string.setting_key_chat_refresh_interval),
        "5000"
    )

    var directoryCacheTime by StringIntSetting(
        getKey(R.string.setting_key_directory_cache_time),
        "300"
    )

    var shouldSortByDisc
        by BooleanSetting(getKey(R.string.setting_key_disc_sort), false)

    var shouldClearBookmark
        by BooleanSetting(getKey(R.string.setting_key_clear_bookmark), false)

    var shouldAskForShareDetails
        by BooleanSetting(getKey(R.string.setting_key_ask_for_share_details), true)

    var defaultShareDescription
        by StringSetting(getKey(R.string.setting_key_default_share_description), "")

    @JvmStatic
    val shareGreeting: String?
        get() {
            val preferences = preferences
            val context = Util.appContext()
            val defaultVal = String.format(
                context.resources.getString(R.string.share_default_greeting),
                context.resources.getString(R.string.common_appname)
            )
            return preferences.getString(
                getKey(R.string.setting_key_default_share_greeting),
                defaultVal
            )
        }

    var defaultShareExpiration by StringSetting(
        getKey(R.string.setting_key_default_share_expiration),
        "0"
    )

    val defaultShareExpirationInMillis: Long
        get() {
            val preferences = preferences
            val preference =
                preferences.getString(getKey(R.string.setting_key_default_share_expiration), "0")!!
            val split = PATTERN.split(preference)
            if (split.size == 2) {
                val timeSpanAmount = split[0].toInt()
                val timeSpanType = split[1]
                val timeSpan =
                    TimeSpanPicker.calculateTimeSpan(appContext, timeSpanType, timeSpanAmount)
                return timeSpan.totalMilliseconds
            }
            return 0
        }

    @JvmStatic
    var debugLogToFile by BooleanSetting(getKey(R.string.setting_key_debug_log_to_file), false)

    @JvmStatic
    val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(Util.appContext())

    @JvmStatic
    val overrideLanguage by StringSetting(getKey(R.string.setting_key_override_language), "")

    var useFiveStarRating by BooleanSetting(
        getKey(R.string.setting_key_use_five_star_rating),
        false
    )

    var useHwOffload by BooleanSetting(getKey(R.string.setting_key_hardware_offload), false)

    @JvmStatic
    var firstInstalledVersion by IntSetting(
        getKey(R.string.setting_key_first_installed_version),
        0
    )

    // TODO: Remove in December 2022
    fun migrateFeatureStorage() {
        val sp = appContext.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
        useFiveStarRating = sp.getBoolean("FIVE_STAR_RATING", false)
    }

    fun hasKey(key: String): Boolean {
        return preferences.contains(key)
    }

    fun getKey(key: Int): String {
        return appContext.getString(key)
    }

    private val appContext: Context
        get() = UApp.applicationContext()
}
