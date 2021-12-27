package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Custom4

class Custom4Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("custom4") private val custom4Wrapper = Custom4Wrapper()
    val custom4List: List<Custom4> get() = custom4Wrapper.custom4List
}

internal class Custom4Wrapper(@JsonProperty("custom4") val custom4List: List<Custom4> = emptyList())
