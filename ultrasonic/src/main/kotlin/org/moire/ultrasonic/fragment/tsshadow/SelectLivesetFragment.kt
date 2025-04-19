package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import TileStorage.loadTiles
import TileStorage.saveTiles
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
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
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

/**
 * Fragment for composing and saving advanced filters to explore long-form music content,
 * such as livesets, based on genre, rating, year, sort method, and optionally festival.
 *
 * Users can perform a search or save their filter configuration as reusable "tiles."
 */
class SelectLivesetFragment : Fragment(), RefreshableFragment {

    override var swipeRefresh: SwipeRefreshLayout? = null

    private lateinit var gridLayout: GridLayout
    private lateinit var yearSpinner: Spinner
    private lateinit var ratingMinSpinner: Spinner
    private lateinit var ratingMaxSpinner: Spinner
    private lateinit var genreSpinner: Spinner
    private lateinit var festivalSpinner: Spinner
    private lateinit var sortMethodSpinner: Spinner
    private lateinit var searchButton: Button
    private lateinit var saveButton: Button

    private lateinit var filterContainer: View
    private lateinit var toggleFiltersButton: Button
    private var filtersVisible = false

    private val genreList = arrayListOf("All")
    private val yearList = arrayListOf("All")
    private val festivalList = arrayListOf("All")

    private var tiles = mutableListOf<TileInfo>()

    companion object {
        private const val DEFAULT_LENGTH = "long"
        private const val PAGE_KEY = "liveset"
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
        return inflater.inflate(R.layout.tsshadow_select_liveset, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        initializeSpinners()

        swipeRefresh?.setOnRefreshListener { load(true) }

        // Toggle filter visibility
        toggleFiltersButton.setOnClickListener {
            filtersVisible = !filtersVisible

            // Animate the transition
            TransitionManager.beginDelayedTransition(view as ViewGroup, AutoTransition())

            filterContainer.visibility = if (filtersVisible) View.VISIBLE else View.GONE
            toggleFiltersButton.text = if (filtersVisible) "Hide Filters ▲" else "Show Filters ▼"
        }
        loadTilesOrDefaults()
        populateTiles()
        adjustGridColumnCount()

        setupSearchButton()
        setupSaveButton()

        setTitle(this, R.string.main_livesets_title)
        load(false)
    }

    private fun initializeViews(view: View) {
        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMinSpinner = view.findViewById(R.id.select_rating_min)
        ratingMaxSpinner = view.findViewById(R.id.select_rating_max)
        genreSpinner = view.findViewById(R.id.select_genre)
        festivalSpinner = view.findViewById(R.id.select_festival)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        searchButton = view.findViewById(R.id.search)
        saveButton = view.findViewById(R.id.save)
        gridLayout = view.findViewById(R.id.gridLayoutContainer)
        filterContainer = view.findViewById(R.id.filter_container)
        toggleFiltersButton = view.findViewById(R.id.toggle_filters)
    }

    private fun initializeSpinners() {
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
        festivalSpinner.adapter = createAdapter(festivalList)

        ratingMinSpinner.adapter = createAdapter((0..5).toList())
        ratingMaxSpinner.adapter = createAdapter((0..5).toList())
        ratingMaxSpinner.setSelection(5)

        val sortMethods = listOf(
            "None", "Id", "Random", "AddedDesc", "LastWrittenDesc",
            "StarredDateDesc", "Name", "DateDescAndRelease", "Release", "TrackList"
        )
        sortMethodSpinner.adapter = createAdapter(sortMethods)
    }

    private fun loadTilesOrDefaults() {
        tiles = loadTiles(requireContext(), PAGE_KEY).toMutableList()

        if (tiles.isEmpty()) {
            tiles = mutableListOf(
                TileInfo(
                    "Hardstyle",
                    genre = "Hardstyle",
                    length = DEFAULT_LENGTH,
                    sortMethod = "LastWrittenDesc"
                ),
                TileInfo(
                    "Raw Hardstyle",
                    genre = "Raw Hardstyle",
                    length = DEFAULT_LENGTH,
                    sortMethod = "LastWrittenDesc"
                ),
                TileInfo(
                    "Hardcore",
                    genre = "Hardcore",
                    length = DEFAULT_LENGTH,
                    sortMethod = "LastWrittenDesc"
                ),
                TileInfo(
                    "Mainstream Hardcore",
                    genre = "Mainstream Hardcore",
                    length = DEFAULT_LENGTH,
                    sortMethod = "LastWrittenDesc"
                ),
                TileInfo(
                    "Uptempo Hardcore",
                    genre = "Uptempo Hardcore",
                    length = DEFAULT_LENGTH,
                    sortMethod = "LastWrittenDesc"
                ),
                TileInfo(
                    "Bouncy Uptempo",
                    genre = "Bouncy Uptempo",
                    length = DEFAULT_LENGTH,
                    sortMethod = "LastWrittenDesc"
                )
            )
            saveTiles(requireContext(), tiles, PAGE_KEY)
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
                PAGE_KEY
            )
            gridLayout.addView(tileView)
        }
    }

