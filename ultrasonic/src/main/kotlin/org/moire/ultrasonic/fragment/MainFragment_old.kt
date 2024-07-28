/*
 * MainFragment_old.kt
 * Copyright (C) 2009-2024 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.


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
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.databinding.MainBinding
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Displays the Main screen of Ultrasonic, where the music library can be browsed
 */
class MainFragment_old : Fragment(), KoinComponent {

    private lateinit var musicTitle: TextView
    private lateinit var artistsButton: TextView
    private lateinit var albumsButton: TextView
    private lateinit var genresButton: TextView
    private lateinit var videosTitle: TextView
    private lateinit var songsTitle: TextView
    private lateinit var randomSongsButton: TextView
    private lateinit var songsStarredButton: TextView
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

    private var binding: MainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MainBinding.inflate(inflater, container, false)
        return binding!!.root
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
        val currentId3Setting = Settings.shouldUseId3Tags

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
        musicTitle = binding!!.mainMusic
        artistsButton = binding!!.mainArtistsButton
        albumsButton = binding!!.mainAlbumsButton
        genresButton = binding!!.mainGenresButton
        videosTitle = binding!!.mainVideosTitle
        songsTitle = binding!!.mainSongs
        randomSongsButton = binding!!.mainSongsButton
        songsStarredButton = binding!!.mainSongsStarred
        albumsTitle = binding!!.mainAlbums
        albumsNewestButton = binding!!.mainAlbumsNewest
        albumsRandomButton = binding!!.mainAlbumsRandom
        albumsHighestButton = binding!!.mainAlbumsHighest
        albumsStarredButton = binding!!.mainAlbumsStarred
        albumsRecentButton = binding!!.mainAlbumsRecent
        albumsFrequentButton = binding!!.mainAlbumsFrequent
        albumsAlphaByNameButton = binding!!.mainAlbumsAlphaByName
        albumsAlphaByArtistButton = binding!!.mainAlbumsAlphaByArtist
        videosButton = binding!!.mainVideos
    }

    private fun setupItemVisibility() {
        // Cache some values
        useId3 = Settings.shouldUseId3Tags
        useId3Offline = Settings.useId3TagsOffline

        val isOnline = !isOffline()

        // Music
        musicTitle.isVisible = true
        artistsButton.isVisible = true
        albumsButton.isVisible = isOnline || useId3Offline
        genresButton.isVisible = isOnline

        // Songs
        songsTitle.isVisible = true
        randomSongsButton.isVisible = true
        songsStarredButton.isVisible = isOnline

        // Albums
        albumsTitle.isVisible = isOnline || useId3Offline
        albumsNewestButton.isVisible = isOnline || useId3Offline
        albumsRecentButton.isVisible = isOnline
        albumsFrequentButton.isVisible = isOnline
        albumsHighestButton.isVisible = isOnline && !useId3
        albumsRandomButton.isVisible = isOnline
        albumsStarredButton.isVisible = isOnline
        albumsAlphaByNameButton.isVisible = isOnline || useId3Offline
        albumsAlphaByArtistButton.isVisible = isOnline || useId3Offline

        // Videos
        videosTitle.isVisible = isOnline
        videosButton.isVisible = isOnline
    }

    private fun setupClickListener() {
        albumsNewestButton.setOnClickListener {
            showAlbumList(AlbumListType.NEWEST, R.string.main_albums_newest)
        }

        albumsRandomButton.setOnClickListener {
            showAlbumList(AlbumListType.RANDOM, R.string.main_albums_random)
        }

        albumsHighestButton.setOnClickListener {
            showAlbumList(AlbumListType.HIGHEST, R.string.main_albums_highest)
        }

        albumsRecentButton.setOnClickListener {
            showAlbumList(AlbumListType.RECENT, R.string.main_albums_recent)
        }

        albumsFrequentButton.setOnClickListener {
            showAlbumList(AlbumListType.FREQUENT, R.string.main_albums_frequent)
        }

        albumsStarredButton.setOnClickListener {
            showAlbumList(AlbumListType.STARRED, R.string.main_albums_starred)
        }

        albumsAlphaByNameButton.setOnClickListener {
            showAlbumList(AlbumListType.SORTED_BY_NAME, R.string.main_albums_alphaByName)
        }

        albumsAlphaByArtistButton.setOnClickListener {
            showAlbumList(AlbumListType.SORTED_BY_ARTIST, R.string.main_albums_alphaByArtist)
        }

        songsStarredButton.setOnClickListener {
            showStarredSongs()
        }

        artistsButton.setOnClickListener {
            showArtists()
        }

        albumsButton.setOnClickListener {
            showAlbumList(AlbumListType.SORTED_BY_NAME, R.string.main_albums_title)
        }

        randomSongsButton.setOnClickListener {
            showRandomSongs()
        }

        genresButton.setOnClickListener {
            showGenres()
        }

        videosButton.setOnClickListener {
            showVideos()
        }
    }

    private fun showStarredSongs() {
        val action = MainFragmentDirections.mainToTrackCollection(
            getStarred = true,
        )
        findNavController().navigate(action)
    }

    private fun showRandomSongs() {
        val action = MainFragmentDirections.mainToTrackCollection(
            getRandom = true,
            size = Settings.maxSongs
        )
        findNavController().navigate(action)
    }

    private fun showArtists() {
        val action = MainFragmentDirections.mainToArtistList(
            title = requireContext().resources.getString(R.string.main_artists_title)
        )
        findNavController().navigate(action)
    }

    private fun showAlbumList(type: AlbumListType, titleIndex: Int) {
        val title = requireContext().resources.getString(titleIndex, "")
        val action = MainFragmentDirections.mainToAlbumList(
            type = type,
            title = title,
            size = Settings.maxAlbums,
            offset = 0
        )
        findNavController().navigate(action)
    }

    private fun showGenres() {
        findNavController().navigate(R.id.mainToSelectGenre)
    }

    private fun showVideos() {
        val action = MainFragmentDirections.mainToTrackCollection(
            getVideos = true,
        )
        findNavController().navigate(action)
    }

    companion object {
        private var useId3 = false
        private var useId3Offline = false
    }
}
*/