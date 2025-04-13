/*
 * SelectPresetFragment.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import TileStorage.loadTiles
import TileStorage.saveTiles
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class SelectPresetFragment : Fragment(), RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private lateinit var gridLayout: GridLayout
    private var tiles: MutableList<TileInfo> = mutableListOf<TileInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.tsshadow_preset_page, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.select_genre_refresh)

        gridLayout = view.findViewById(R.id.gridLayoutContainer)

//        val currentYear = Year.now().value


        tiles = loadTiles(requireContext(), "preset");
        if (tiles.size == 0)
        {
            tiles = mutableListOf(
                TileInfo("Recent Songs"),
                TileInfo("Random Songs", sortMethod = "Random"),
                TileInfo("Recent Modified Songs", sortMethod = "LastWrittenDesc", length = "long"),
                TileInfo("Recent Livesets", length = "long"),
                TileInfo("Random Livesets", sortMethod = "Random", length = "long"),
                TileInfo("Recent Modified Livesets", sortMethod = "LastWrittenDesc", length = "long"),
            )
            saveTiles(requireContext(), tiles, "preset")
        }

        // Dynamically create and add tiles to GridLayout
        tiles.forEachIndexed { index, tile ->
            val tileView =
                createTileView(tile, index, requireContext(), gridLayout, findNavController(), tiles, "preset")
            gridLayout.addView(tileView)
        }

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        gridLayout.columnCount = if (isLandscape) 5 else 3

        setTitle(this, "Presets")
    }
}
