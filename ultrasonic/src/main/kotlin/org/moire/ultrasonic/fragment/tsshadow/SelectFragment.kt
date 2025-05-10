package org.moire.ultrasonic.fragment.tsshadow

import FilterState
import TileInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme
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
import timber.log.Timber

abstract class SelectFragment : Fragment(), RefreshableFragment, TileAdapterCallback {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tileAdapter: TileAdapter

    protected abstract val pageKey: String
    protected abstract val defaultLength: String
    protected abstract val filterModalType: FilterModalType

    override var swipeRefresh: SwipeRefreshLayout? = null

    private lateinit var toggleFiltersButton: ImageButton

    private var tiles = mutableListOf<TileInfo>()
    private var lastEditedTilePosition: Int? = null

    protected open val additionalSpinnerIds: List<Int> = emptyList()
    protected open val additionalFilterLists: MutableList<MutableList<String>> = mutableListOf()

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
        setupFilterModalListener()

        toggleFiltersButton.setOnClickListener {
            val modal = FilterModalFragment.newInstance(null, false, filterModalType)
            modal.show(childFragmentManager, "FilterModal")
        }

        swipeRefresh?.setOnRefreshListener { load(true) }

        loadTilesOrDefaults()
        populateTiles()
        setTitle()
        load(false)
    }

    protected abstract fun setTitle()

    private fun setupFilterModalListener() {
        childFragmentManager.setFragmentResultListener(
            "filters_result",
            viewLifecycleOwner
        ) { _, bundle ->
            val filterState =
                bundle.getParcelable<FilterState>("filters") ?: return@setFragmentResultListener
            val action = bundle.getString("action") ?: return@setFragmentResultListener

            when (action) {
                "search" -> {
                    val filters = Filters().apply {
                        if (filterState.genres.isNotEmpty())
                            add(Filter("GENRE", filterState.genres))
                        if (filterState.years.isNotEmpty())
                            add(Filter("YEAR", filterState.years))
                        if (filterState.label.isNotEmpty())
                            add(Filter("PUBLISHER", filterState.label))
                        if (filterState.festival.isNotEmpty())
                            add(Filter("FESTIVAL", filterState.festival))
                        add(Filter("LENGTH", defaultLength))
                    }

                    val navAction = NavigationGraphDirections.toTrackCollection(
                        songs = "?",
                        filters = Gson().toJson(filters),
                        size = maxSongs,
                        offset = 0,
                        length = defaultLength,
                        ratingMin = filterState.ratingMin,
                        ratingMax = filterState.ratingMax,
                        sortMethod = filterState.sortMethod
                    )

                    findNavController().navigate(navAction)
                }

                "save" -> {
                    val tile = tileInfoFromFilterState(filterState)
                    tileAdapter.addTile(tile)
                    TileStorage.saveTiles(requireContext(), tileAdapter.tiles, pageKey)
                }

                "update" -> {
                    lastEditedTilePosition?.let {
                        tiles[it] = tileInfoFromFilterState(filterState)
                        tileAdapter.notifyItemChanged(it)
                        TileStorage.saveTiles(requireContext(), tiles, pageKey)
                        lastEditedTilePosition = null
                    }
                }

                "delete" -> {
                    lastEditedTilePosition?.let {
                        tiles.removeAt(it)
                        tileAdapter.notifyItemRemoved(it)
                        TileStorage.saveTiles(requireContext(), tiles, pageKey)
                        lastEditedTilePosition = null
                    }
                }
            }
        }
    }

    private fun tileInfoFromFilterState(state: FilterState): TileInfo {
        return TileInfo(
            title = state.title,
            genre = state.genres,
            year = state.years,
            label = state.label,
            festival = state.festival,
            sortMethod = state.sortMethod,
            length = defaultLength,
            ratingMin = state.ratingMin,
            ratingMax = state.ratingMax
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onEditTile(tile: TileInfo, position: Int) {
        lastEditedTilePosition = position
        val filterState = FilterState(
            title = tile.title ?: "",
            genres = tile.genre ?: emptyList(),
            years = tile.year ?: emptyList(),
            label = tile.label ?: emptyList(),
            festival = tile.festival ?: emptyList(),
            sortMethod = tile.sortMethod ?: "None",
            ratingMin = tile.ratingMin,
            ratingMax = tile.ratingMax
        )
        val modal = FilterModalFragment.newInstance(filterState, true, filterModalType)
        modal.show(childFragmentManager, "FilterModal")
    }

    open fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.tileRecyclerView)
        toggleFiltersButton = view.findViewById(R.id.show_filters)
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
            // De modal haalt deze nu uit shared ViewModel of vooraf geladen waardes.

            val years = withContext(Dispatchers.IO) {
                musicService.getTags(refresh, "YEAR", null, null)
            }.sortedByDescending { it.name }
            // Idem
        }
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val desiredTileWidthDp = 120 + 12
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
