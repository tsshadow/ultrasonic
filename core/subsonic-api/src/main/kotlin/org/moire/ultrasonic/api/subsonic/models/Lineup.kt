package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Lineup(
    @JsonProperty("name") val name: String
)
