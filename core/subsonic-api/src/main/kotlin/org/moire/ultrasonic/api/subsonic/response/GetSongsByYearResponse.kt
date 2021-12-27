package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsByYearResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songsByYear") private val songsByYearList = SongsByYearWrapper()

    val songsList get() = songsByYearList.songsList
}

internal class SongsByYearWrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
