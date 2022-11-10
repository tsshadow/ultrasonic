/*
 * PodcastFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.PodcastsChannel
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.BackgroundTask
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.FragmentBackgroundTask
import org.moire.ultrasonic.util.Util.applyTheme

/**
 * Displays the podcasts available on the server
 *
 * TODO: This file has been converted from Java, but not modernized yet.
 * TODO: Use Coroutines
 */
class PodcastFragment : Fragment() {

    private var emptyTextView: View? = null
    var channelItemsListView: ListView? = null
    private var cancellationToken: CancellationToken? = null
    private var swipeRefresh: SwipeRefreshLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.podcasts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancellationToken = CancellationToken()
        swipeRefresh = view.findViewById(R.id.podcasts_refresh)
        swipeRefresh!!.setOnRefreshListener { load(true) }
        setTitle(this, R.string.podcasts_label)
        emptyTextView = view.findViewById(R.id.select_podcasts_empty)
        channelItemsListView = view.findViewById(R.id.podcasts_channels_items_list)
        channelItemsListView!!.setOnItemClickListener { parent, _, position, _ ->
            val (id) = parent.getItemAtPosition(position) as PodcastsChannel
            val action = NavigationGraphDirections.toTrackCollection(
                podcastChannelId = id
            )

            findNavController().navigate(action)
        }
        load(false)
    }

    override fun onDestroyView() {
        cancellationToken!!.cancel()
        super.onDestroyView()
    }

    private fun load(refresh: Boolean) {
        val task: BackgroundTask<List<PodcastsChannel>> =
            object : FragmentBackgroundTask<List<PodcastsChannel>>(
                activity, true, swipeRefresh, cancellationToken
            ) {
                @Throws(Throwable::class)
                override fun doInBackground(): List<PodcastsChannel> {
                    val musicService = getMusicService()
                    return musicService.getPodcastsChannels(refresh)
                }

                override fun done(result: List<PodcastsChannel>) {
                    channelItemsListView!!.adapter =
                        ArrayAdapter(requireContext(), R.layout.list_item_generic, result)
                    emptyTextView!!.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        task.execute()
    }
}
