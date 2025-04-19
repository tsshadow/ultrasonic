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
import java.time.Year

/**
 * Fragment for selecting or saving tile presets for regular songs.
 * Allows filtering by genre, year, rating, and sort method.
 * Search results or saved tiles navigate to a TrackCollection.
 */
class SelectSongFragment : Fragment(), RefreshableFragment {

    companion object {
        private const val DEFAULT_LENGTH = "short"
        private const val PAGE_KEY = "song"
    }

    override var swipeRefresh: SwipeRefreshLayout? = null

    private lateinit var gridLayout: GridLayout
    private lateinit var yearSpinner: Spinner
    private lateinit var ratingMinSpinner: Spinner
    private lateinit var ratingMaxSpinner: Spinner
    private lateinit var genreSpinner: Spinner
    private lateinit var labelSpinner: Spinner
    private lateinit var sortMethodSpinner: Spinner
    private lateinit var searchButton: Button
    private lateinit var saveButton: Button

    private lateinit var filterContainer: View
    private lateinit var toggleFiltersButton: Button
    private var filtersVisible = false

    private val genreList = arrayListOf("All")
    private val yearList = arrayListOf("All")
    private val labelList = arrayListOf("All")
    private var tiles = mutableListOf<TileInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.tsshadow_select_song, container, false)
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

        setTitle(this, R.string.main_songs_title)
        load(false)
    }

    private fun initializeViews(view: View) {
        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMinSpinner = view.findViewById(R.id.select_rating_min)
        ratingMaxSpinner = view.findViewById(R.id.select_rating_max)
        genreSpinner = view.findViewById(R.id.select_genre)
        labelSpinner = view.findViewById(R.id.select_label)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        searchButton = view.findViewById(R.id.search)
        saveButton = view.findViewById(R.id.save)
        gridLayout = view.findViewById(R.id.gridLayoutContainer)
        filterContainer = view.findViewById(R.id.filter_container)
        toggleFiltersButton = view.findViewById(R.id.toggle_filters)
    }

    private fun initializeSpinners() {
        fun <T> createAdapter(items: List<T>): ArrayAdapter<T> {
            return ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        yearSpinner.adapter = createAdapter(yearList)
        genreSpinner.adapter = createAdapter(genreList)
        labelSpinner.adapter = createAdapter(labelList)
        ratingMinSpinner.adapter = createAdapter((0..5).toList())
        ratingMaxSpinner.adapter = createAdapter((0..5).toList())
        ratingMaxSpinner.setSelection(5)

        val sortMethods = listOf(
            "None", "Id", "Random", "AddedDesc", "LastWrittenDesc",
            "StarredDateDesc", "Name", "DateDescAndRelease", "Release", "TrackList"
        )
        sortMethodSpinner.adapter = createAdapter(sortMethods)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadTilesOrDefaults() {
        tiles = loadTiles(requireContext(), PAGE_KEY).toMutableList()
        val currentYear = Year.now().value.toString()

        if (tiles.isEmpty()) {
            tiles = mutableListOf(
                TileInfo("Euphoric Hardstyle ($currentYear)", genre = "Euphoric Hardstyle", length = DEFAULT_LENGTH, year = currentYear),
                TileInfo("Hardstyle ($currentYear)", genre = "Hardstyle", length = DEFAULT_LENGTH, year = currentYear),
                TileInfo("Mainstream Hardstyle ($currentYear)", genre = "Mainstream Hardstyle", length = DEFAULT_LENGTH, year = currentYear),
                TileInfo("Raw Hardstyle ($currentYear)", genre = "Raw Hardstyle", length = DEFAULT_LENGTH, year = currentYear),
                TileInfo("Hardcore ($currentYear)", genre = "Hardcore", length = DEFAULT_LENGTH, year = currentYear),
                TileInfo("Uptempo Hardcore ($currentYear)", genre = "Uptempo Hardcore", length = DEFAULT_LENGTH, year = currentYear),
                TileInfo("Bouncy Uptempo ($currentYear)", genre = "Bouncy Uptempo", length = DEFAULT_LENGTH, year = currentYear)
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
                genre = if(genreSpinner.selectedItem as String == "All") null else genreSpinner.selectedItem as String,
                label = if (labelSpinner.selectedItem as String == "All") null else labelSpinner.selectedItem as String,
                size = maxSongs,
                offset = 0,
                year = if(yearSpinner.selectedItem as String == "All") null else yearSpinner.selectedItem as String,
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
            val label = labelSpinner.selectedItem as? String


            val newTile = TileInfo(
                title = createTitle(genre, year, sortMethod, label),
                genre = if (genreSpinner.selectedItem as String == "All") null else genreSpinner.selectedItem as String,
                year = if (year == "All") null else year,
                label = if (label == "All") null else label,
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

            if (labelList.size == 1 && labelList[0] == "All") {
                val labels = withContext(Dispatchers.IO) {
                    musicService.getTags(refresh, "PUBLISHER", null, null)
                }
                labelList.addAll(labels.map { it.name })
            }

            if (yearList.size == 1 && yearList[0] == "All") {
                val years = withContext(Dispatchers.IO) {
                    musicService.getTags(refresh, "YEAR", null, null)
                }.sortedByDescending { it.name }
                yearList.addAll(years.map { it.name })
            }
        }
    }

    private fun createTitle(genre: String, year: String, sortMethod: String,
                            label: String?): String {
        return buildString {
            when (sortMethod) {
                "AddedDesc" -> append("Recent ")
                "Random" -> append("Random ")
                "LastWrittenDesc" -> append("Recent Modified ")
            }
            if (!label.isNullOrBlank()) append(label)
            if (genre.isNotBlank() && genre != "All") {
                if (isNotEmpty()) append(" ")
                append(genre)
            }
            if (year != "All") append(" ($year)")
        }
    }
}
