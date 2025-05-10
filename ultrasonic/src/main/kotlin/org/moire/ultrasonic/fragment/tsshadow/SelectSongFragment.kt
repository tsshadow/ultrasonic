package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import java.time.Year

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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun defaultTileSet(): MutableList<TileInfo> {
        val currentYear = listOf(Year.now().value.toString())
        return mutableListOf(
            TileInfo(genre = listOf("Euphoric Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Mainstream Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Raw Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Hardcore"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Uptempo Hardcore"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Bouncy Uptempo"), length = defaultLength, year = currentYear)
        )
    }
}
