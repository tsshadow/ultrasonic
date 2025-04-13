/*
 * ArtistListFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.model.ArtistListModel
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import timber.log.Timber

/**
 * Displays the list of Artists or Indexes (folders) from the media library
 */
class ArtistListFragment : EntryListFragment<ArtistOrIndex>() {
    private var allDataLoaded: Boolean = false
    private var isLoading: Boolean = false
    private var offset = 0
    private val pageSize = 1000
    override val listModel: ArtistListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout = R.layout.list_layout_generic

    private val navArgs: ArtistListFragmentArgs by navArgs()

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(refresh: Boolean, append: Boolean): LiveData<List<ArtistOrIndex>> {
        return listModel.getItems(navArgs.refresh || refresh, swipeRefresh!!)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(navArgs.title)

        viewAdapter.register(
            ArtistRowBinder(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) }
            )
        )

        listView?.addOnScrollListener(object :
            androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                dx: Int,
                dy: Int
            ) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy <= 0 || isLoading || allDataLoaded) return

                val layoutManager =
                    recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                        ?: return

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                val reachedBottom = (visibleItemCount + firstVisibleItem) >= totalItemCount - 3
                if (reachedBottom) {
                    loadMoreItems()
                }
            }
        })
    }

    private fun loadMoreItems() {
        isLoading = true
        offset += pageSize
        listModel.loadMore(
            isOffline = false,
            useId3Tags = true, // or use a flag from somewhere like `ActiveServerProvider`
            musicService = getMusicService(),
            refresh = false,
            offset = offset,
            count = pageSize
        )

        // You can observe `artists` in your ViewModel or use a LiveData callback for better control
        // Here's a basic delay-based placeholder to stop loading
        listView?.postDelayed({
            isLoading = false
            if ((viewAdapter.items.size ?: 0) < offset + pageSize) {
                allDataLoaded = true
            }
        }, 1000)
    }

    override fun onItemClick(item: ArtistOrIndex) {
        val action = if (item is Index) {
            NavigationGraphDirections.toTrackCollection(
                id = item.id,
                name = item.name,
                parentId = item.id,
                isArtist = (item is Artist)
            )
        } else {
            NavigationGraphDirections.toAlbumList(
                type = AlbumListType.SORTED_BY_NAME,
                byArtist = true,
                id = item.id,
                title = item.name,
                size = 1000,
                offset = 0
            )
        }

        findNavController().navigate(action)
    }
}
