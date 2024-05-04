package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Year

class YearsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("year")
    private val yearsWrapper = YearsWrapper()
    val yearsList: List<Year> get() = yearsWrapper.yearsList
}

internal class YearsWrapper(@JsonProperty("year") val yearsList: List<Year> = emptyList())
