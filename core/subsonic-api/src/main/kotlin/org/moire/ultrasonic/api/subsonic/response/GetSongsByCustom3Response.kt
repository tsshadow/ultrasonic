package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsByCustom3Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songsByCustom3") private val songsByCustom3List = SongsByCustom3Wrapper()

    val songsList get() = songsByCustom3List.songsList
}

internal class SongsByCustom3Wrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
