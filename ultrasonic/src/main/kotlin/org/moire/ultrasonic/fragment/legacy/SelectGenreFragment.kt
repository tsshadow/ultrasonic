/*
 * SelectGenreFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Spinner
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.GenreAdapter
import java.util.Arrays


/**
 * Displays the available genres in the media library
 */
class SelectGenreFragment : Fragment(), RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private var genreListView: ListView? = null
    private var lengthSpinner: Spinner? = null
    private var yearSpinner: Spinner? = null
    private var ratingMin: Spinner? = null
    private var ratingMax: Spinner? = null
    private var emptyView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.select_genre, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        genreListView = view.findViewById(R.id.select_genre_list)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMin = view.findViewById(R.id.select_rating_min)
        ratingMax = view.findViewById(R.id.select_rating_max)
        lengthSpinner = view.findViewById(R.id.select_length)
        swipeRefresh?.setOnRefreshListener { load(true) }

        genreListView?.setOnItemClickListener {
                parent: AdapterView<*>, _: View?,
                position: Int, _: Long
            ->
            val genre = parent.getItemAtPosition(position) as Genre
            val action = NavigationGraphDirections.toTrackCollection(
                genreName = genre.name,
                size = maxSongs,
                offset = 0,
                year = yearSpinner?.getSelectedItem() as Int,
                length = lengthSpinner?.getSelectedItem() as String?,
                ratingMin = ratingMin?.getSelectedItem() as Int,
                ratingMax = ratingMax?.getSelectedItem() as Int,
            )
            findNavController().navigate(action)
        }

        var ratings = arrayOf(0, 1, 2, 3, 4, 5);
        val ratingAdapter = ArrayAdapter<Int>(requireContext(),android.R.layout.simple_spinner_item, ratings)
        ratingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)


        var years = arrayOf(0,2024, 2023, 2022, 2021, 2020, 2019, 2018, 2017, 2016, 2015, 2014, 2013, 2012, 2011, 2010, 2009, 2008, 2007, 2006, 2005, 2004, 2003, 2002, 2001, 2000, 1999, 1998, 1997, 1996, 1995, 1994, 1993, 1992);
        val yearAdapter = ArrayAdapter<Int>(requireContext(),android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        var lengths = arrayOf("","short","long");
        val lengthAdapter = ArrayAdapter<String>(requireContext(),android.R.layout.simple_spinner_item, lengths)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        yearSpinner?.setAdapter(yearAdapter)
        lengthSpinner?.setAdapter(lengthAdapter)
        ratingMin?.setAdapter(ratingAdapter)
        ratingMax?.setAdapter(ratingAdapter)
        ratingMax?.setSelection(5)

        emptyView = view.findViewById(R.id.select_genre_empty)
        registerForContextMenu(genreListView!!)
        setTitle(this, R.string.main_genres_title)
        load(false)
    }

    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                musicService.getGenres(refresh)
            }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) {
                emptyView?.isVisible = result.isEmpty()
                genreListView?.adapter = GenreAdapter(requireContext(), result)
            }
        }
    }
}
