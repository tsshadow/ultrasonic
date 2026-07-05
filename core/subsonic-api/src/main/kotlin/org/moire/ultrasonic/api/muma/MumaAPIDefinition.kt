package org.moire.ultrasonic.api.muma

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MumaAPIDefinition {
    @POST("api/auth/login")
    fun login(
        @Body request: LoginRequest
    ): Call<LoginResponse>

    @GET("api/users/{user_id}/dynamic-playlists")
    fun getTiles(
        @Path("user_id") userId: Int
    ): Call<List<MumaTile>>

    @POST("api/users/{user_id}/dynamic-playlists")
    fun saveTile(
        @Path("user_id") userId: Int,
        @Body tile: MumaTile
    ): Call<MumaSaveResponse>

    @DELETE("api/users/{user_id}/dynamic-playlists/{playlist_id}")
    fun deleteTile(
        @Path("user_id") userId: Int,
        @Path("playlist_id") playlistId: Int
    ): Call<ResponseBody>

    @GET("api/users/{user_id}/settings/{app_id}")
    fun getSettings(
        @retrofit2.http.Path("user_id") userId: Int,
        @retrofit2.http.Path("app_id") appId: String
    ): Call<MumaSettingsResponse>

    @POST("api/users/{user_id}/settings/{app_id}")
    fun saveSettings(
        @retrofit2.http.Path("user_id") userId: Int,
        @retrofit2.http.Path("app_id") appId: String,
        @Body settings: MumaSettingsRequest
    ): Call<MumaStatusResponse>
}

data class MumaTile(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String,
    @SerializedName("params") val smartParams: String
)

data class MumaSaveResponse(
    val id: String
)

data class MumaSettingsRequest(
    val settings: String
)

data class MumaSettingsResponse(
    val settings: String?,
    val updated_at: String? = null
)

data class MumaStatusResponse(
    val status: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val id: Int,
    val username: String,
    val display_name: String,
    val is_admin: Boolean,
    val api_key: String
)
