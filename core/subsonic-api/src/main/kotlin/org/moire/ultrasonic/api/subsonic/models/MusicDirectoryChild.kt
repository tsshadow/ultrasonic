package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class MusicDirectoryChild(
    val id: String = "",
    val parent: String = "",
    val isDir: Boolean = false,
    val title: String = "",
    val album: String = "",
    val artist: String = "",
    val track: Int = -1,
    val year: Int? = null,
    val genre: String = "",
    @JsonProperty("genres") val genresValue: List<ApiGenre> = emptyList(),
    val date: String = "",
    val coverArt: String = "",
    val size: Long = -1,
    val contentType: String = "",
    val suffix: String = "",
    val transcodedContentType: String = "",
    val transcodedSuffix: String = "",
    val duration: Int = -1,
    val bitRate: Int = -1,
    val path: String = "",
    val isVideo: Boolean = false,
    val playCount: Int = 0,
    val discNumber: Int = -1,
    val created: Calendar? = null,
    val albumId: String = "",
    val artistId: String = "",
    val type: String = "",
    val starred: Calendar? = null,
    val streamId: String = "",
    val channelId: String = "",
    val description: String = "",
    val status: String = "",
    val publishDate: Calendar? = null,
    val userRating: Int? = null,
    val averageRating: Float? = null
)

class ApiGenre {
    @JsonProperty("name")
    val name: String = ""
}

/** List of genre names from the API. */
val MusicDirectoryChild.genreNames: List<String>
    get() = genresValue.map { it.name }

/**
 * Genre string used by the player. Joins [genreNames] when present or falls back to
 * the single [genre] field.
 */
val MusicDirectoryChild.combinedGenre: String
    get() = if (genreNames.isNotEmpty()) genreNames.joinToString() else genre
