/*
 * SelectSongFragment.kt
 * Copyright (C) 2009-2024 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridLayout
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import createTileView
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

/**
 * Advanced search fragment, enables searching for songs with multiple parameters
 */
class SelectSongFragment : Fragment(), RefreshableFragment {
    private lateinit var gridLayout: GridLayout
    override var swipeRefresh: SwipeRefreshLayout? = null

    private var yearSpinner: Spinner? = null
    private var yearList = ArrayList<String>()

    private var ratingMin: Spinner? = null
    private var ratingMax: Spinner? = null


    private var genreSpinner: Spinner? = null
    private var genreList = ArrayList<String>()

    private var sortMethodSpinner: Spinner? = null

    private var searchButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.tsshadow_select_song, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMin = view.findViewById(R.id.select_rating_min)
        ratingMax = view.findViewById(R.id.select_rating_max)
        genreSpinner = view.findViewById(R.id.select_genre)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        searchButton = view.findViewById(R.id.search)
        swipeRefresh?.setOnRefreshListener { load(true) }

        searchButton?.setOnClickListener {
            val genre = genreSpinner?.selectedItem as String
            val action = NavigationGraphDirections.toTrackCollection(
                songs = "?",
                genre = if (genre != "") genre else null,
                size = maxSongs,
                offset = 0,
                year = yearSpinner?.selectedItem as String,
                length = "short",
                ratingMin = ratingMin?.selectedItem as Int,
                ratingMax = ratingMax?.selectedItem as Int,
                sortMethod = sortMethodSpinner?.selectedItem as String
            )
            findNavController().navigate(action)
        }

        val ratingAdapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                arrayOf(0, 1, 2, 3, 4, 5)
            )
        ratingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        yearList = arrayListOf("All")
        val yearAdapter =
            ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, yearList)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Initialize empty genres list
        genreList = arrayListOf("")
        val genreAdapter =
            ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, genreList)
        genreAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val sortMethodAdapter =
            ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                arrayListOf(
                    "None",
                    "Id",
                    "Random",
                    "AddedDesc",
                    "LastWrittenDesc",
                    "StarredDateDesc",
                    "Name",
                    "DateDescAndRelease",
                    "Release",
                    "TrackList"
                )
            )
        // Set Adapters
        yearSpinner?.setAdapter(yearAdapter)
        ratingMin?.setAdapter(ratingAdapter)
        ratingMax?.setAdapter(ratingAdapter)
        ratingMax?.setSelection(5)
        genreSpinner?.setAdapter(genreAdapter)
        sortMethodSpinner?.setAdapter(sortMethodAdapter)


        // TILES
        gridLayout = view.findViewById(R.id.gridLayoutContainer)

        val currentYear = Year.now().value

        val genreTiles = listOf(
            TileInfo(
                "Euphoric Hardstyle",
                genre = "Euphoric Hardstyle",
                length = "short",
                year = "$currentYear"
            ),
            TileInfo("Hardstyle", genre = "Hardstyle", length = "short", sortMethod = "Random"),
            TileInfo("Hardstyle", genre = "Hardstyle", length = "short", year = "$currentYear"),
            TileInfo(
                "Hardstyle Classics",
                genre = "Hardstyle Classics",
                length = "short",
            ),
            TileInfo(
                "Mainstream Hardstyle",
                genre = "Mainstream Hardstyle",
                length = "short",
                year = "$currentYear"
            ),
            TileInfo(
                "Raw Hardstyle",
                genre = "Raw Hardstyle",
                length = "short",
                year = "$currentYear"
            ),
            TileInfo("Hardcore", genre = "Hardcore", length = "short", sortMethod = "Random"),
            TileInfo("Hardcore", genre = "Hardcore", length = "short", year = "$currentYear"),
            TileInfo(
                "Mainstream Hardcore",
                genre = "Mainstream Hardcore",
                length = "short",
                year = "$currentYear"
            ),
            TileInfo(
                "Industrial Hardcore",
                genre = "Industrial Hardcore",
                length = "short",
            ),
            TileInfo(
                "Uptempo Hardcore",
                genre = "Uptempo Hardcore",
                length = "short",
                year = "$currentYear"
            ),
            TileInfo(
                "Bouncy Uptempo",
                genre = "Bouncy Uptempo",
                length = "short",
                year = "$currentYear"
            ),
            TileInfo("Zaagtempo", genre = "Zaagtempo", length = "short", year = "$currentYear"),
        )


        // Dynamically create and add tiles to GridLayout
        genreTiles.forEachIndexed { index, tile ->
            val tileView =
                createTileView(tile, index, requireContext(), gridLayout, findNavController())
            gridLayout.addView(tileView)
        }

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        gridLayout.columnCount = if (isLandscape) 5 else 3

        setTitle(this, R.string.main_songs_title)
        load(false)
    }

    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val genres = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
//                val yr = yearSpinner?.getSelectedItem() as String;

                musicService.getGenres(refresh, null, null)
            }
            for (genre in genres) {
                genreList.add(genre.name)
            }


            var years = withContext(Dispatchers.IO) {
                val musicService = getMusicService()

                musicService.getTags(refresh, "YEAR", null, null)
            }
            years = years.sortedByDescending { it.name }

            for (year in years) {
                yearList.add(year.name)
            }
        }
    }
}
