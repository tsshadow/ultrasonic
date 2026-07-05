package org.moire.ultrasonic.fragment.tsshadow

import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.fragment.FilterableFragment
import org.moire.ultrasonic.view.ViewCapabilities
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.util.Settings
import android.graphics.Color
import android.os.Bundle
import android.view.View
import timber.log.Timber

/**
 * Fragment for selecting or saving tile presets for regular songs and livesets.
 * Allows filtering by genre, year, rating, sort method, and optionally label.
 * Search results or saved tiles navigate to a TrackCollection.
 */
class SelectSongFragment : SelectFragment(), FilterableFragment {
    override var pageKey = "song"
    override var defaultLength = ""
    override var filterModalType = FilterModalType.SONG

    override var viewCapabilities: ViewCapabilities = ViewCapabilities(
        supportsGrid = false,
        supportsSetsToggle = true,
        supportedSortOrders = emptyList()
    )

    override val isSetsMode: Boolean get() = Settings.isSetsMode

    override fun setOnSetsToggle(isSets: Boolean) {
        if (isSets && pageKey == "liveset") return
        if (!isSets && pageKey == "song") return

        if (isSets) {
            pageKey = "liveset"
            defaultLength = ""
            filterModalType = FilterModalType.LIVESET
            setTitle(this, R.string.main_livesets_title)
        } else {
            pageKey = "song"
            defaultLength = ""
            filterModalType = FilterModalType.SONG
            setTitle(this, R.string.main_songs_title)
        }
        // Reload tiles and refresh UI
        updateBrandColors()
        loadTilesOrDefaults()
        populateTiles()
        load(false)
    }

    override fun setOrderType(newOrder: SortOrder) {
        // Tiles have their own sort methods, so we don't need to do anything here
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (Settings.isSetsMode) {
            pageKey = "liveset"
            defaultLength = ""
            filterModalType = FilterModalType.LIVESET
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun setTitle() {
        if (pageKey == "song") {
            setTitle(this, R.string.main_songs_title)
        } else {
            setTitle(this, R.string.main_livesets_title)
        }
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
