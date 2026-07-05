package org.moire.ultrasonic.data

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

class MumaClient(private val okHttpClient: OkHttpClient) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class LoginRequest(val username: String, val password: String)
    data class LoginResponse(
        val id: Int,
        val username: String,
        val display_name: String,
        val is_admin: Boolean,
        val api_key: String
    )

    data class DynamicPlaylist(
        val id: Int,
        val user_id: Int,
        val name: String,
        val params: String
    )

    fun login(baseUrl: String, username: String, password: String): LoginResponse? {
        val mumaUrl = baseUrl.replace("lms", "muma") + "/api/auth/login"
        val body = gson.toJson(LoginRequest(username, password)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(mumaUrl)
            .post(body)
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    gson.fromJson(responseBody, LoginResponse::class.java).also {
                        Settings.mumaApiKey = it.api_key
                        Settings.mumaUserId = it.id
                    }
                } else {
                    Timber.e("Muma login failed: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Muma login error")
            null
        }
    }

    fun getDynamicPlaylists(baseUrl: String): List<DynamicPlaylist> {
        val userId = Settings.mumaUserId
        if (userId == -1) return emptyList()

        val mumaUrl = baseUrl.replace("lms", "muma") + "/api/users/$userId/dynamic-playlists"
        val request = Request.Builder()
            .url(mumaUrl)
            .get()
            .header("X-API-Key", Settings.mumaApiKey)
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    gson.fromJson(responseBody, Array<DynamicPlaylist>::class.java).toList()
                } else {
                    Timber.e("Failed to get dynamic playlists: ${response.code}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting dynamic playlists")
            emptyList()
        }
    }

    fun saveDynamicPlaylist(baseUrl: String, name: String, params: String): Boolean {
        val userId = Settings.mumaUserId
        if (userId == -1) return false

        val mumaUrl = baseUrl.replace("lms", "muma") + "/api/users/$userId/dynamic-playlists"
        val json = gson.toJson(mapOf("name" to name, "params" to params))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(mumaUrl)
            .post(body)
            .header("X-API-Key", Settings.mumaApiKey)
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving dynamic playlist")
            false
        }
    }
}
