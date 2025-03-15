/*
 * SelectSongFragment.kt
 * Copyright (C) 2009-2024 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
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
import timber.log.Timber

/**
 * Advanced search fragment, enables searching for songs with multiple parameters
 */
class SelectLivesetFragment : Fragment(), RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null

    private var yearSpinner: Spinner? = null
    private var yearList = ArrayList<String>()

    private var ratingMin: Spinner? = null
    private var ratingMax: Spinner? = null

    private var genreSpinner: Spinner? = null
    private var genreList = ArrayList<String>()

    private var festivalSpinner: Spinner? = null
    private var festivalList = ArrayList<String>()

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
        return inflater.inflate(R.layout.tsshadow_select_liveset, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMin = view.findViewById(R.id.select_rating_min)
        ratingMax = view.findViewById(R.id.select_rating_max)
        genreSpinner = view.findViewById(R.id.select_genre)
        festivalSpinner = view.findViewById(R.id.select_festival)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        searchButton = view.findViewById(R.id.search)
        swipeRefresh?.setOnRefreshListener { load(true) }

        searchButton?.setOnClickListener {
            val genre = genreSpinner?.selectedItem as String
            val festival = festivalSpinner?.selectedItem as String
            val action = NavigationGraphDirections.toTrackCollection(
                getSongsName = "getSongs",
                genreName = if (genre != "") genre else null,
                //         festival = festival,
                size = maxSongs,
                offset = 0,
                year = yearSpinner?.selectedItem as String,
                length = "long",
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


        // Initialize empty festivals list
        festivalList = arrayListOf("")
        val festivalAdapter =
            ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                festivalList
            )
        festivalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

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
        festivalSpinner?.setAdapter(festivalAdapter)
        sortMethodSpinner?.setAdapter(sortMethodAdapter)

        setTitle(this, R.string.main_livesets_title)
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

            val festivals = withContext(Dispatchers.IO) {
                val musicService = getMusicService()

                musicService.getTags(refresh, "FESTIVAL", null, null)
            }
            for (festival in festivals) {
                festivalList.add(festival.name)
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
