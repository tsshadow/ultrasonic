package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Custom5

class Custom5Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("custom5") private val custom5Wrapper = Custom5Wrapper()
    val custom5List: List<Custom5> get() = custom5Wrapper.custom5List
}

internal class Custom5Wrapper(@JsonProperty("custom5") val custom5List: List<Custom5> = emptyList())
