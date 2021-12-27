package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Custom2

class Custom2Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("custom2") private val custom2Wrapper = Custom2Wrapper()
    val custom2List: List<Custom2> get() = custom2Wrapper.custom2List
}

internal class Custom2Wrapper(@JsonProperty("custom2") val custom2List: List<Custom2> = emptyList())
