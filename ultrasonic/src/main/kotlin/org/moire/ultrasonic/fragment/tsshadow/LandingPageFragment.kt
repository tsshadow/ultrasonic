package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util

/**
 * Displays the Main screen of Ultrasonic, where the music library can be browsed
 */
class LandingPageFragment : Fragment(), KoinComponent {

    private lateinit var videosTitle: TextView

    // Songs
    private lateinit var songsTitle: TextView
    private lateinit var randomSongsButton: TextView
    private lateinit var recentSongsButton: TextView
    private lateinit var randomSongsThisYearButton: TextView

    // Livesets
    private lateinit var livesetsTitle: TextView
    private lateinit var randomLivesetsButton: TextView
    private lateinit var recentLivesetsButton: TextView
    private lateinit var randomLivesetsThisYearButton: TextView

    private lateinit var albumsTitle: TextView
    private lateinit var albumsNewestButton: TextView
    private lateinit var albumsRandomButton: TextView
    private lateinit var albumsHighestButton: TextView
    private lateinit var albumsStarredButton: TextView
    private lateinit var albumsRecentButton: TextView
    private lateinit var albumsFrequentButton: TextView
    private lateinit var albumsAlphaByNameButton: TextView
    private lateinit var albumsAlphaByArtistButton: TextView
    private lateinit var videosButton: TextView

    private var binding: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = inflater.inflate(R.layout.tsshadow_landing_page, container, false)
        return binding!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupButtons()
        setupClickListener()
        setupItemVisibility()

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        var shouldRelayout = false
        val currentId3Setting = true //Settings.shouldUseId3Tags

        // If setting has changed...
        if (currentId3Setting != useId3) {
            useId3 = currentId3Setting
            shouldRelayout = true
        }

        // then setup the list anew.
        if (shouldRelayout) {
            setupItemVisibility()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setupButtons() {
        // Songs
        songsTitle = binding!!.findViewById(R.id.main_songs);
        randomSongsButton = binding!!.findViewById(R.id.main_songs_button);
        randomSongsThisYearButton = binding!!.findViewById(R.id.main_songs_this_year_button);
        recentSongsButton = binding!!.findViewById(R.id.main_songs_recent);
        // Livesets
        livesetsTitle = binding!!.findViewById(R.id.main_livesets);
        randomLivesetsButton = binding!!.findViewById(R.id.main_livesets_button);
        randomLivesetsThisYearButton = binding!!.findViewById(R.id.main_livesets_this_year_button);
        recentLivesetsButton = binding!!.findViewById(R.id.main_livesets_recent);

        // Albums
        albumsTitle = binding!!.findViewById(R.id.main_albums)
        albumsNewestButton = binding!!.findViewById(R.id.main_albums_newest)
        albumsRandomButton = binding!!.findViewById(R.id.main_albums_random)
        albumsHighestButton = binding!!.findViewById(R.id.main_albums_highest)
        albumsStarredButton = binding!!.findViewById(R.id.main_albums_starred)
        albumsRecentButton = binding!!.findViewById(R.id.main_albums_recent)
        albumsFrequentButton = binding!!.findViewById(R.id.main_albums_frequent)
        albumsAlphaByNameButton = binding!!.findViewById(R.id.main_albums_alphaByName)
        albumsAlphaByArtistButton = binding!!.findViewById(R.id.main_albums_alphaByArtist)


        // Videos
        videosTitle = binding!!.findViewById(R.id.main_videos_title)
        videosButton = binding!!.findViewById(R.id.main_videos)
    }

    private fun setupItemVisibility() {
        // Cache some values
//        useId3 = Settings.shouldUseId3Tags
//        useId3Offline = Settings.useId3TagsOffline

        val isOnline = !isOffline()


        // Songs
        songsTitle.isVisible = true
        randomSongsButton.isVisible = true
        recentSongsButton.isVisible = true
        randomSongsThisYearButton.isVisible = true


        // Songs
        livesetsTitle.isVisible = true
        randomLivesetsButton.isVisible = true
        recentLivesetsButton.isVisible = true
        randomLivesetsThisYearButton.isVisible = true

        // Albums
        albumsTitle.isVisible = false// isOnline || useId3Offline
        albumsNewestButton.isVisible =  false//isOnline || useId3Offline
        albumsRecentButton.isVisible =  false//isOnline
        albumsFrequentButton.isVisible =  false//isOnline
        albumsHighestButton.isVisible =  false//isOnline && !useId3
        albumsRandomButton.isVisible =  false//isOnline
        albumsStarredButton.isVisible =  false//isOnline
        albumsAlphaByNameButton.isVisible =  false//isOnline || useId3Offline
        albumsAlphaByArtistButton.isVisible =  false//isOnline || useId3Offline

        // Videos
        videosTitle.isVisible =  false//isOnline
        videosButton.isVisible =  false//isOnline
    }

    private fun setupClickListener() {
        randomSongsButton.setOnClickListener {
            val action = NavigationGraphDirections.toTrackCollection(
                size = maxSongs,
                offset = 0,
                sortMethod = "Random",
                length = "short",
                getSongsName = "Random Songs"
            )
            findNavController().navigate(action)
        }
        randomSongsThisYearButton.setOnClickListener {
            val action = NavigationGraphDirections.toTrackCollection(
                size = maxSongs,
                offset = 0,
                sortMethod = "Random",
                length = "short",
                year = "2024",
                getSongsName = "Random Songs (This Year)"
            )
            findNavController().navigate(action)
        }

        recentSongsButton.setOnClickListener {
            val action = NavigationGraphDirections.toTrackCollection(
                size = maxSongs,
                offset = 0,
                sortMethod = "LastWritten",
                length = "short",
                getSongsName = "Recent Songs"
            )
            findNavController().navigate(action)
        }
        randomLivesetsButton.setOnClickListener {
            val action = NavigationGraphDirections.toTrackCollection(
                size = maxSongs,
                offset = 0,
                sortMethod = "Random",
                length = "long",
                getSongsName = "Random Livesets"
            )
            findNavController().navigate(action)
        }

        recentLivesetsButton.setOnClickListener {
            val action = NavigationGraphDirections.toTrackCollection(
                size = maxSongs,
                offset = 0,
                sortMethod = "LastWritten",
                length = "long",
                getSongsName = "Recent Livesets"
            )
            findNavController().navigate(action)
        }

        randomLivesetsThisYearButton.setOnClickListener {
            val action = NavigationGraphDirections.toTrackCollection(
                size = maxSongs,
                offset = 0,
                sortMethod = "Random",
                length = "long",
                year = "2024",
                getSongsName = "Random Livesets (This Year)"
            )
            findNavController().navigate(action)
        }

    }

    companion object {
        private var useId3 = false
        private var useId3Offline = false
    }
}