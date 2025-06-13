package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Genre

/**
 * Response voor de getGenres Subsonic API-call.
 *
 * Bevat een lijst van genres, m.b.v. een wrapper object zoals gespecificeerd in het Subsonic JSON-schema.
 */
class GenresResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    @JsonProperty("genres") private val genresWrapper: GenresWrapper = GenresWrapper()
) : SubsonicResponse(status, version, error) {

    val genresList: List<Genre> get() = genresWrapper.genresList
}

data class GenresWrapper(
    @JsonProperty("genre")
    val genresList: List<Genre> = emptyList()
)
