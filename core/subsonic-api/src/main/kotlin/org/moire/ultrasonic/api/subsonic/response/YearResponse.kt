package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Year

class YearResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("year") private val yearWrapper = YearWrapper()
    val yearList: List<Year> get() = yearWrapper.yearList
}

internal class YearWrapper(@JsonProperty("year") val yearList: List<Year> = emptyList())
