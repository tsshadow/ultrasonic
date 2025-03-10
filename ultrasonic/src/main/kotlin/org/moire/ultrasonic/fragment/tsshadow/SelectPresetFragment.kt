/*
 * SelectPresetFragment.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler

class SelectPresetFragment : Fragment(), RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private lateinit var gridLayout: GridLayout
    private val colors = listOf(
        Color.parseColor("#000000"),   // Black
        Color.parseColor("#00008B"),   // Dark Blue
        Color.parseColor("#8B0000"),   // Dark Red
        Color.parseColor("#006400"),   // Dark Green
        Color.parseColor("#4B0082"),   // Dark Purple
        Color.parseColor("#808080"),   // Grey
        Color.parseColor("#2F4F4F"),   // Dark Slate Gray
        Color.parseColor("#A9A9A9"),   // Dark Gray
        Color.parseColor("#800000"),   // Maroon
        Color.parseColor("#2C3E50"),   // Midnight Blue
        Color.parseColor("#8B4513"),   // Saddle Brown
        Color.parseColor("#3B3B3B"),   // Charcoal
        Color.parseColor("#556B2F"),   // Dark Olive Green
        Color.parseColor("#2F4F4F"),   // Dark Sea Green
        Color.parseColor("#D2691E"),   // Chocolate
        Color.parseColor("#B22222"),   // Firebrick
        Color.parseColor("#4C4C4C"),   // Gunmetal
        Color.parseColor("#3A3A3A"),   // Outer Space
        Color.parseColor("#4E4E4E"),   // Onyx
        Color.parseColor("#6A5ACD")    // Slate Blue
    )


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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        swipeRefresh?.setOnRefreshListener { load(true) }

        gridLayout = view.findViewById(R.id.gridLayoutContainer)

        // List of tile data
        val genreTiles = listOf(
            TileInfo("Recent Songs"),
            TileInfo("Random Songs", sortMethod = "Random"),
            TileInfo("Recent Livesets", length = "long"),
            TileInfo("Random Livesets", sortMethod = "Random", length = "long"),
            TileInfo("Hardstyle", genre = "Hardstyle", year = "2025"),
            TileInfo("Mainstream Hardstyle", genre = "Mainstream Hardstyle", year = "2025"),
            TileInfo("Raw Hardstyle", genre = "Raw Hardstyle", year = "2025"),
            TileInfo("Hardcore", genre = "Hardcore", year = "2025"),
            TileInfo("Mainstream Hardcore", genre = "Mainstream Hardcore", year = "2025"),
            TileInfo("Industrial Hardcore", genre = "Raw Hardstyle", year = "2025"),
            TileInfo("Uptempo Hardcore", genre = "Uptempo Hardcore", year = "2025"),
            TileInfo("Bouncy Uptempo", genre = "Bouncy Uptempo", year = "2025"),
            TileInfo("Terror", genre = "Terror", year = "2025")
        )


        // Dynamically create and add tiles to GridLayout
        genreTiles.forEachIndexed { index, tile ->
            val tileView = createTileView(tile, index)
            gridLayout.addView(tileView)
        }

        setTitle(this, "Presets")
        load(false)
    }

    private fun createTileView(tile: TileInfo, index: Int): View {
        // You can use an existing layout file or create a new one
        val tileView = LayoutInflater.from(context).inflate(R.layout.tile_layout, gridLayout, false)

        val tileCard = tileView.findViewById<ConstraintLayout>(R.id.tile_card)
        tileCard.setBackgroundColor(
            colors[index % colors.size]
        )

        // Set the properties for the tile (you could use a TextView, ImageView, etc.)
        val tileTextView = tileView.findViewById<TextView>(R.id.tile_text)
        tileTextView.text = tile.title

        // Set click listener
        tileView.setOnClickListener {
            navigateToGenre(tile)
        }

        return tileView
    }

    private fun navigateToGenre(tile: TileInfo) {
        val action = NavigationGraphDirections.toTrackCollection(
            getSongsName = tile.title,
            genreName = tile.genre,
            size = tile.size,
            offset = tile.offset,
            year = tile.year,
            length = tile.length,
            ratingMin = tile.ratingMin,
            ratingMax = tile.ratingMax,
            sortMethod = tile.sortMethod
        )
        findNavController().navigate(action)
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

    // Data class to store tile settings
    data class TileInfo(
        val title: String,
        val genre: String? = null,
        val year: String? = null,
        val size: Int = maxSongs,
        val offset: Int = 0,
        val length: String = "short",
        val ratingMin: Int = 0,
        val ratingMax: Int = 5,
        val sortMethod: String = "AddedDesc"
    )
}
