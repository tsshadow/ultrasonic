package org.moire.ultrasonic.fragment.tsshadow

import FilterOptionsViewModel
import FilterState
import TileInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Tag
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler
import timber.log.Timber

abstract class SelectFragment :
    Fragment(),
    RefreshableFragment,
    TileAdapterCallback {
    private val filterOptionsViewModel: FilterOptionsViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var tileAdapter: TileAdapter

    protected abstract val pageKey: String
    protected abstract val defaultLength: String
    protected abstract val filterModalType: FilterModalType

    override var swipeRefresh: SwipeRefreshLayout? = null

    private lateinit var toggleFiltersButton: ImageButton

    private var tiles = mutableListOf<TileInfo>()
    private var lastEditedTilePosition: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.tsshadow_search, container, false)

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
                BundleCompat.getParcelable(bundle, "filters", FilterState::class.java)
                    ?: return@setFragmentResultListener

            val action = bundle.getString("action") ?: return@setFragmentResultListener

            when (action) {
                "search" -> {
                    val filters = Filters().apply {
                        if (filterState.genres.isNotEmpty()) {
                            add(Filter("GENRE", filterState.genres))
                        }
                        if (filterState.artists.isNotEmpty()) {
                            add(Filter("ARTIST", filterState.artists))
                        }
                        if (filterState.years.isNotEmpty()) {
                            add(Filter("YEAR", filterState.years))
                        }
                        if (filterState.label.isNotEmpty()) {
                            add(Filter("PUBLISHER", filterState.label))
                        }
                        if (filterState.festival.isNotEmpty()) {
                            add(Filter("FESTIVAL", filterState.festival))
                        }
                        add(Filter("LENGTH", defaultLength))
                    }

                    val navAction = NavigationGraphDirections.toTrackCollection(
                        songs = "?",
                        filters = Gson().toJson(filters),
                        size = filterState.count,
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

    private fun tileInfoFromFilterState(state: FilterState): TileInfo = TileInfo(
        title = state.title,
        genre = state.genres,
        artists = state.artists,
        year = state.years,
        label = state.label,
        festival = state.festival,
        sortMethod = state.sortMethod,
        length = defaultLength,
        ratingMin = state.ratingMin,
        ratingMax = state.ratingMax,
        size = state.count,
        festivalLineup = state.festivalLineup,
        favorite = state.favorite
    )

    override fun onEditTile(tile: TileInfo, position: Int) {
        lastEditedTilePosition = position
        val filterState = FilterState(
            title = tile.title,
            genres = tile.genre ?: emptyList(),
            artists = tile.artists ?: emptyList(),
            years = tile.year ?: emptyList(),
            label = tile.label ?: emptyList(),
            festivalLineup = tile.festivalLineup,
            festival = tile.festival ?: emptyList(),
            sortMethod = tile.sortMethod,
            ratingMin = tile.ratingMin,
            ratingMax = tile.ratingMax,
            count = tile.size,
            favorite = tile.favorite
        )
        val modal = FilterModalFragment.newInstance(filterState, true, filterModalType)
        modal.show(childFragmentManager, "FilterModal")
    }

    open fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.tileRecyclerView)
        toggleFiltersButton = view.findViewById(R.id.show_filters)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
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
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
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
            }.let { list ->
                if (isOffline()) {
                    list.map {
                        it.name
                    }
                } else {
                    list.filter { it.songCount > 10 }.map { it.name }
                }
            }

            val years = withContext(Dispatchers.IO) {
                if (isOffline()) {
                    musicService.getYears(refresh).map { year ->
                        Tag(
                            index = year.index,
                            name = year.name,
                            songCount = 0 // default value, since Year has no songCount
                        )
                    }
                } else {
                    musicService.getTags(refresh, "YEAR", null, null)
                }
            }.map { it.name }.sortedDescending()

            val labels = withContext(Dispatchers.IO) {
                musicService.getTags(refresh, "PUBLISHER", null, null)
            }.map { it.name }.sorted()

            val festivals = withContext(Dispatchers.IO) {
                musicService.getTags(refresh, "FESTIVAL", null, null)
            }.map { it.name }.sorted()

            val lineups = withContext(Dispatchers.IO) {
                musicService.getLineups(refresh)
            }.map { it.name }.sorted()

            val artists = withContext(Dispatchers.IO) {
                musicService.getArtists(refresh, null, null)
            }.mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)

            filterOptionsViewModel.genres.postValue(genres)
            filterOptionsViewModel.years.postValue(years)
            filterOptionsViewModel.labels.postValue(labels)
            filterOptionsViewModel.festivals.postValue(festivals)
            filterOptionsViewModel.lineups.postValue(lineups)
            filterOptionsViewModel.artists.postValue(artists)
            Timber.d(
                "Filter data loaded: genres=${genres.size}, years=${years.size}, " +
                    "labels=${labels.size}, festivals=${festivals.size}, artists=${artists.size}"
            )
        }
        swipeRefresh?.isRefreshing = false
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return (screenWidthDp / DESIRED_TILE_WIDTH_DP).toInt().coerceAtLeast(MIN_SPAN_COUNT)
    }

    private fun updateGridLayoutManager() {
        val spanCount = calculateSpanCount()
        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateGridLayoutManager()
    }

    companion object {
        private const val DESIRED_TILE_WIDTH_DP = 132
        private const val MIN_SPAN_COUNT = 2
    }
}
