package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.fragment.tsshadow.TileStorage
import org.moire.ultrasonic.fragment.tsshadow.toTileInfo
import org.moire.ultrasonic.fragment.tsshadow.toSmartParamsJson
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.ConfirmationDialog
import timber.log.Timber
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.fragment.tsshadow.TileInfo

class MumaSyncFragment : Fragment() {

    private val activeServerProvider: ActiveServerProvider by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.muma_sync_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btn_logon).setOnClickListener {
            performLogon()
        }

        view.findViewById<MaterialButton>(R.id.btn_load_tiles).setOnClickListener {
            loadTiles()
        }

        view.findViewById<MaterialButton>(R.id.btn_write_tiles).setOnClickListener {
            writeTiles()
        }

        view.findViewById<MaterialButton>(R.id.btn_clear_tiles).setOnClickListener {
            clearTiles()
        }

        view.findViewById<MaterialButton>(R.id.btn_load_settings).setOnClickListener {
            loadSettings()
        }

        view.findViewById<MaterialButton>(R.id.btn_write_settings).setOnClickListener {
            writeSettings()
        }
    }

    private fun performLogon() {
        lifecycleScope.launch(toastingExceptionHandler()) {
            withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().mumaLogin()
            }
            toast(R.string.muma_sync_logon_success, true, requireContext())
        }
    }

    private fun loadTiles() {
        lifecycleScope.launch(toastingExceptionHandler()) {
            val mumaTiles = withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().getMumaTiles()
            }
            val tiles = mumaTiles.mapNotNull { it.toTileInfo() }
            
            // We need to group them by pageKey and save
            val groupedTiles = tiles.groupBy { it.pageKey }
            groupedTiles.forEach { (pageKey, pageTiles) ->
                TileStorage.saveTiles(requireContext(), pageTiles.toMutableList(), pageKey)
            }
            
            toast(R.string.sync_tiles_success, true, requireContext())
        }
    }

    private fun writeTiles() {
        lifecycleScope.launch(toastingExceptionHandler()) {
            // This is a bit more complex as we need all tiles from all pages.
            // TileStorage doesn't easily provide a list of all pages.
            // For now, let's at least sync the main ones or those we can find.
            // Actually, TileStorage uses SharedPreferences with keys like "tiles_" + pageKey
            
            val prefs = requireContext().getSharedPreferences("TileStorage", android.content.Context.MODE_PRIVATE)
            val allKeys = prefs.all.keys.filter { it.startsWith("tiles_") }
            
            withContext(Dispatchers.IO) {
                val service = MusicServiceFactory.getMusicService()
                allKeys.forEach { key ->
                    val pageKey = key.removePrefix("tiles_")
                    val tiles: List<TileInfo> = TileStorage.loadTiles(requireContext(), pageKey)
                    tiles.forEach { tile: TileInfo ->
                        try {
                            val id = service.saveMumaTile(
                                org.moire.ultrasonic.domain.MumaTile(
                                    id = tile.id,
                                    name = tile.title,
                                    smartParams = tile.toSmartParamsJson()
                                )
                            )
                            tile.id = id
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to save tile '${tile.title}' during global sync")
                        }
                    }
                    // Save back local tiles to store the updated IDs
                    withContext(Dispatchers.Main) {
                        TileStorage.saveTiles(requireContext(), tiles.toMutableList(), pageKey)
                    }
                }
            }
            toast(R.string.sync_tiles_success, true, requireContext())
        }
    }

    private fun clearTiles() {
        ConfirmationDialog.Builder(requireContext())
            .setMessage("Are you sure you want to clear all local tiles?")
            .setPositiveButton(R.string.common_ok) { _, _ ->
                val prefs = requireContext().getSharedPreferences("TileStorage", android.content.Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                toast("All tiles cleared locally", true, requireContext())
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun loadSettings() {
        val server = activeServerProvider.getActiveServer()
        val userId = server.mumaUserId ?: Settings.mumaUserId
        if (userId == -1) {
            toast("MuMa User ID not set. Please check your MuMa server settings.", true, requireContext())
            return
        }

        lifecycleScope.launch(toastingExceptionHandler()) {
            val json = withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().getMumaSettings(userId, "ultrasonic")
            }

            if (json != null) {
                val allPrefs = Gson().fromJson(json, Map::class.java)
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val editor = prefs.edit()

                allPrefs.forEach { (key, value) ->
                    val k = key as String
                    when (value) {
                        is Boolean -> editor.putBoolean(k, value)
                        is Float -> editor.putFloat(k, value)
                        is Int -> editor.putInt(k, value)
                        is Long -> editor.putLong(k, value)
                        is Double -> {
                            if (value == value.toInt().toDouble()) {
                                editor.putInt(k, value.toInt())
                            } else {
                                editor.putFloat(k, value.toFloat())
                            }
                        }
                        is String -> editor.putString(k, value)
                    }
                }
                editor.apply()
                toast(R.string.settings_muma_sync_success, true, requireContext())

                ConfirmationDialog.Builder(requireContext())
                    .setMessage("Settings restored successfully. Please restart the app to apply all changes.")
                    .setPositiveButton(R.string.common_ok) { d: DialogInterface, _: Int -> d.dismiss() }
                    .show()
            } else {
                toast("No settings found on MuMa server", true, requireContext())
            }
        }
    }

    private fun writeSettings() {
        val server = activeServerProvider.getActiveServer()
        val userId = server.mumaUserId ?: Settings.mumaUserId
        if (userId == -1) {
            toast("MuMa User ID not set. Please check your MuMa server settings.", true, requireContext())
            return
        }

        lifecycleScope.launch(toastingExceptionHandler()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val allPrefs = prefs.all
            val json = Gson().toJson(allPrefs)

            withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().saveMumaSettings(userId, "ultrasonic", json)
            }
            toast(R.string.settings_muma_sync_success, true, requireContext())
        }
    }
}
