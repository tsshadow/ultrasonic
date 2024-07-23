package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

class Cluster(@JsonProperty("name") val name: String , @JsonProperty("value") val value: String) {
}