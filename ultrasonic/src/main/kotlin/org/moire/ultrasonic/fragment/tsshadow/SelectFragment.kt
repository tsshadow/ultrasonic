package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
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
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.MultiSpinnerView
import org.moire.ultrasonic.view.StarRatingRangeView
import timber.log.Timber

abstract class SelectFragment : Fragment(), RefreshableFragment, TileAdapterCallback {
    private lateinit var recyclerView: RecyclerView
    private lateinit var tileAdapter: TileAdapter
    protected abstract val pageKey: String
    protected abstract val defaultLength: String

    override var swipeRefresh: SwipeRefreshLayout? = null

    private lateinit var titleInput: EditText
    private lateinit var yearSpinner: MultiSpinnerView
    private lateinit var ratingMinSpinner: Spinner
    private lateinit var ratingMaxSpinner: Spinner
    private lateinit var genreSpinner: MultiSpinnerView
    private lateinit var sortMethodSpinner: Spinner
    private lateinit var searchButton: Button
    private lateinit var saveButton: Button
    private lateinit var updateButton: Button
    private lateinit var deleteButton: Button
    private lateinit var toggleFiltersButton: AppCompatImageButton
    private lateinit var filterContainer: View
    private lateinit var closeButton: View

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
        initializeFilters()
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

    protected open fun createFilters(): Filters {
        val filters = Filters()

        if (genreSpinner.hasSelection()) {
            val selectedGenres = genreSpinner.getSelectedItems()
                .filter { it.isNotBlank() }
            if (selectedGenres.isNotEmpty()) {
                filters.add(Filter("GENRE", selectedGenres))
            }
        }

        if (yearSpinner.hasSelection()) {
            val selectedYears = yearSpinner.getSelectedItems()
                .filter { it.isNotBlank() }
            if (selectedYears.isNotEmpty()) {
                filters.add(Filter("YEAR", selectedYears))
            }
        }

        val extras = getAdditionalFilterParams()

        extras.label.takeIf { it.isNotEmpty() }
            ?.let { filters.add(Filter("PUBLISHER", it)) }

        extras.festival.takeIf { it.isNotEmpty() }
            ?.let { filters.add(Filter("FESTIVAL", it)) }


        filters.add(Filter("LENGTH", defaultLength))

        return filters
    }


    protected open fun setupSearchButton() {
        searchButton.setOnClickListener {
            val filters = createFilters()
            val action = NavigationGraphDirections.toTrackCollection(
                songs = "?",
                filters = Gson().toJson(filters),
                size = maxSongs,
                offset = 0,
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
            val value = requireView().findViewById<Spinner>(id).selectedItem as? String
            when (id) {
                R.id.select_label -> label = value
                R.id.select_festival -> festival = value
            }
        }
        var customTitle = titleInput.text.toString().takeIf { it.isNotBlank() }
        if (customTitle == null)
            customTitle =""

        return TileInfo(
            title = customTitle,
            genre = genreSpinner.getSelectedItems(),
            year = yearSpinner.getSelectedItems(),
            label = label,
            festival = festival,
            sortMethod = sortMethodMap[sortMethodSpinner.selectedItem as? String ?: ""] ?: "None",
            length = defaultLength,
            ratingMin = ratingMinSpinner.selectedItem as Int,
            ratingMax = ratingMaxSpinner.selectedItem as Int
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val newTile = createTileInfoFromFilters()
            tileAdapter.addTile(newTile)
            hideFilters(requireView())
            showCreateMode()
        }
    }

    private fun showCreateMode() {
        saveButton.visibility = View.VISIBLE
        searchButton.visibility = View.VISIBLE
        updateButton.visibility = View.GONE
        deleteButton.visibility = View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showEditMode(position: Int) {
        saveButton.visibility = View.GONE
        searchButton.visibility = View.GONE
        updateButton.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE

        updateButton.setOnClickListener {
            val updatedTile = createTileInfoFromFilters()
            updateTile(updatedTile, position)
            hideFilters(requireView())
            showCreateMode()
            setupSaveButton()
        }

        deleteButton.setOnClickListener {
            tiles.removeAt(position)
            tileAdapter.notifyItemRemoved(position)
            TileStorage.saveTiles(requireContext(), tiles, pageKey)
            hideFilters(requireView())
            showCreateMode()
            setupSaveButton()
        }
    }

    protected open fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.tileRecyclerView)
        titleInput = view.findViewById(R.id.select_title)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMinSpinner = view.findViewById(R.id.select_rating_min)
        ratingMaxSpinner = view.findViewById(R.id.select_rating_max)
        genreSpinner = view.findViewById(R.id.select_genre)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        searchButton = view.findViewById(R.id.search)
        saveButton = view.findViewById(R.id.save)
        updateButton = view.findViewById(R.id.update)
        deleteButton = view.findViewById(R.id.delete)
        toggleFiltersButton = view.findViewById(R.id.show_filters)
        filterContainer = view.findViewById(R.id.filter_container)
        closeButton = view.findViewById(R.id.close_button)
    }

