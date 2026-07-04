package org.moire.ultrasonic.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.R
import timber.log.Timber

/**
 * Utility to check for updates from the custom APK hoster.
 */
object UpdateChecker {
    private fun getUpdateUrl(): String {
        val apkName = if (BuildConfig.DEBUG) "ultrasonic-debug" else "ultrasonic"
        return "${BuildConfig.APK_HOST_URL}/api/version?apk=$apkName&user=${BuildConfig.APK_HOST_USER}"
    }

    /**
     * Opens the update website in a browser.
     */
    fun openUpdateSite(context: Context) {
        val baseUrl = BuildConfig.APK_HOST_URL
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendQueryParameter("v", versionName)
            .appendQueryParameter("c", versionCode.toString())
            .build()
        
        Timber.d("Opening update site: $uri")
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open update site")
            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Checks for updates and shows a dialog if a new version is available.
     * Should be called from a coroutine scope.
     * @param manual If true, shows a message if no update is found.
     */
    suspend fun checkForUpdates(context: Context, manual: Boolean = false) {
        val url = getUpdateUrl()
        Timber.d("Checking for updates at $url")
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
                    if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.update_no_new_version, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else if (manual) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for updates")
            if (manual) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun fetchLatestVersion(): JSONObject? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val url = getUpdateUrl()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Ultrasonic/${BuildConfig.VERSION_NAME}")
            .header("X-Upload-Password", BuildConfig.APK_HOST_PASSWORD)
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
            Timber.e(e, "Error fetching version info from $url")
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
