/*
 * SelectFragment.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import TileStorage.loadTiles
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.toastingExceptionHandler
import timber.log.Timber

abstract class SelectFragment : Fragment(), RefreshableFragment {
    private lateinit var recyclerView: RecyclerView
    private lateinit var tileAdapter: TileAdapter
    protected abstract val pageKey: String
    protected abstract val defaultLength: String

    override var swipeRefresh: SwipeRefreshLayout? = null

    private lateinit var yearSpinner: Spinner
    private lateinit var ratingMinSpinner: Spinner
    private lateinit var ratingMaxSpinner: Spinner
    private lateinit var genreSpinner: Spinner
    private lateinit var sortMethodSpinner: Spinner
    private lateinit var searchButton: Button
    private lateinit var saveButton: Button
    private lateinit var toggleFiltersButton: AppCompatImageButton
    private lateinit var filterContainer: View
    private lateinit var closeButton: View

    private val genreList = arrayListOf("All")
    private val yearList = arrayListOf("All")
    private var tiles = mutableListOf<TileInfo>()

    protected open val additionalSpinnerIds: List<Int> = emptyList()
    protected open val additionalFilterLists: MutableList<MutableList<String>> = mutableListOf()

    private var filtersVisible = false

    private val sortMethodMap by lazy {
        mapOf(
            getString(R.string.sort_none) to "None",
            getString(R.string.sort_random) to "Random",
            getString(R.string.sort_added_desc) to "AddedDesc",
            getString(R.string.sort_written_desc) to "LastWrittenDesc"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.tsshadow_search, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        initializeSpinners()
        setupFilterToggle(view)
        swipeRefresh?.setOnRefreshListener { load(true) }

        loadTilesOrDefaults()
        populateTiles()
        setTitle()
        setupSearchButton()
        setupSaveButton()

        load(false)
    }


    protected abstract fun setTitle()

    protected open fun getAdditionalFilterParams(): FilterParams {
        return FilterParams()
    }

    protected open fun setupSearchButton() {
        searchButton.setOnClickListener {
            val extras = getAdditionalFilterParams()

            val action = NavigationGraphDirections.toTrackCollection(
                songs = "?",
                genre = getSelectedOrNull(genreSpinner),
                label = extras.label,
                festival = extras.festival,
                size = maxSongs,
                offset = 0,
                year = getSelectedOrNull(yearSpinner),
                length = defaultLength,
                ratingMin = ratingMinSpinner.selectedItem as Int,
                ratingMax = ratingMaxSpinner.selectedItem as Int,
                sortMethod = sortMethodMap[sortMethodSpinner.selectedItem as? String ?: ""] ?: "None"
            )
            findNavController().navigate(action)
        }
    }

    protected open fun createTileInfoFromFilters(): TileInfo {
        var label: String? = null
        var festival: String? = null

        additionalSpinnerIds.forEach { id ->
            val value = getSelectedOrNull(requireView().findViewById(id))
            when (id) {
                R.id.select_label -> label = value
                R.id.select_festival -> festival = value
            }
        }

        return TileInfo(
            genre = getSelectedOrNull(genreSpinner),
            year = getSelectedOrNull(yearSpinner),
            label = label,
            festival = festival,
            sortMethod = sortMethodMap[sortMethodSpinner.selectedItem as? String ?: ""] ?: "None",
            length = defaultLength,
            ratingMin = ratingMinSpinner.selectedItem as Int,
            ratingMax = ratingMaxSpinner.selectedItem as Int
        )
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val newTile = createTileInfoFromFilters()
            tileAdapter.addTile(newTile)
            hideFilters(requireView())
        }
    }

    protected open fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.tileRecyclerView)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMinSpinner = view.findViewById(R.id.select_rating_min)
        ratingMaxSpinner = view.findViewById(R.id.select_rating_max)
        genreSpinner = view.findViewById(R.id.select_genre)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        searchButton = view.findViewById(R.id.search)
        saveButton = view.findViewById(R.id.save)
        toggleFiltersButton = view.findViewById(R.id.show_filters)
        filterContainer = view.findViewById(R.id.filter_container)
        closeButton = view.findViewById(R.id.close_button)
    }

    protected fun initializeSpinners() {
        fun <T> createAdapter(items: List<T>): ArrayAdapter<T> {
            return ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                items
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        yearSpinner.adapter = createAdapter(yearList)
        genreSpinner.adapter = createAdapter(genreList)
        ratingMinSpinner.adapter = createAdapter((0..5).toList())
        ratingMaxSpinner.adapter = createAdapter((0..5).toList())
        ratingMaxSpinner.setSelection(5)

        additionalSpinnerIds.forEachIndexed { index, id ->
            val spinner = requireView().findViewById<Spinner>(id)
            val values = additionalFilterLists.getOrNull(index) ?: mutableListOf()
            spinner.adapter = createAdapter(values)
        }

        val translatedList = sortMethodMap.keys.toList()
        sortMethodSpinner.adapter = createAdapter(translatedList)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupFilterToggle(root: View) {
        toggleFiltersButton.setOnClickListener {
            showFilters(root)
        }

        closeButton.setOnClickListener {
            hideFilters(root)
        }
    }

    private fun showFilters(root: View) {
        filtersVisible = true
        TransitionManager.beginDelayedTransition(root as ViewGroup, AutoTransition())
        filterContainer.visibility = View.VISIBLE
        toggleFiltersButton.tooltipText = getString(R.string.hide_filters)
        toggleFiltersButton.visibility = View.GONE
    }

    private fun hideFilters(root: View) {
        filtersVisible = false
        TransitionManager.beginDelayedTransition(root as ViewGroup, AutoTransition())
        filterContainer.visibility = View.GONE
        toggleFiltersButton.tooltipText = getString(R.string.show_filters)
        toggleFiltersButton.visibility = View.VISIBLE
    }

    private fun populateTiles() {
        val spanCount = calculateSpanCount()
        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)

        tileAdapter = TileAdapter(
            tiles = tiles,
            context = requireContext(),
            navController = findNavController(),
            pageKey = pageKey
        )
        recyclerView.adapter = tileAdapter
    }


    protected open fun loadTilesOrDefaults() {
        Timber.e("loadTilesOrDefaults: $tiles")
        tiles = loadTiles(requireContext(), pageKey).toMutableList()
        if (tiles.isEmpty()) {
            tiles = defaultTileSet()
            TileStorage.saveTiles(requireContext(), tiles, pageKey)
        }
        Timber.e("loadTilesOrDefaults: $tiles")
    }

    protected open fun defaultTileSet(): MutableList<TileInfo> = mutableListOf()

    protected open fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val musicService = getMusicService()

            if (genreList.size == 1 && genreList[0] == "All") {
                val genres = withContext(Dispatchers.IO) {
                    musicService.getGenres(refresh, null, null)
                }
                genreList.addAll(genres.map { it.name })
            }

            if (yearList.size == 1 && yearList[0] == "All") {
                val years = withContext(Dispatchers.IO) {
                    musicService.getTags(refresh, "YEAR", null, null)
                }.sortedByDescending { it.name }
                yearList.addAll(years.map { it.name })
            }
        }
    }

    protected fun getSelectedOrNull(spinner: Spinner): String? {
        val value = spinner.selectedItem as? String ?: return null
        return if (value == "All") null else value
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val desiredTileWidthDp = 120 + 12  // tile width + margin
        return (screenWidthDp / desiredTileWidthDp).toInt().coerceAtLeast(2)
    }

    private fun updateGridLayoutManager() {
        val spanCount = calculateSpanCount()
        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateGridLayoutManager()
    }

}
