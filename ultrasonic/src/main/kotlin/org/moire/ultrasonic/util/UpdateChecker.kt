package org.moire.ultrasonic.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.moire.ultrasonic.BuildConfig
import timber.log.Timber

/**
 * Utility to check for updates from the custom APK hoster.
 */
object UpdateChecker {
    private const val UPDATE_URL = "https://apk.teunschriks.nl/api/version?apk=ultrasonic"

    /**
     * Checks for updates and shows a dialog if a new version is available.
     * Should be called from a coroutine scope.
     */
    suspend fun checkForUpdates(context: Context) {
        Timber.d("Checking for updates at $UPDATE_URL")
        try {
            val latestVersion = fetchLatestVersion()
            if (latestVersion != null) {
                val latestVersionCodeStr = latestVersion.optString("versionCode", "0")
                val latestVersionCode = latestVersionCodeStr.toIntOrNull() ?: 0
                val currentVersionCode = BuildConfig.VERSION_CODE

                Timber.d("Update check: current version code=$currentVersionCode, latest version code=$latestVersionCode")

                if (latestVersionCode > currentVersionCode) {
                    val latestVersionName = latestVersion.optString("versionName", "")
                    val downloadUrl = latestVersion.optString("url", "")
                    
                    Timber.i("New version available: $latestVersionName ($latestVersionCode)")
                    
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(context, latestVersionName, downloadUrl)
                    }
                } else {
                    Timber.d("App is up to date.")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for updates")
        }
    }

    private suspend fun fetchLatestVersion(): JSONObject? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(UPDATE_URL)
            .header("User-Agent", "Ultrasonic/${BuildConfig.VERSION_NAME}")
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Update check failed with code: ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                return@withContext JSONObject(body)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching version info from $UPDATE_URL")
            null
        }
    }

    private fun showUpdateDialog(context: Context, versionName: String, downloadUrl: String) {
        try {
            AlertDialog.Builder(context)
                .setTitle("New Version Available")
                .setMessage("A new version of Ultrasonic ($versionName) is available. Would you like to download it now?")
                .setPositiveButton("Download") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    context.startActivity(intent)
                }
                .setNegativeButton("Later", null)
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to show update dialog")
        }
    }
}
