package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Custom1

class Custom1Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("custom1") private val custom1Wrapper = Custom1Wrapper()
    val custom1List: List<Custom1> get() = custom1Wrapper.custom1List
}

internal class Custom1Wrapper(@JsonProperty("custom1") val custom1List: List<Custom1> = emptyList())
