/*
 * SelectFragment.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import createTileView
import TileInfo
import TileStorage.loadTiles
import TileStorage.saveTiles
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
import org.moire.ultrasonic.fragment.FragmentTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.toastingExceptionHandler

abstract class SelectFragment : Fragment(), RefreshableFragment {
    private lateinit var gridLayout: GridLayout

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
    private lateinit var toggleFiltersButton: Button
    private lateinit var filterContainer: View

    private val genreList = arrayListOf("All")
    private val yearList = arrayListOf("All")
    private var tiles = mutableListOf<TileInfo>()

    protected open val additionalSpinnerIds: List<Int> = emptyList()
    protected open val additionalFilterLists: MutableList<MutableList<String>> = mutableListOf()

    private var filtersVisible = false

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
                sortMethod = sortMethodSpinner.selectedItem as String
            )
            findNavController().navigate(action)
        }
    }
    protected open fun createTileInfoFromFilters(): TileInfo {
        val genre = getSelectedOrNull(genreSpinner)
        val year = getSelectedOrNull(yearSpinner)
        val sortMethod = sortMethodSpinner.selectedItem as? String ?: ""

        // Initialize optional fields as null
        var label: String? = null
        var festival: String? = null

        // Handle dynamic additional fields by ID
        additionalSpinnerIds.forEach { id ->
            val value = getSelectedOrNull(requireView().findViewById(id))
            when (id) {
                R.id.select_label -> label = value
                R.id.select_festival -> festival = value
                // You can easily extend this
            }
        }

        return TileInfo(
            genre = genre,
            year = year,
            label = label,
            festival = festival,
            sortMethod = sortMethod,
            length = defaultLength,
            ratingMin = ratingMinSpinner.selectedItem as Int,
            ratingMax = ratingMaxSpinner.selectedItem as Int
        )
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val newTile = createTileInfoFromFilters()

            tiles.add(newTile)
            saveTiles(requireContext(), tiles, pageKey)

            val tileView = createTileView(
                newTile,
                tiles.size - 1,
                requireContext(),
                gridLayout,
                findNavController(),
                tiles,
                pageKey
            )
            gridLayout.addView(tileView)
        }
    }


    protected open fun initializeViews(view: View) {
        gridLayout = view.findViewById(R.id.gridLayoutContainer)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMinSpinner = view.findViewById(R.id.select_rating_min)
        ratingMaxSpinner = view.findViewById(R.id.select_rating_max)
        genreSpinner = view.findViewById(R.id.select_genre)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        searchButton = view.findViewById(R.id.search)
        saveButton = view.findViewById(R.id.save)
        toggleFiltersButton = view.findViewById(R.id.toggle_filters)
        filterContainer = view.findViewById(R.id.filter_container)
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

        val sortMethodMap = mapOf(
            getString(R.string.sort_none) to "None",
            getString(R.string.sort_random) to "Random",
            getString(R.string.sort_added_desc) to "AddedDesc",
            getString(R.string.sort_written_desc) to "LastWrittenDesc"
        )

        val translatedList = sortMethodMap.keys.toList()
        sortMethodSpinner.adapter = createAdapter(translatedList)
    }

    private fun setupFilterToggle(root: View) {
        toggleFiltersButton.setOnClickListener {
            filtersVisible = !filtersVisible
            TransitionManager.beginDelayedTransition(root as ViewGroup, AutoTransition())
            filterContainer.visibility = if (filtersVisible) View.VISIBLE else View.GONE
            toggleFiltersButton.text =
                if (filtersVisible) getString(R.string.hide_filters) else getString(R.string.show_filters)
        }
    }

    private fun populateTiles() {
        tiles.forEachIndexed { index, tile ->
            val tileView = createTileView(
                tile,
                index,
                requireContext(),
                gridLayout,
                findNavController(),
                tiles,
                pageKey
            )
            gridLayout.addView(tileView)
        }
    }

    protected open fun loadTilesOrDefaults() {
        tiles = loadTiles(requireContext(), pageKey).toMutableList()
        if (tiles.isEmpty()) {
            tiles = defaultTileSet()
            saveTiles(requireContext(), tiles, pageKey)
        }
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
}
