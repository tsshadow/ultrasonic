package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsByCustom4Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songsByCustom4") private val songsByCustom4List = SongsByCustom4Wrapper()

    val songsList get() = songsByCustom4List.songsList
}

internal class SongsByCustom4Wrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
