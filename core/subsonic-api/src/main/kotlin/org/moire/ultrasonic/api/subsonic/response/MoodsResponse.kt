package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Mood

class MoodsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("mood")
    private val moodsWrapper = MoodsWrapper()
    val moodsList: List<Mood> get() = moodsWrapper.moodsList
}

internal class MoodsWrapper(@JsonProperty("mood") val moodsList: List<Mood> = emptyList())
