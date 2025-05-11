package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Lineup

class LineupResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("lineups")
    private val LineupWrapper = LineupWrapper()
    val lineupList: List<Lineup> get() = LineupWrapper.lineupList
}

internal class LineupWrapper(@JsonProperty("lineup") val lineupList: List<Lineup> = emptyList())
