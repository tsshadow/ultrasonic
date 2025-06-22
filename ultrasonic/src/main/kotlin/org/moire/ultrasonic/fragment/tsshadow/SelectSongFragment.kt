package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle

/**
 * Fragment for selecting or saving tile presets for regular songs.
 * Allows filtering by genre, year, rating, sort method, and optionally label.
 * Search results or saved tiles navigate to a TrackCollection.
 */
class SelectSongFragment : SelectFragment() {
    override val pageKey = "song"
    override val defaultLength = "short"
    override val filterModalType = FilterModalType.SONG

    override fun setTitle() {
        setTitle(this, R.string.main_songs_title)
    }

    override fun defaultTileSet(): MutableList<TileInfo> {
        return mutableListOf(
            TileInfo(genre = listOf("Euphoric Hardstyle"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
            TileInfo(genre = listOf("Hardstyle"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
            TileInfo(genre = listOf("Mainstream Hardstyle"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
            TileInfo(genre = listOf("Raw Hardstyle"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
            TileInfo(genre = listOf("Hardcore"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
            TileInfo(genre = listOf("Mainstream Hardcore"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
            TileInfo(genre = listOf("Uptempo Hardcore"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
            TileInfo(genre = listOf("Bouncy Uptempo"), length = defaultLength, sortMethod = "DateDescAndRelease", favorite = true),
        )
    }
}
