package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Mood

class MoodResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("mood") private val moodWrapper = MoodWrapper()
    val moodList: List<Mood> get() = moodWrapper.moodList
}

internal class MoodWrapper(@JsonProperty("mood") val moodList: List<Mood> = emptyList())
