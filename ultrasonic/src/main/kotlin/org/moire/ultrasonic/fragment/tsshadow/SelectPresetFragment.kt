/*
 * SelectPresetFragment.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

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
import androidx.constraintlayout.widget.ConstraintLayout
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
import java.time.Year

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
            TileInfo("Euphoric Hardstyle", genre = "Euphoric Hardstyle", year = "$currentYear"),
            TileInfo("Hardstyle", genre = "Hardstyle", year = "$currentYear"),
            TileInfo("Hardstyle Classics", genre = "Hardstyle Classics", year = "$currentYear"),
            TileInfo("Mainstream Hardstyle", genre = "Mainstream Hardstyle", year = "$currentYear"),
            TileInfo("Raw Hardstyle", genre = "Raw Hardstyle", year = "$currentYear"),
            TileInfo("Hardcore", genre = "Hardcore", year = "$currentYear"),
            TileInfo("Mainstream Hardcore", genre = "Mainstream Hardcore", year = "$currentYear"),
            TileInfo("Millennium Hardcore", genre = "Millennium Hardcore", year = "$currentYear"),
            TileInfo("Industrial Hardcore", genre = "Industrial Hardcore"),
            TileInfo("Uptempo Hardcore", genre = "Uptempo Hardcore", year = "$currentYear"),
            TileInfo("Bouncy Uptempo", genre = "Bouncy Uptempo", year = "$currentYear"),
            TileInfo("Zaagtempo", genre = "Zaagtempo", year = "$currentYear"),
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
        val tileView = LayoutInflater.from(context).inflate(R.layout.tile_layout, gridLayout, false)

        val tileBackground = tileView.findViewById<View>(R.id.tile_card)
        val tileIcon = tileView.findViewById<ImageView>(R.id.tile_icon)
        val tileTextView = tileView.findViewById<TextView>(R.id.tile_text)

        // Assign gradient backgrounds (cycling through a predefined list)
        val backgroundList = listOf(
            R.drawable.tile_background_gradient,  // Default gradient
        )
        val chosenBackground = backgroundList[index % backgroundList.size]
        tileBackground.setBackgroundResource(chosenBackground)

        // Assign icons based on genre
        val iconMap = mapOf(
            // Random and Recent Songs
            "Recent Songs" to R.drawable.baseline_music_note_24,
            "Random Songs" to R.drawable.baseline_music_note_24,
            "Recent Livesets" to R.drawable.baseline_music_note_24,
            "Random Livesets" to R.drawable.baseline_music_note_24,

            // Softer Genres
            "Euphoric Hardstyle" to R.drawable.baseline_emoji_emotions_24,  // Softer, emotional
            "Hardstyle" to R.drawable.baseline_mood_24,  // Regular Hardstyle is softer
            "Mainstream Hardstyle" to R.drawable.baseline_mood_24,  // Mainstream is softer
            "Hardstyle Classics" to R.drawable.baseline_headset_24,  // Classic hardstyle is more chill

            // Harder Genres
            "Raw Hardstyle" to R.drawable.baseline_local_fire_department_24,  // Raw = harder
            "Mainstream Hardcore" to R.drawable.baseline_thunderstorm_24,  // Hardcore but mainstream
            "Hardcore" to R.drawable.baseline_bolt_24,  // Standard Hardcore
            "Millennium Hardcore" to R.drawable.baseline_headset_24,  // Old-school hardcore = more chill
            "Industrial Hardcore" to R.drawable.baseline_local_fire_department_24,  // Harder, industrial vibes
            "Uptempo Hardcore" to R.drawable.baseline_thunderstorm_24,  // Fast, aggressive, stormy
            "Bouncy Uptempo" to R.drawable.baseline_emoji_emotions_24,  // Still hard, but fun
            "Zaagtempo" to R.drawable.baseline_bolt_24  // Extreme hardcore, lightning fast
        )

        tileIcon.setImageResource(iconMap[tile.genre] ?: R.drawable.baseline_music_note_24)

        // Set tile text
        tileTextView.text = tile.title

        // Dynamic Gradient Colors
        val colorStart = colors[index % colors.size]  // Pick a dark color from the list
        val colorEnd = Color.BLACK  // Fade into black

        // Create GradientDrawable
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,  // Top-left to Bottom-right
            intArrayOf(colorStart, colorEnd)  // Gradient colors
        )
        gradientDrawable.cornerRadius = 24f  // Smooth corners

        // Apply Gradient Background to Tile
        tileBackground.background = gradientDrawable

        // Set icon from icon map
        val iconRes = iconMap[tile.title] ?: R.drawable.baseline_music_note_24
        tileIcon.setImageResource(iconRes)

        // Set text
        tileTextView.text = tile.title

        // Click event
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
