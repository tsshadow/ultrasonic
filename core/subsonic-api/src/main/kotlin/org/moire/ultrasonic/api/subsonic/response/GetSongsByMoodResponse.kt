package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsByMoodResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songsByMood")
    private val songsByMoodList = SongsByMoodWrapper()

    val songsList get() = songsByMoodList.songsList
}

internal class SongsByMoodWrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
