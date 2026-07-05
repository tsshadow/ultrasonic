package org.moire.ultrasonic.api.muma

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature

class MumaAPIClient(
    baseUrl: String,
    okHttpClient: OkHttpClient
) {
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("${baseUrl.removeSuffix("/")}/muma/")
        .client(okHttpClient)
        .addConverterFactory(JacksonConverterFactory.create(jacksonMapper))
        .build()

    val api: MumaAPIDefinition = retrofit.create(MumaAPIDefinition::class.java)

    companion object {
        private val jacksonMapper: ObjectMapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(KotlinModule.Builder().build())
    }
}
