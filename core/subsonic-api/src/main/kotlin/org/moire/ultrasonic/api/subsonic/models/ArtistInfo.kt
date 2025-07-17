package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Additional information for an artist provided by the Subsonic API.
 */
data class ArtistInfo(
    val biography: String? = null,
    val genre: String? = null,
    @JsonProperty("similarArtist") val similarArtists: List<Artist> = emptyList()
)
