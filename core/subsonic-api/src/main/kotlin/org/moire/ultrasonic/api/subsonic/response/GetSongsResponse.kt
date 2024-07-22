package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songs")
    private val list = SongsWrapper()

    val songsList get() = list.songsList
}

internal class SongsWrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
