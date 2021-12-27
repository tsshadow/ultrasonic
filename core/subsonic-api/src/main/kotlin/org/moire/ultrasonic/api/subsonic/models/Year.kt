package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Year(
    val songCount: Int = 0,
    val albumCount: Int = 0,
    @JsonProperty("value") val name: String
)
