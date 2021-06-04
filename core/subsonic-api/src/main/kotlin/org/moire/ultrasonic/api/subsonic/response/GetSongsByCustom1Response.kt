package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsByCustom1Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songsByCustom1") private val songsByCustom1List = SongsByCustom1Wrapper()

    val songsList get() = songsByCustom1List.songsList
}

internal class SongsByCustom1Wrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