    protected fun initializeFilters() {
        fun <T> createAdapter(items: List<T>): ArrayAdapter<T> {
            return ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                items
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showFilters(root: View) {
        filtersVisible = true
        TransitionManager.beginDelayedTransition(root as ViewGroup, AutoTransition())
        filterContainer.visibility = View.VISIBLE
        toggleFiltersButton.tooltipText = getString(R.string.hide_filters)
        toggleFiltersButton.visibility = View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
            pageKey = pageKey,
            callback = this
        )
        recyclerView.adapter = tileAdapter
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onEditTile(tile: TileInfo, position: Int) {
        populateFiltersWithTile(tile)
        showFilters(requireView())
        showEditMode(position)
    }

    private fun updateTile(updatedTile: TileInfo, position: Int) {
        tiles[position] = updatedTile
        TileStorage.saveTiles(requireContext(), tiles, pageKey)
        tileAdapter.notifyItemChanged(position)
    }

    private fun populateFiltersWithTile(tile: TileInfo) {
        titleInput.setText(tile.title)

        genreSpinner.setSelectedItems(tile.genre ?: emptyList())
        yearSpinner.setSelectedItems(tile.year ?: emptyList())

        ratingMaxSpinner.setSelection(tile.ratingMax)
        ratingMinSpinner.setSelection(tile.ratingMin)

        sortMethodSpinner.setSelection(
            sortMethodMap.values.indexOf(tile.sortMethod).coerceAtLeast(0)
        )

        additionalSpinnerIds.forEach { id ->
            val spinner = requireView().findViewById<Spinner>(id)
            when (id) {
                R.id.select_label -> spinner.setSelection(getSpinnerIndex(spinner, tile.label))
                R.id.select_festival -> spinner.setSelection(getSpinnerIndex(spinner, tile.festival))
            }
        }
    }

    private fun getSpinnerIndex(spinner: Spinner, value: String?): Int {
        val adapter = spinner.adapter as ArrayAdapter<String>
        return adapter.getPosition(value ?: "All").coerceAtLeast(0)
    }


    protected open fun loadTilesOrDefaults() {
        try {
            Timber.d("Trying to load tiles for $pageKey")
            tiles = TileStorage.loadTiles(requireContext(), pageKey).toMutableList()
            if (tiles.isEmpty()) {
                Timber.w("No saved tiles found, loading defaults.")
                tiles = defaultTileSet()
                TileStorage.saveTiles(requireContext(), tiles, pageKey)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load saved tiles, falling back to defaults.")
            tiles = defaultTileSet()
            TileStorage.saveTiles(requireContext(), tiles, pageKey)
        }
        Timber.d("Loaded tiles: $tiles")
    }

    protected open fun defaultTileSet(): MutableList<TileInfo> = mutableListOf()

    protected open fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val musicService = getMusicService()

            val genres = withContext(Dispatchers.IO) {
                musicService.getGenres(refresh, null, null)
            }
            genreSpinner.setItems(genres.map { it.name })

            val years = withContext(Dispatchers.IO) {
                musicService.getTags(refresh, "YEAR", null, null)
            }.sortedByDescending { it.name }
            yearSpinner.setItems(years.map { it.name })
        }
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