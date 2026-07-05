package org.moire.ultrasonic.api.muma

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface MumaAPIDefinition {
    @GET("tiles")
    fun getTiles(): Call<List<MumaTile>>

    @POST("tiles")
    fun saveTile(
        @Body tile: MumaTile
    ): Call<MumaSaveResponse>

    @DELETE("tiles")
    fun deleteTile(
        @Query("id") id: String
    ): Call<ResponseBody>

    @GET("users/{user_id}/settings/{app_id}")
    fun getSettings(
        @retrofit2.http.Path("user_id") userId: Int,
        @retrofit2.http.Path("app_id") appId: String
    ): Call<MumaSettingsResponse>

    @POST("users/{user_id}/settings/{app_id}")
    fun saveSettings(
        @retrofit2.http.Path("user_id") userId: Int,
        @retrofit2.http.Path("app_id") appId: String,
        @Body settings: MumaSettingsRequest
    ): Call<MumaStatusResponse>
}

data class MumaTile(
    val id: String? = null,
    val name: String,
    val smartParams: String
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
