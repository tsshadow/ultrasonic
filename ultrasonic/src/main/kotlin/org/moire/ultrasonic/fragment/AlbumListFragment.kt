/*
 * AlbumListFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

@file:Suppress("NAME_SHADOWING")

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumRowBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.model.AlbumListModel

/**
 * Displays a list of Albums from the media library
 */
class AlbumListFragment : EntryListFragment<Album>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: AlbumListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout: Int = R.layout.list_layout_generic

    /**
     * Whether to refresh the data onViewCreated
     */
    override val refreshOnCreation: Boolean = false

    private val navArgs: AlbumListFragmentArgs by navArgs()

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(
        refresh: Boolean
    ): LiveData<List<Album>> {
        fetchAlbums(refresh)

        return listModel.list
    }

    private fun fetchAlbums(refresh: Boolean = navArgs.refresh, append: Boolean = navArgs.append) {
        val refresh = navArgs.refresh || refresh

        listModel.viewModelScope.launch(handler) {
            refreshListView?.isRefreshing = true

            if (navArgs.type == AlbumListType.BY_ARTIST) {
                listModel.getAlbumsOfArtist(
                    refresh = navArgs.refresh,
                    id = navArgs.id!!,
                    name = navArgs.title
                )
            } else {
                listModel.getAlbums(
                    albumListType = navArgs.type,
                    size = navArgs.size,
                    offset = navArgs.offset,
                    append = append,
                    refresh = refresh or append
                )
            }
            refreshListView?.isRefreshing = false
        }
    }

    // TODO: Make generic

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setTitle(navArgs.title)

        // Attach our onScrollListener
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            val scrollListener = object : EndlessScrollListener(viewManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                    // Triggered only when new data needs to be appended to the list
                    // Add whatever code is needed to append new items to the bottom of the list
                    fetchAlbums(append = true)
                }
            }
            addOnScrollListener(scrollListener)
        }

        viewAdapter.register(
            AlbumRowBinder(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
                imageLoaderProvider.getImageLoader()
            )
        )

        emptyTextView.setText(R.string.select_album_empty)
    }

    override fun onItemClick(item: Album) {
        val action = AlbumListFragmentDirections.albumListToTrackCollection(
            item.id,
            isAlbum = item.isDirectory,
            name = item.title,
            parentId = item.parent
        )
        findNavController().navigate(action)
    }
}
