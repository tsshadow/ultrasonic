/*
 * Util.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnyRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.fragment.findNavController
import java.io.Closeable
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.domain.Bookmark
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import timber.log.Timber

private const val LINE_LENGTH = 60
private const val DEGRADE_PRECISION_AFTER = 10
private const val MINUTES_IN_HOUR = 60
private const val KBYTE = 1024

/**
 * Contains various utility functions
 */
@Suppress("TooManyFunctions", "LargeClass")
object Util {

    private val GIGA_BYTE_FORMAT = DecimalFormat("0.00 GB")
    private val MEGA_BYTE_FORMAT = DecimalFormat("0.00 MB")
    private val KILO_BYTE_FORMAT = DecimalFormat("0 KB")
    private var GIGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var MEGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var KILO_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var BYTE_LOCALIZED_FORMAT: DecimalFormat? = null

    // Used by hexEncode()
    private val HEX_DIGITS =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    private var toast: Toast? = null

    // Retrieves an instance of the application Context
    fun appContext(): Context {
        return applicationContext()
    }

    @JvmStatic
    fun applyTheme(context: Context?) {
        if (context == null) return
        val style = getStyleFromSettings(context)
        // First set the theme (light, dark, etc.)
        context.setTheme(style)
        // Then set an overlay controlling the status bar behaviour etc.
        context.setTheme(R.style.UltrasonicTheme_Base)
    }

    private fun getStyleFromSettings(context: Context): Int {
        return when (Settings.theme.lowercase()) {
            context.getString(R.string.setting_key_theme_dark) -> {
                R.style.UltrasonicTheme_Dark
            }

            context.getString(R.string.setting_key_theme_black) -> {
                R.style.UltrasonicTheme_Black
            }

            context.getString(R.string.setting_key_theme_light) -> {
                R.style.UltrasonicTheme_Light
            }

            else -> {
                R.style.UltrasonicTheme_DayNight
            }
        }
    }

    fun getString(@StringRes resId: Int): String {
        return applicationContext().resources.getString(resId)
    }

    @JvmStatic
    @JvmOverloads
    fun toast(messageId: Int, shortDuration: Boolean = true, context: Context?) {
        toast(applicationContext().getString(messageId), shortDuration, context)
    }

    @JvmStatic
    fun toast(message: CharSequence, context: Context?) {
        toast(message, true, context)
    }

    @JvmStatic
    // Toast needs a real context or it will throw a IllegalAccessException
    // We wrap it in a try-catch block, because if called after doing
    // some background processing, our context might have expired!
    fun toast(message: CharSequence, shortDuration: Boolean, context: Context?) {
        try {
            if (toast == null) {
                toast = Toast.makeText(
                    context,
                    message,
                    if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                )
                toast!!.setGravity(Gravity.CENTER, 0, 0)
            } else {
                toast!!.setText(message)
                toast!!.duration =
                    if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            }
            toast!!.show()
        } catch (all: Exception) {
            Timber.w(all)
        }
    }

    fun Fragment.toast(message: CharSequence, shortDuration: Boolean = true) {
        toast(
            message,
            shortDuration = shortDuration,
            context = this.context
        )
    }

