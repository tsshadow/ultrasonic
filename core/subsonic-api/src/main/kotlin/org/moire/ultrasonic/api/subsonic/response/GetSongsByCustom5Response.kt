package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsByCustom5Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songsByCustom5") private val songsByCustom5List = SongsByCustom5Wrapper()

    val songsList get() = songsByCustom5List.songsList
}

internal class SongsByCustom5Wrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