    private fun adjustGridColumnCount() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        gridLayout.columnCount = if (isLandscape) 5 else 3
    }

    private fun setupSearchButton() {
        searchButton.setOnClickListener {
            val action = NavigationGraphDirections.toTrackCollection(
                songs = "?",
                genre = if (genreSpinner.selectedItem as String == "All") null else genreSpinner.selectedItem as String,
                festival = if (festivalSpinner.selectedItem as String == "All") null else festivalSpinner.selectedItem as String,
                size = maxSongs,
                offset = 0,
                year = if (yearSpinner.selectedItem as String == "All") null else yearSpinner.selectedItem as String,
                length = DEFAULT_LENGTH,
                ratingMin = ratingMinSpinner.selectedItem as Int,
                ratingMax = ratingMaxSpinner.selectedItem as Int,
                sortMethod = sortMethodSpinner.selectedItem as String
            )
            findNavController().navigate(action)
        }
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val genre = genreSpinner.selectedItem as? String ?: return@setOnClickListener
            val year = yearSpinner.selectedItem as? String ?: return@setOnClickListener
            val sortMethod = sortMethodSpinner.selectedItem as? String ?: return@setOnClickListener
            val festival = festivalSpinner.selectedItem as? String

            val newTile = TileInfo(
                title = createTitle(genre, year, sortMethod, festival),
                genre = if (genre == "All") null else genre,
                year = if (year == "All") null else year,
                festival = if (festival == "All") null else festival,
                sortMethod = sortMethod,
                length = DEFAULT_LENGTH,
                ratingMin = ratingMinSpinner.selectedItem as Int,
                ratingMax = ratingMaxSpinner.selectedItem as Int
            )

            tiles.add(newTile)
            saveTiles(requireContext(), tiles, PAGE_KEY)

            val tileView = createTileView(
                newTile,
                tiles.size - 1,
                requireContext(),
                gridLayout,
                findNavController(),
                tiles,
                PAGE_KEY
            )
            gridLayout.addView(tileView)

            Toast.makeText(requireContext(), "Tegel toegevoegd!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val musicService = getMusicService()

            if (genreList.size == 1 && genreList[0] == "All") {
                val genres = withContext(Dispatchers.IO) {
                    musicService.getGenres(refresh, null, null)
                }
                genreList.addAll(genres.map { it.name })
            }

            if (festivalList.size == 1 && festivalList[0] == "All") {
                val festivals = withContext(Dispatchers.IO) {
                    musicService.getTags(refresh, "FESTIVAL", null, null)
                }
                festivalList.addAll(festivals.map { it.name })
            }

            if (yearList.size == 1 && yearList[0] == "All") {
                val years = withContext(Dispatchers.IO) {
                    musicService.getTags(refresh, "YEAR", null, null)
                }.sortedByDescending { it.name }
                yearList.addAll(years.map { it.name })
            }
        }
    }

    private fun createTitle(
        genre: String,
        year: String,
        sortMethod: String,
        festival: String?
    ): String {
        return buildString {
            when (sortMethod) {
                "AddedDesc" -> append("Recent ")
                "Random" -> append("Random ")
                "LastWrittenDesc" -> append("Recent Modified ")
            }
            if (!festival.isNullOrBlank()) append(festival)
            if (genre.isNotBlank() && genre != "All") {
                if (isNotEmpty()) append(" ")
                append(genre)
            }
            if (year != "All") append(" ($year)")
        }
    }
}
