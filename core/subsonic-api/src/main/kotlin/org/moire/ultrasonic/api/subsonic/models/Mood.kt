package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Mood(
    @JsonProperty("songCount") val songCount: Int = 0,
    @JsonProperty("albumCount") val albumCount: Int = 0,
    @JsonProperty("value") val name: String
)