    fun Fragment.toast(messageId: Int = 0, shortDuration: Boolean = true) {
        toast(
            messageId = messageId,
            shortDuration = shortDuration,
            context = this.context
        )
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     *
     *  * `format(918)` returns *"918 B"*.
     *  * `format(98765)` returns *"96 KB"*.
     *  * `format(1238476)` returns *"1.2 MB"*.
     *
     * This method assumes that 1 KB is 1024 bytes.
     * To get a localized string, please use formatLocalizedBytes instead.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    @JvmStatic
    @Synchronized
    fun formatBytes(byteCount: Long): String {

        // More than 1 GB?
        if (byteCount >= KBYTE * KBYTE * KBYTE) {
            return GIGA_BYTE_FORMAT.format(byteCount.toDouble() / (KBYTE * KBYTE * KBYTE))
        }

        // More than 1 MB?
        if (byteCount >= KBYTE * KBYTE) {
            return MEGA_BYTE_FORMAT.format(byteCount.toDouble() / (KBYTE * KBYTE))
        }

        // More than 1 KB?
        return if (byteCount >= KBYTE) {
            KILO_BYTE_FORMAT.format(byteCount.toDouble() / KBYTE)
        } else "$byteCount B"
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     *
     *  * `format(918)` returns *"918 B"*.
     *  * `format(98765)` returns *"96 KB"*.
     *  * `format(1238476)` returns *"1.2 MB"*.
     *
     * This method assumes that 1 KB is 1024 bytes.
     * This version of the method returns a localized string.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    @Synchronized
    @Suppress("ReturnCount")
    fun formatLocalizedBytes(byteCount: Long, context: Context): String {

        // More than 1 GB?
        if (byteCount >= KBYTE * KBYTE * KBYTE) {
            if (GIGA_BYTE_LOCALIZED_FORMAT == null) {
                GIGA_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_gigabyte))
            }
            return GIGA_BYTE_LOCALIZED_FORMAT!!
                .format(byteCount.toDouble() / (KBYTE * KBYTE * KBYTE))
        }

        // More than 1 MB?
        if (byteCount >= KBYTE * KBYTE) {
            if (MEGA_BYTE_LOCALIZED_FORMAT == null) {
                MEGA_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_megabyte))
            }
            return MEGA_BYTE_LOCALIZED_FORMAT!!
                .format(byteCount.toDouble() / (KBYTE * KBYTE))
        }

        // More than 1 KB?
        if (byteCount >= KBYTE) {
            if (KILO_BYTE_LOCALIZED_FORMAT == null) {
                KILO_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_kilobyte))
            }
            return KILO_BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble() / KBYTE)
        }
        if (BYTE_LOCALIZED_FORMAT == null) {
            BYTE_LOCALIZED_FORMAT =
                DecimalFormat(context.resources.getString(R.string.util_bytes_format_byte))
        }
        return BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble())
    }

    @Suppress("SuspiciousEqualsCombination")
    fun equals(object1: Any?, object2: Any?): Boolean {
        return object1 === object2 || !(object1 == null || object2 == null) && object1 == object2
    }

    /**
     * Encodes the given string by using the hexadecimal representation of its UTF-8 bytes.
     *
     * @param s The string to encode.
     * @return The encoded string.
     */
    @Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
    fun utf8HexEncode(s: String?): String? {
        if (s == null) {
            return null
        }
        val utf8: ByteArray = try {
            s.toByteArray(charset(Constants.UTF_8))
        } catch (x: UnsupportedEncodingException) {
            throw RuntimeException(x)
        }
        return hexEncode(utf8)
    }

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data Bytes to convert to hexadecimal characters.
     * @return A string containing hexadecimal characters.
     */
    @Suppress("MagicNumber")
    fun hexEncode(data: ByteArray): String {
        val length = data.size
        val out = CharArray(length shl 1)
        var j = 0

        // two characters form the hex value.
        for (aData in data) {
            out[j++] = HEX_DIGITS[0xF0 and aData.toInt() ushr 4]
            out[j++] = HEX_DIGITS[0x0F and aData.toInt()]
        }
        return String(out)
    }

    /**
     * Calculates the MD5 digest and returns the value as a 32 character hex string.
     *
     * @param s Data to digest.
     * @return MD5 digest as a hex string.
     */
    @JvmStatic
    @Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
    fun md5Hex(s: String?): String? {
        return if (s == null) {
            null
        } else try {
            val md5 = MessageDigest.getInstance("MD5")
            hexEncode(md5.digest(s.toByteArray(charset(Constants.UTF_8))))
        } catch (x: Exception) {
            throw RuntimeException(x.message, x)
        }
    }

    @JvmStatic
    fun getGrandparent(path: String?): String? {
        // Find the top level folder, assume it is the album artist
        if (path != null) {
            val slashIndex = path.indexOf('/')
            if (slashIndex > 0) {
                return path.substring(0, slashIndex)
            }
        }
        return null
    }

    /**
     * Check if a usable network for downloading media is available
     *
     * @return Boolean
     */
    @JvmStatic
    fun hasUsableNetwork(): Boolean {
        val isUnmetered = !isNetworkRestricted()
        val wifiRequired = Settings.isWifiRequiredForDownload
        return (!wifiRequired || isUnmetered)
    }

    fun isNetworkRestricted(): Boolean {
        return isNetworkMetered() || isNetworkCellular()
    }

    private fun isNetworkMetered(): Boolean {
        val connManager = connectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = connManager.getNetworkCapabilities(
                connManager.activeNetwork
            )
            if (capabilities != null &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return false
            }
        }
        return connManager.isActiveNetworkMetered
    }

    @Suppress("DEPRECATION")
    private fun isNetworkCellular(): Boolean {
        val connManager = connectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connManager.activeNetwork
                ?: return false // Nothing connected
            connManager.getNetworkInfo(network)
                ?: return true // Better be safe than sorry
            val capabilities = connManager.getNetworkCapabilities(network)
                ?: return true // Better be safe than sorry
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            // if the default network is a VPN,
            // this method will return the NetworkInfo for one of its underlying networks
            val info = connManager.activeNetworkInfo
                ?: return false // Nothing connected
            info.type == ConnectivityManager.TYPE_MOBILE
        }
    }

    @JvmStatic
    fun isExternalStoragePresent(): Boolean =
        Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    @JvmStatic
    fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (x: InterruptedException) {
            Timber.w(x, "Interrupted from sleep.")
        }
    }

    fun createWifiLock(tag: String?): WifiLock {
        val wm =
            appContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag)
    }

    fun getScaledHeight(height: Double, width: Double, newWidth: Int): Int {
        // Try to keep correct aspect ratio of the original image, do not force a square
        val aspectRatio = height / width

        // Assume the size given refers to the width of the image, so calculate the new height using
        // the previously determined aspect ratio
        return (newWidth * aspectRatio).roundToInt()
    }

    fun getSongsFromSearchResult(searchResult: SearchResult): MusicDirectory {
        val musicDirectory = MusicDirectory()
        for (entry in searchResult.songs) {
            musicDirectory.add(entry)
        }
        return musicDirectory
    }

    @JvmStatic
    fun getSongsFromBookmarks(bookmarks: Iterable<Bookmark>): MusicDirectory {
        val musicDirectory = MusicDirectory()
        var song: Track
        for (bookmark in bookmarks) {
            song = bookmark.track
            song.bookmarkPosition = bookmark.position
            musicDirectory.add(song)
        }
        return musicDirectory
    }

    @JvmStatic
    @Suppress("MagicNumber")
    fun getNotificationImageSize(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val imageSizeLarge =
            min(metrics.widthPixels, metrics.heightPixels).toFloat().roundToInt()
        return when {
            imageSizeLarge <= 480 -> {
                64
            }
            imageSizeLarge <= 768 -> 128
            else -> 256
        }
    }

    @Suppress("MagicNumber")
    fun getAlbumImageSize(context: Context?): Int {
        val metrics = context!!.resources.displayMetrics
        val imageSizeLarge =
            min(metrics.widthPixels, metrics.heightPixels).toFloat().roundToInt()
        return when {
            imageSizeLarge <= 480 -> {
                128
            }
            imageSizeLarge <= 768 -> 256
            else -> 512
        }
    }

    fun getMinDisplayMetric(): Int {
        val metrics = appContext().resources.displayMetrics
        return min(metrics.widthPixels, metrics.heightPixels)
    }

    fun getMaxDisplayMetric(): Int {
        val metrics = appContext().resources.displayMetrics
        return max(metrics.widthPixels, metrics.heightPixels)
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and
            // width
            val heightRatio = (height.toFloat() / reqHeight.toFloat()).roundToInt()
            val widthRatio = (width.toFloat() / reqWidth.toFloat()).roundToInt()

            // Choose the smallest ratio as inSampleSize value, this will
            // guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = min(heightRatio, widthRatio)
        }
        return inSampleSize
    }

    @JvmStatic
    fun isNullOrWhiteSpace(string: String?): Boolean {
        return string.isNullOrEmpty() || string.trim { it <= ' ' }.isEmpty()
    }

    @JvmOverloads
    fun formatTotalDuration(totalDuration: Long?, inMilliseconds: Boolean = false): String {
        if (totalDuration == null) return ""
        var millis = totalDuration
        if (!inMilliseconds) {
            millis = totalDuration * 1000
        }
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) -
            TimeUnit.MINUTES.toSeconds(hours * MINUTES_IN_HOUR + minutes)

        return when {
            hours >= DEGRADE_PRECISION_AFTER -> {
                String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
                )
            }

            hours > 0 -> {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            }

            minutes >= DEGRADE_PRECISION_AFTER -> {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            minutes > 0 -> String.format(
                Locale.getDefault(),
                "%d:%02d",
                minutes,
                seconds
            )

            else -> String.format(Locale.getDefault(), "0:%02d", seconds)
        }
    }

    fun ensureNotificationChannel(
        id: String,
        name: String,
        importance: Int? = null,
        notificationManager: NotificationManagerCompat
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // The suggested importance of a startForeground service notification is IMPORTANCE_LOW
            val channel = NotificationChannel(
                id,
                name,
                importance ?: NotificationManager.IMPORTANCE_DEFAULT
            )

            channel.lightColor = android.R.color.holo_blue_dark
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)

            notificationManager.createNotificationChannel(channel)
        }
    }

    fun ensurePermissionToPostNotification(
        fragment: ComponentActivity,
        onGranted: (() -> Unit)? = null
    ) {
        if (ContextCompat.checkSelfPermission(
                applicationContext(),
                POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {

            val requestPermissionLauncher =
                fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    if (!it) {
                        toast(R.string.notification_permission_required, context = fragment)
                    }
                }

            requestPermissionLauncher.launch(POST_NOTIFICATIONS)
        } else {
            // Execute the closure
            if (onGranted != null) {
                onGranted()
            }
        }
    }

    fun postNotificationIfPermitted(
        notificationManagerCompat: NotificationManagerCompat,
        id: Int,
        notification: Notification
    ) {
        if (ContextCompat.checkSelfPermission(
                applicationContext(),
                POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            notificationManagerCompat.notify(id, notification)
        }
    }

    @JvmStatic
    fun getVersionName(context: Context): String? {
        var versionName: String? = null
        val pm = context.packageManager
        if (pm != null) {
            val packageName = context.packageName
            try {
                versionName = pm.getPackageInfo(packageName, 0).versionName
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        return versionName
    }

    @Suppress("DEPRECATION")
    fun getVersionCode(context: Context): Int {
        var versionCode = 0
        val pm = context.packageManager
        if (pm != null) {
            val packageName = context.packageName
            try {
                versionCode = pm.getPackageInfo(packageName, 0).versionCode
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        return versionCode
    }

    @JvmStatic
    fun scanMedia(file: String?) {
        // TODO this doesn't work for URIs
        MediaScannerConnection.scanFile(
            applicationContext(), arrayOf(file),
            null, null
        )
    }

    fun isFirstRun(): Boolean {
        if (Settings.firstRunExecuted) return false

        Settings.firstRunExecuted = true
        return true
    }

    fun hideKeyboard(activity: Activity?) {
        val inputManager =
            activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = activity.currentFocus
        if (currentFocusedView != null) {
            inputManager.hideSoftInputFromWindow(
                currentFocusedView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }

    fun getUriToDrawable(context: Context, @AnyRes drawableId: Int): Uri {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + context.resources.getResourcePackageName(drawableId) +
                '/' + context.resources.getResourceTypeName(drawableId) +
                '/' + context.resources.getResourceEntryName(drawableId)
        )
    }

    data class ReadableEntryDescription(
        var artist: String,
        var title: String,
        val trackNumber: String,
        val duration: String,
        var bitrate: String?,
        var fileFormat: String?,
    )

    @Suppress("ComplexMethod", "LongMethod")
    fun readableEntryDescription(song: Track): ReadableEntryDescription {
        val artist = StringBuilder(LINE_LENGTH)
        var bitRate: String? = null
        var trackText = ""

        val duration = song.duration

        if (song.bitRate != null && song.bitRate!! > 0)
            bitRate = String.format(
                appContext().getString(R.string.song_details_kbps), song.bitRate
            )

        val fileFormat: String?
        val suffix = song.suffix
        val transcodedSuffix = song.transcodedSuffix

        fileFormat = if (
            TextUtils.isEmpty(transcodedSuffix) || transcodedSuffix == suffix || song.isVideo
        ) suffix else String.format(Locale.ROOT, "%s > %s", suffix, transcodedSuffix)

        val artistName = song.artist

        if (artistName != null) {
            if (Settings.shouldDisplayBitrateWithArtist && (
                !bitRate.isNullOrBlank() || !fileFormat.isNullOrBlank()
                )
            ) {
                artist.append(artistName).append(" (").append(
                    String.format(
                        appContext().getString(R.string.song_details_all),
                        if (bitRate == null) ""
                        else String.format(Locale.ROOT, "%s ", bitRate),
                        fileFormat
                    )
                ).append(')')
            } else {
                artist.append(artistName)
            }
        }

        val trackNumber = song.track ?: 0

        val title = StringBuilder(LINE_LENGTH)
        if (Settings.shouldShowTrackNumber && trackNumber > 0) {
            trackText = String.format(Locale.ROOT, "%02d.", trackNumber)
        }

        title.append(song.title)

        if (song.isVideo && Settings.shouldDisplayBitrateWithArtist) {
            title.append(" (").append(
                String.format(
                    appContext().getString(R.string.song_details_all),
                    if (bitRate == null) ""
                    else String.format(Locale.ROOT, "%s ", bitRate),
                    fileFormat
                )
            ).append(')')
        }

        return ReadableEntryDescription(
            artist = artist.toString(),
            title = title.toString(),
            trackNumber = trackText,
            duration = formatTotalDuration(duration?.toLong()),
            bitrate = bitRate,
            fileFormat = fileFormat,
        )
    }

    /**
     * Removes diacritics (~= accents) from a string. The case will not be altered.
     * Note that ligatures will be left as is.
     *
     * @param input String to be stripped
     * @return input text with diacritics removed
     *
     */
    fun stripAccents(input: String): String {
        val decomposed: java.lang.StringBuilder =
            java.lang.StringBuilder(Normalizer.normalize(input, Normalizer.Form.NFD))
        convertRemainingAccentCharacters(decomposed)
        return STRIP_ACCENTS_PATTERN.matcher(decomposed).replaceAll("")
    }

    /**
     * Pattern used in [.stripAccents].
     */
    private val STRIP_ACCENTS_PATTERN: Pattern =
        Pattern.compile("\\p{InCombiningDiacriticalMarks}+") // $NON-NLS-1$

    private fun convertRemainingAccentCharacters(decomposed: java.lang.StringBuilder) {
        for (i in decomposed.indices) {
            if (decomposed[i] == '\u0141') {
                decomposed.setCharAt(i, 'L')
            } else if (decomposed[i] == '\u0142') {
                decomposed.setCharAt(i, 'l')
            }
        }
    }

    fun getPlayListFromTimeline(
        timeline: Timeline?,
        isShuffled: Boolean,
        firstIndex: Int? = null,
        count: Int? = null
    ): List<MediaItem> {
        if (timeline == null) return emptyList()
        if (timeline.windowCount < 1) return emptyList()

        val playlist: MutableList<MediaItem> = mutableListOf()
        var i = firstIndex ?: timeline.getFirstWindowIndex(isShuffled)
        if (i == C.INDEX_UNSET) return emptyList()

        while (i != C.INDEX_UNSET && (count != playlist.count())) {
            val window = timeline.getWindow(i, Timeline.Window())
            playlist.add(window.mediaItem)
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, isShuffled)
        }
        return playlist
    }

    fun getPendingIntentToShowPlayer(context: Context): PendingIntent {
        val intent = Intent(context, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // needed starting Android 12 (S = 31)
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        intent.putExtra(Constants.INTENT_SHOW_PLAYER, true)
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun dpToPx(dp: Int, activity: Activity): Int {
        return (dp * (activity.resources.displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
            .roundToInt()
    }

    private val connectivityManager: ConnectivityManager
        get() = appContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Executes the given block if this is not null.
     * @return: the return of the block, or null if this is null
     */
    fun <T : Any, R> T?.ifNotNull(block: (T) -> R): R? {
        return this?.let(block)
    }

    /**
     * Closes a Closeable while ignoring any errors.
     **/
    fun Closeable?.safeClose() {
        try {
            this?.close()
        } catch (_: Exception) {
            // Ignored
        }
    }

    fun Service.stopForegroundRemoveNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun dumpSettingsToLog() {
        Timber.d("Current user preferences")
        Timber.d("========================")
        val keys = Settings.preferences.all

        keys.forEach {
            Timber.d("${it.key}: ${it.value}")
        }
    }

    fun Fragment.navigateToCurrent() {
        if (Settings.shouldTransitionOnPlayback) {
            findNavController().popBackStack(R.id.playerFragment, true)
            findNavController().navigate(R.id.playerFragment)
        }
    }
}
