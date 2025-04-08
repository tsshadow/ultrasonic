/*
 * SelectPresetFragment.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import createTileView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import navigateToGenre
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler
import java.time.Year

class SelectPresetFragment : Fragment(), RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private lateinit var gridLayout: GridLayout

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
        swipeRefresh?.setOnRefreshListener { load(true) }

        gridLayout = view.findViewById(R.id.gridLayoutContainer)

        val currentYear = Year.now().value

        val genreTiles = listOf(
            TileInfo("Recent Songs"),
            TileInfo("Random Songs", sortMethod = "Random"),
            TileInfo("Recent Livesets", length = "long"),
            TileInfo("Random Livesets", sortMethod = "Random", length = "long"),
        )


        // Dynamically create and add tiles to GridLayout
        genreTiles.forEachIndexed { index, tile ->
            val tileView =
                createTileView(tile, index, requireContext(), gridLayout, findNavController())
            gridLayout.addView(tileView)
        }

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        gridLayout.columnCount = if (isLandscape) 5 else 3

        setTitle(this, "Presets")
        load(false)
    }


    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val genres = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                musicService.getGenres(refresh, null, null)
            }
            // Additional logic if needed for fetching genres dynamically
        }
    }


}
