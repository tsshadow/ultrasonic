package org.moire.ultrasonic.fragment

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.SearchRecentSuggestions
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import kotlin.math.ceil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.log.FileLoggerTree.Companion.deleteLogFiles
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileNumber
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileSizes
import org.moire.ultrasonic.log.FileLoggerTree.Companion.plantToTimberForest
import org.moire.ultrasonic.log.FileLoggerTree.Companion.uprootFromTimberForest
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.FileUtil.ultrasonicDirectory
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Settings.preferences
import org.moire.ultrasonic.util.Settings.shareGreeting
import org.moire.ultrasonic.util.Settings.shouldUseId3Tags
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.TimeSpanPreference
import org.moire.ultrasonic.util.TimeSpanPreferenceDialogFragmentCompat
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber

/**
 * Shows main app settings.
 */
@Suppress("TooManyFunctions")
class SettingsFragment :
    PreferenceFragmentCompat(),
    OnSharedPreferenceChangeListener,
    KoinComponent {
    private var theme: ListPreference? = null
    private var maxBitrateWifi: ListPreference? = null
    private var maxBitrateMobile: ListPreference? = null
    private var cacheSize: ListPreference? = null
    private var cacheLocation: Preference? = null
    private var preloadCount: ListPreference? = null
    private var bufferLength: ListPreference? = null
    private var incrementTime: ListPreference? = null
    private var networkTimeout: ListPreference? = null
    private var maxAlbums: ListPreference? = null
    private var maxSongs: ListPreference? = null
    private var maxArtists: ListPreference? = null
    private var defaultAlbums: ListPreference? = null
    private var defaultSongs: ListPreference? = null
    private var defaultArtists: ListPreference? = null
    private var chatRefreshInterval: ListPreference? = null
    private var directoryCacheTime: ListPreference? = null
    private var mediaButtonsEnabled: CheckBoxPreference? = null
    private var showArtistPicture: CheckBoxPreference? = null
    private var useId3TagsOffline: CheckBoxPreference? = null
    private var sharingDefaultDescription: EditTextPreference? = null
    private var sharingDefaultGreeting: EditTextPreference? = null
    private var sharingDefaultExpiration: TimeSpanPreference? = null
    private var resumeOnBluetoothDevice: Preference? = null
    private var pauseOnBluetoothDevice: Preference? = null
    private var debugLogToFile: CheckBoxPreference? = null
    private var customCacheLocation: CheckBoxPreference? = null

    private val mediaPlayerController: MediaPlayerController by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.menu_settings)
        theme = findPreference(getString(R.string.setting_key_theme))
        maxBitrateWifi = findPreference(getString(R.string.setting_key_max_bitrate_wifi))
        maxBitrateMobile = findPreference(getString(R.string.setting_key_max_bitrate_mobile))
        cacheSize = findPreference(getString(R.string.setting_key_cache_size))
        cacheLocation = findPreference(getString(R.string.setting_key_cache_location))
        preloadCount = findPreference(getString(R.string.setting_key_preload_count))
        bufferLength = findPreference(getString(R.string.setting_key_buffer_length))
        incrementTime = findPreference(getString(R.string.setting_key_increment_time))
        networkTimeout = findPreference(getString(R.string.setting_key_network_timeout))
        maxAlbums = findPreference(getString(R.string.setting_key_max_albums))
        maxSongs = findPreference(getString(R.string.setting_key_max_songs))
        maxArtists = findPreference(getString(R.string.setting_key_max_artists))
        defaultArtists = findPreference(getString(R.string.setting_key_default_artists))
        defaultSongs = findPreference(getString(R.string.setting_key_default_songs))
        defaultAlbums = findPreference(getString(R.string.setting_key_default_albums))
        chatRefreshInterval = findPreference(getString(R.string.setting_key_chat_refresh_interval))
        directoryCacheTime = findPreference(getString(R.string.setting_key_directory_cache_time))
        mediaButtonsEnabled = findPreference(getString(R.string.setting_key_media_buttons))
        sharingDefaultDescription =
            findPreference(getString(R.string.setting_key_default_share_description))
        sharingDefaultGreeting =
            findPreference(getString(R.string.setting_key_default_share_greeting))
        sharingDefaultExpiration =
            findPreference(getString(R.string.setting_key_default_share_expiration))
        resumeOnBluetoothDevice =
            findPreference(getString(R.string.setting_key_resume_on_bluetooth_device))
        pauseOnBluetoothDevice =
            findPreference(getString(R.string.setting_key_pause_on_bluetooth_device))
        debugLogToFile = findPreference(getString(R.string.setting_key_debug_log_to_file))
        showArtistPicture = findPreference(getString(R.string.setting_key_show_artist_picture))
        useId3TagsOffline = findPreference(getString(R.string.setting_key_id3_tags_offline))
        customCacheLocation = findPreference(getString(R.string.setting_key_custom_cache_location))

        sharingDefaultGreeting?.text = shareGreeting

        setupTextColors()
        setupClearSearchPreference()
        setupCacheLocationPreference()
        setupBluetoothDevicePreferences()
    }

    private fun setupTextColors(enabled: Boolean = shouldUseId3Tags) {
        val firstPart = getString(R.string.settings_use_id3_offline_warning)
        var secondPart = getString(R.string.settings_use_id3_offline_summary)

        // Little hack to circumvent a bug in Android. If we just change the color,
        // the text is not refreshed. If we also change the string, it is refreshed.
        if (enabled) secondPart += " "

        val color = if (enabled) "#bd5164" else "#813b48"

        Timber.i(color)

        val warning = SpannableString(firstPart + "\n" + secondPart)
        warning.setSpan(
            ForegroundColorSpan(Color.parseColor(color)), 0, firstPart.length, 0
        )
        warning.setSpan(
            StyleSpan(Typeface.BOLD), 0, firstPart.length, 0
        )
        useId3TagsOffline?.summary = warning
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        update()
    }

    /**
     * This function will be called when we return from the file picker
     * with a new custom cache location
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (
            requestCode == SELECT_CACHE_ACTIVITY &&
            resultCode == Activity.RESULT_OK &&
            resultData != null
        ) {
            val read = (resultData.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
            val write = (resultData.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0
            val persist = (resultData.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0

            if (read && write && persist) {
                if (resultData.data != null) {
                    // The result data contains a URI for the document or directory that
                    // the user selected.
                    val uri = resultData.data!!
                    val contentResolver = UApp.applicationContext().contentResolver

                    contentResolver.takePersistableUriPermission(uri, RW_FLAG)
                    setCacheLocation(uri.toString())
                    setupCacheLocationPreference()
                    return
                }
            }
            ErrorDialog.Builder(context)
                .setMessage(R.string.settings_cache_location_error)
                .show()
        }

        if (Settings.cacheLocationUri == "") {
            Settings.customCacheLocation = false
            customCacheLocation?.isChecked = false
            setupCacheLocationPreference()
        }
    }

    override fun onResume() {
        super.onResume()
        val preferences = preferences
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        val prefs = preferences
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        Timber.d("Preference changed: %s", key)
        update()
        when (key) {
            getString(R.string.setting_key_hide_media) -> {
                setHideMedia(sharedPreferences.getBoolean(key, false))
            }
            getString(R.string.setting_key_debug_log_to_file) -> {
                setDebugLogToFile(sharedPreferences.getBoolean(key, false))
            }
            getString(R.string.setting_key_id3_tags) -> {
                val enabled = sharedPreferences.getBoolean(key, false)
                showArtistPicture?.isEnabled = enabled
                useId3TagsOffline?.isEnabled = enabled
                setupTextColors(enabled)
            }
            getString(R.string.setting_key_theme) -> {
                RxBus.themeChangedEventPublisher.onNext(Unit)
            }
            getString(R.string.setting_key_custom_cache_location) -> {
                if (Settings.customCacheLocation) {
                    selectCacheLocation()
                } else {
                    if (Settings.cacheLocationUri != "") setCacheLocation("")
                    setupCacheLocationPreference()
                }
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        var dialogFragment: DialogFragment? = null
        if (preference is TimeSpanPreference) {
            dialogFragment = TimeSpanPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString("key", preference.getKey())
            dialogFragment.setArguments(bundle)
        }
        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(
                this.parentFragmentManager,
                "android.support.v7.preference.PreferenceFragment.DIALOG"
            )
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun setupCacheLocationPreference() {
        if (!Settings.customCacheLocation) {
            cacheLocation?.isVisible = false
            return
        }

        cacheLocation?.isVisible = true
        val uri = Uri.parse(Settings.cacheLocationUri)
        cacheLocation!!.summary = uri.path
        cacheLocation!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            selectCacheLocation()
            true
        }
    }

    private fun selectCacheLocation() {
        // Choose a directory using the system's file picker.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

        if (Settings.cacheLocationUri != "" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Settings.cacheLocationUri)
        }

        intent.addFlags(RW_FLAG)
        intent.addFlags(PERSISTABLE_FLAG)

        startActivityForResult(intent, SELECT_CACHE_ACTIVITY)
    }

    private fun setupBluetoothDevicePreferences() {
        val resumeSetting = Settings.resumeOnBluetoothDevice
        val pauseSetting = Settings.pauseOnBluetoothDevice
        resumeOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(resumeSetting)
        pauseOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(pauseSetting)
        resumeOnBluetoothDevice!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showBluetoothDevicePreferenceDialog(
                    R.string.settings_playback_resume_on_bluetooth_device,
                    Settings.resumeOnBluetoothDevice
                ) { choice: Int ->
                    Settings.resumeOnBluetoothDevice = choice
                    resumeOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(choice)
                }
                true
            }
        pauseOnBluetoothDevice!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showBluetoothDevicePreferenceDialog(
                    R.string.settings_playback_pause_on_bluetooth_device,
                    Settings.pauseOnBluetoothDevice
                ) { choice: Int ->
                    Settings.pauseOnBluetoothDevice = choice
                    pauseOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(choice)
                }
                true
            }
    }

    private fun showBluetoothDevicePreferenceDialog(
        @StringRes title: Int,
        defaultChoice: Int,
        onChosen: (Int) -> Unit
    ) {
        val choice = intArrayOf(defaultChoice)
        AlertDialog.Builder(activity).setTitle(title)
            .setSingleChoiceItems(
                R.array.bluetoothDeviceSettingNames, defaultChoice
            ) { _: DialogInterface?, i: Int -> choice[0] = i }
            .setNegativeButton(R.string.common_cancel) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.cancel()
            }
            .setPositiveButton(R.string.common_ok) { dialogInterface: DialogInterface, _: Int ->
                onChosen(choice[0])
                dialogInterface.dismiss()
            }
            .create().show()
    }

    private fun bluetoothDevicePreferenceToString(preferenceValue: Int): String {
        return when (preferenceValue) {
            Constants.PREFERENCE_VALUE_ALL -> {
                getString(R.string.settings_playback_bluetooth_all)
            }
            Constants.PREFERENCE_VALUE_A2DP -> {
                getString(R.string.settings_playback_bluetooth_a2dp)
            }
            Constants.PREFERENCE_VALUE_DISABLED -> {
                getString(R.string.settings_playback_bluetooth_disabled)
            }
            else -> ""
        }
    }

    private fun setupClearSearchPreference() {
        val clearSearchPreference =
            findPreference<Preference>(getString(R.string.setting_key_clear_search_history))
        if (clearSearchPreference != null) {
            clearSearchPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val suggestions = SearchRecentSuggestions(
                        activity,
                        SearchSuggestionProvider.AUTHORITY,
                        SearchSuggestionProvider.MODE
                    )
                    suggestions.clearHistory()
                    toast(activity, R.string.settings_search_history_cleared)
                    false
                }
        }
    }

    private fun update() {
        theme!!.summary = theme!!.entry
        maxBitrateWifi!!.summary = maxBitrateWifi!!.entry
        maxBitrateMobile!!.summary = maxBitrateMobile!!.entry
        cacheSize!!.summary = cacheSize!!.entry
        preloadCount!!.summary = preloadCount!!.entry
        bufferLength!!.summary = bufferLength!!.entry
        incrementTime!!.summary = incrementTime!!.entry
        networkTimeout!!.summary = networkTimeout!!.entry
        maxAlbums!!.summary = maxAlbums!!.entry
        maxArtists!!.summary = maxArtists!!.entry
        maxSongs!!.summary = maxSongs!!.entry
        defaultAlbums!!.summary = defaultAlbums!!.entry
        defaultArtists!!.summary = defaultArtists!!.entry
        defaultSongs!!.summary = defaultSongs!!.entry
        chatRefreshInterval!!.summary = chatRefreshInterval!!.entry
        directoryCacheTime!!.summary = directoryCacheTime!!.entry
        sharingDefaultExpiration!!.summary = sharingDefaultExpiration!!.text
        sharingDefaultDescription!!.summary = sharingDefaultDescription!!.text
        sharingDefaultGreeting!!.summary = sharingDefaultGreeting!!.text

        if (debugLogToFile?.isChecked == true) {
            debugLogToFile?.summary = getString(
                R.string.settings_debug_log_path,
                ultrasonicDirectory, FileLoggerTree.FILENAME
            )
        } else {
            debugLogToFile?.summary = ""
        }
        showArtistPicture?.isEnabled = shouldUseId3Tags
        useId3TagsOffline?.isEnabled = shouldUseId3Tags
    }

    private fun setHideMedia(hide: Boolean) {
        // TODO this only hides the media files in the Ultrasonic dir and not in the music cache
        val nomediaDir = File(ultrasonicDirectory, ".nomedia")
        if (hide && !nomediaDir.exists()) {
            if (!nomediaDir.mkdir()) {
                Timber.w("Failed to create %s", nomediaDir)
            }
        } else if (nomediaDir.exists()) {
            if (!nomediaDir.delete()) {
                Timber.w("Failed to delete %s", nomediaDir)
            }
        }
        toast(activity, R.string.settings_hide_media_toast, false)
    }

    private fun setCacheLocation(path: String) {
        if (path != "") {
            val uri = Uri.parse(path)
            cacheLocation!!.summary = uri.path ?: ""
        }

        Settings.cacheLocationUri = path

        // Clear download queue.
        mediaPlayerController.clear()
        mediaPlayerController.clearCaches()
        Storage.reset()
    }

    private fun setDebugLogToFile(writeLog: Boolean) {
        if (writeLog) {
            plantToTimberForest()
            Timber.i("Enabled debug logging to file")
        } else {
            uprootFromTimberForest()
            Timber.i("Disabled debug logging to file")
            val fileNum = getLogFileNumber()
            val fileSize = getLogFileSizes()
            val message = getString(
                R.string.settings_debug_log_summary,
                fileNum.toString(),
                ceil(fileSize.toDouble() / 1000 / 1000).toString(),
                ultrasonicDirectory
            )
            val keep = R.string.settings_debug_log_keep
            val delete = R.string.settings_debug_log_delete
            InfoDialog.Builder(activity)
                .setMessage(message)
                .setNegativeButton(keep) { dIf: DialogInterface, _: Int ->
                    dIf.cancel()
                }
                .setPositiveButton(delete) { dIf: DialogInterface, _: Int ->
                    deleteLogFiles()
                    Timber.i("Deleted debug log files")
                    dIf.dismiss()
                    AlertDialog.Builder(activity)
                        .setMessage(R.string.settings_debug_log_deleted)
                        .setPositiveButton(R.string.common_ok) { dIf2: DialogInterface, _: Int ->
                            dIf2.dismiss()
                        }
                        .create().show()
                }
                .create().show()
        }
    }

    companion object {
        const val SELECT_CACHE_ACTIVITY = 161161
        const val RW_FLAG = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val PERSISTABLE_FLAG = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
