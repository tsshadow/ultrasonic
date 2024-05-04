/*
 * SelectYearFragment.kt
 * Copyright (C) 2009-2024 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

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
import org.moire.ultrasonic.domain.Year
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.YearAdapter
import java.util.Arrays


/**
 * Displays the available years in the media library
 */
class SelectYearFragment : Fragment(), RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private var yearListView: ListView? = null
    private var lengthSpinner: Spinner? = null
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
        return inflater.inflate(R.layout.tsshadow_select_year, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.select_year_refresh)
        yearListView = view.findViewById(R.id.select_year_list)
        ratingMin = view.findViewById(R.id.select_rating_min)
        ratingMax = view.findViewById(R.id.select_rating_max)
        lengthSpinner = view.findViewById(R.id.select_length)
        swipeRefresh?.setOnRefreshListener { load(true) }

        yearListView?.setOnItemClickListener {
                parent: AdapterView<*>, _: View?,
                position: Int, _: Long
            ->
            val year = parent.getItemAtPosition(position) as Year
            val action = NavigationGraphDirections.toTrackCollection(
                yearName = year.name,
                size = maxSongs,
                offset = 0,
                length = lengthSpinner?.getSelectedItem() as String?,
                ratingMin = ratingMin?.getSelectedItem() as Int,
                ratingMax = ratingMax?.getSelectedItem() as Int,
            )
            findNavController().navigate(action)
        }

//        var ratings = arrayOf(0, 1, 2, 3, 4, 5);
//        val ratingAdapter = ArrayAdapter<Int>(requireContext(),android.R.layout.simple_spinner_item, ratings)
//        ratingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//
//
//        var lengths = arrayOf("","short","long");
//        val lengthAdapter = ArrayAdapter<String>(requireContext(),android.R.layout.simple_spinner_item, lengths)
//
//        lengthSpinner?.setAdapter(lengthAdapter)
//        ratingMin?.setAdapter(ratingAdapter)
//        ratingMax?.setAdapter(ratingAdapter)
//        ratingMax?.setSelection(5)

        emptyView = view.findViewById(R.id.select_year_empty)
        registerForContextMenu(yearListView!!)
        setTitle(this, R.string.main_years_title)
        load(false)
    }

    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                musicService.getYears(refresh)
            }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) {
                emptyView?.isVisible = result.isEmpty()
                yearListView?.adapter = YearAdapter(requireContext(), result)
            }
        }
    }
}
