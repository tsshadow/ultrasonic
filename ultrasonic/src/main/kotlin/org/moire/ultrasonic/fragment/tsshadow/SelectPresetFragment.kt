/*
 * SelectPresetFragment.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.GridLayout
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import createTileView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme

/**
 * Fragment for displaying general purpose search presets (tiles),
 * such as "Recent Songs", "Random Livesets", etc.
 */
class SelectPresetFragment : Fragment(), RefreshableFragment {
    companion object {
        private const val PAGE_KEY = "preset"
    }

    override var swipeRefresh: SwipeRefreshLayout? = null

    private lateinit var gridLayout: GridLayout
    private var tiles: MutableList<TileInfo> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.tsshadow_preset_page, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        gridLayout = view.findViewById(R.id.gridLayoutContainer)

        loadTilesOrDefaults()
        populateTiles()
        adjustGridColumnCount()

        setTitle(this, "Presets")
    }

    /**
     * Load saved tiles or fallback to predefined default preset tiles
     */
    private fun loadTilesOrDefaults() {
        tiles = mutableListOf(
            TileInfo("Recent Songs"),
            TileInfo("Random Songs", sortMethod = "Random"),
            TileInfo(
                "Recent Modified Songs",
                sortMethod = "LastWrittenDesc",
                length = "long"
            ),
            TileInfo("Recent Livesets", length = "long"),
            TileInfo("Random Livesets", sortMethod = "Random", length = "long"),
            TileInfo(
                "Recent Modified Livesets",
                sortMethod = "LastWrittenDesc",
                length = "long"
            )
        )
    }

    /**
     * Dynamically creates and places each tile in the grid
     */
    private fun populateTiles() {
        tiles.forEachIndexed { index, tile ->
            val tileView = createTileView(
                tile,
                index,
                requireContext(),
                gridLayout,
                findNavController(),
                tiles,
                PAGE_KEY
            )
            gridLayout.addView(tileView)
        }
    }

    /**
     * Adjusts column count for orientation
     */
    private fun adjustGridColumnCount() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        gridLayout.columnCount = if (isLandscape) 5 else 3
    }
}
