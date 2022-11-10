/*
 * SelectGenreFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.BackgroundTask
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.FragmentBackgroundTask
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.view.GenreAdapter
import timber.log.Timber

/**
 * Displays the available genres in the media library
 *
 * TODO: This file has been converted from Java, but not modernized yet.
 */
class SelectGenreFragment : Fragment() {
    private var refreshGenreListView: SwipeRefreshLayout? = null
    private var genreListView: ListView? = null
    private var emptyView: View? = null
    private var cancellationToken: CancellationToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.select_genre, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cancellationToken = CancellationToken()
        refreshGenreListView = view.findViewById(R.id.select_genre_refresh)
        genreListView = view.findViewById(R.id.select_genre_list)
        refreshGenreListView!!.setOnRefreshListener { load(true) }

        genreListView!!.setOnItemClickListener { parent: AdapterView<*>,
            _: View?,
            position: Int,
            _: Long ->
            val genre = parent.getItemAtPosition(position) as Genre

            val action = NavigationGraphDirections.toTrackCollection(
                genreName = genre.name,
                size = maxSongs,
                offset = 0
            )
            findNavController().navigate(action)
        }
        emptyView = view.findViewById(R.id.select_genre_empty)
        registerForContextMenu(genreListView!!)
        setTitle(this, R.string.main_genres_title)
        load(false)
    }

    override fun onDestroyView() {
        cancellationToken!!.cancel()
        super.onDestroyView()
    }

    // TODO: Migrate to Coroutines
    private fun load(refresh: Boolean) {
        val task: BackgroundTask<List<Genre>> = object : FragmentBackgroundTask<List<Genre>>(
            activity, true, refreshGenreListView, cancellationToken
        ) {
            override fun doInBackground(): List<Genre> {
                val musicService = getMusicService()
                var genres: List<Genre> = ArrayList()
                try {
                    genres = musicService.getGenres(refresh)
                } catch (all: Exception) {
                    Timber.e(all, "Failed to load genres")
                }
                return genres
            }

            override fun done(result: List<Genre>) {
                emptyView!!.isVisible = result.isEmpty()
                genreListView!!.adapter = GenreAdapter(context, result)
            }
        }
        task.execute()
    }
}
