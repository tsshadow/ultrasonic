package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import java.lang.reflect.Type
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
/**
 * Retrofit Converter Factory which uses Jackson for conversion and maintains the
 * version of the Subsonic API.
 * @param notifier: callback function to call when the Subsonic API version changes
 */
class VersionAwareJacksonConverterFactory(
    private val notifier: (SubsonicAPIVersions) -> Unit = {}
) : Converter.Factory() {

    constructor(
        notifier: (SubsonicAPIVersions) -> Unit = {},
        mapper: ObjectMapper
    ) : this(notifier) {
        this.mapper = mapper
        jacksonConverterFactory = JacksonConverterFactory.create(mapper)
    }

    private var mapper: ObjectMapper? = null
    private var jacksonConverterFactory: JacksonConverterFactory? = null

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *> {
        val javaType: JavaType = mapper!!.typeFactory.constructType(type)
        val reader: ObjectReader? = mapper!!.readerFor(javaType)
        return VersionAwareResponseBodyConverter<Any>(notifier, reader!!)
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        return jacksonConverterFactory?.requestBodyConverter(
            type,
            parameterAnnotations,
            methodAnnotations,
            retrofit
        )
    }

    companion object {
        @JvmOverloads // Guarding public API nullability.
        fun create(
            notifier: (SubsonicAPIVersions) -> Unit = {},
            mapper: ObjectMapper? = ObjectMapper()
        ): VersionAwareJacksonConverterFactory {
            if (mapper == null) throw NullPointerException("mapper == null")
            return VersionAwareJacksonConverterFactory(notifier, mapper)
        }
    }

    class VersionAwareResponseBodyConverter<T>(
        private val notifier: (SubsonicAPIVersions) -> Unit = {},
        private val adapter: ObjectReader
    ) : Converter<ResponseBody, T> {
        override fun convert(value: ResponseBody): T {
            value.use {
                // Read the raw JSON string from the response body
                val rawJson = value.string()

                // Sanitize: remove control characters (e.g., ESC, NULL, etc.)
                val sanitized = rawJson.replace(Regex("[\\x00-\\x1F\\x7F]"), "")

                // Wrap the sanitized string as a new ResponseBody for Jackson to parse
                val cleanedBody = sanitized.toResponseBody("application/json".toMediaTypeOrNull())

                // Parse the sanitized JSON using the configured Jackson ObjectReader
                val response: T = adapter.readValue(cleanedBody.charStream())

                // Notify API version if applicable
                if (response is SubsonicResponse) {
                    try {
                        notifier(response.version)
                    } catch (ignored: IllegalArgumentException) {
                        // Ignore unknown or malformed version values
                    }
                }

                return response
            }
        }
    }
}
