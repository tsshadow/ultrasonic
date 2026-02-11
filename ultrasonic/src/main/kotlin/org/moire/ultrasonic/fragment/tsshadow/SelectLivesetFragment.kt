package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle

/**
 * Fragment for selecting or saving tile presets for long-form content like livesets.
 * Filters by genre, year, rating, sort method, and optionally festival.
 */
class SelectLivesetFragment : SelectFragment() {
    override val pageKey = "liveset"
    override val defaultLength = "long"
    override val filterModalType = FilterModalType.LIVESET

    override fun setTitle() {
        setTitle(this, R.string.main_livesets_title)
    }

    override fun defaultTileSet(): MutableList<TileInfo> = mutableListOf(
        TileInfo(
            genre = listOf("Euphoric Hardstyle"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        ),
        TileInfo(
            genre = listOf("Hardstyle"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        ),
        TileInfo(
            genre = listOf("Mainstream Hardstyle"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        ),
        TileInfo(
            genre = listOf("Raw Hardstyle"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        ),
        TileInfo(
            genre = listOf("Hardcore"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        ),
        TileInfo(
            genre = listOf("Mainstream Hardcore"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        ),
        TileInfo(
            genre = listOf("Uptempo Hardcore"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        ),
        TileInfo(
            genre = listOf("Bouncy Uptempo"),
            length = defaultLength,
            sortMethod = "DateDescAndRelease",
            favorite = true
        )
    )
}
