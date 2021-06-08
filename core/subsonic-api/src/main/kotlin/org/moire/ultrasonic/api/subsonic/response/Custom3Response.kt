package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Custom3

class Custom3Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("custom3") private val custom3Wrapper = Custom3Wrapper()
    val custom3List: List<Custom3> get() = custom3Wrapper.custom3List
}

internal class Custom3Wrapper(@JsonProperty("custom3") val custom3List: List<Custom3> = emptyList())
