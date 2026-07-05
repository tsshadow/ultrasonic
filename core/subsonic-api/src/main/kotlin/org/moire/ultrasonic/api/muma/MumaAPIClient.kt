package org.moire.ultrasonic.api.muma

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder

class MumaAPIClient(
    baseUrl: String,
    okHttpClient: OkHttpClient,
    private var apiKey: String? = null,
    debug: Boolean = false,
    okLogger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
) {
    private val mumaOkHttpClient = okHttpClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val newRequest = if (apiKey != null) {
                request.newBuilder()
                    .header("X-API-Key", apiKey!!)
                    .build()
            } else {
                request
            }
            chain.proceed(newRequest)
        }
        .apply {
            if (debug) {
                val loggingInterceptor = HttpLoggingInterceptor(okLogger)
                loggingInterceptor.level = HttpLoggingInterceptor.Level.HEADERS
                addInterceptor(loggingInterceptor)
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(mumaOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val api: MumaAPIDefinition = retrofit.create(MumaAPIDefinition::class.java)

    fun setApiKey(key: String) {
        this.apiKey = key
    }

    companion object {
        private val gson: Gson = GsonBuilder()
            .setLenient()
            .create()
    }
}
