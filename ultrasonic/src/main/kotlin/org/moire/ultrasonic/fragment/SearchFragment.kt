/*
 * SearchFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumRowDelegate
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.adapters.DividerBinder
import org.moire.ultrasonic.adapters.MoreButtonBinder
import org.moire.ultrasonic.adapters.MoreButtonBinder.MoreButton
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.model.SearchListModel
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer.Companion.playVideo
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber

/**
 * Initiates a search on the media library and displays the results
 * TODO: Switch to material3 class
 */
class SearchFragment : MultiListFragment<Identifiable>(), KoinComponent {
    private var searchResult: SearchResult? = null
    private var searchRefresh: SwipeRefreshLayout? = null
    private var searchView: SearchView? = null

    private val mediaPlayerManager: MediaPlayerManager by inject()

    private val shareHandler: ShareHandler by inject()
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()

    private var cancellationToken: CancellationToken? = null

    private val navArgs by navArgs<SearchFragmentArgs>()

    override val listModel: SearchListModel by viewModels()

    override val mainLayout: Int = R.layout.search

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancellationToken = CancellationToken()
        setTitle(this, R.string.search_title)

        // Register our options menu
        (requireActivity() as MenuHost).addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        listModel.searchResult.observe(
            viewLifecycleOwner
        ) {
            if (it != null) {
                // Shorten the display initially
                searchResult = it
                populateList(listModel.trimResultLength(it))
            }
        }

        searchRefresh = view.findViewById(R.id.swipe_refresh_view)
        searchRefresh!!.isEnabled = false

        registerForContextMenu(listView!!)

        // Register our data binders
        // IMPORTANT:
        // They need to be added in the order of most specific -> least specific.
        viewAdapter.register(
            ArtistRowBinder(
                onItemClick = ::onItemClick,
                onContextMenuClick = ::onContextMenuItemSelected,
                enableSections = false
            )
        )

        viewAdapter.register(
            AlbumRowDelegate(
                onItemClick = ::onItemClick,
                onContextMenuClick = ::onContextMenuItemSelected
            )
        )

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = { file, _ -> onItemClick(file) },
                onContextMenuClick = ::onContextMenuItemSelected,
                checkable = false,
                draggable = false,
                lifecycleOwner = viewLifecycleOwner
            )
        )

        viewAdapter.register(
            DividerBinder()
        )

        viewAdapter.register(
            MoreButtonBinder()
        )

        // If the fragment was started with a query (e.g. from voice search),
        // try to execute search right away
        if (navArgs.query != null) {
            return search(navArgs.query!!, navArgs.autoplay)
        }
    }

    /**
     * This provide creates the search bar above the recycler view
     */
    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
            setupOptionsMenu(menu)
        }

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.search, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return true
        }
    }
    fun setupOptionsMenu(menu: Menu) {
        val activity = activity ?: return
        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchItem = menu.findItem(R.id.search_item)
        searchView = searchItem.actionView as SearchView
        val searchableInfo = searchManager.getSearchableInfo(requireActivity().componentName)
        searchView!!.setSearchableInfo(searchableInfo)

        val autoPlay = navArgs.autoplay
        val query = navArgs.query

        // If started with a query, enter it to the searchView
        if (query != null) {
            searchView!!.setQuery(query, false)
            searchView!!.clearFocus()
        }

        searchView!!.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return true
            }

            override fun onSuggestionClick(position: Int): Boolean {
                Timber.d("onSuggestionClick: %d", position)
                val cursor = searchView!!.suggestionsAdapter.cursor
                cursor.moveToPosition(position)

                // 2 is the index of col containing suggestion name.
                val suggestion = cursor.getString(2)
                searchView!!.setQuery(suggestion, true)
                return true
            }
        })

        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                Timber.d("onQueryTextSubmit: %s", query)
                searchView!!.clearFocus()
                search(query, autoPlay)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })

        searchView!!.setIconifiedByDefault(false)
        searchItem.expandActionView()
    }

    override fun onDestroyView() {
        Util.hideKeyboard(activity)
        cancellationToken?.cancel()
        super.onDestroyView()
    }

    private fun downloadBackground(save: Boolean, songs: List<Track?>) {
        val onValid = Runnable {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            DownloadService.download(songs.filterNotNull(), save)
        }
        onValid.run()
    }

    private fun search(query: String, autoplay: Boolean) {
        listModel.viewModelScope.launch(CommunicationError.getHandler(context)) {
            refreshListView?.isRefreshing = true
            listModel.search(query)
            refreshListView?.isRefreshing = false
        }.invokeOnCompletion {
            if (it == null && autoplay) {
                autoplay()
            }
        }
    }

    private fun populateList(result: SearchResult) {
        val list = mutableListOf<Identifiable>()

        val artists = result.artists
        if (artists.isNotEmpty()) {

            list.add(DividerBinder.Divider(R.string.search_artists))
            list.addAll(artists)
            if (searchResult!!.artists.size > artists.size) {
                list.add(MoreButton(0, ::expandArtists))
            }
        }
        val albums = result.albums
        if (albums.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_albums))
            list.addAll(albums)
            if (searchResult!!.albums.size > albums.size) {
                list.add(MoreButton(1, ::expandAlbums))
            }
        }
        val songs = result.songs
        if (songs.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_songs))
            list.addAll(songs)
            if (searchResult!!.songs.size > songs.size) {
                list.add(MoreButton(2, ::expandSongs))
            }
        }

        // Show/hide the empty text view
        emptyView.isVisible = list.isEmpty()

        viewAdapter.submitList(list)
    }

    private fun expandArtists() {
        populateList(listModel.trimResultLength(searchResult!!, maxArtists = Int.MAX_VALUE))
    }

    private fun expandAlbums() {
        populateList(listModel.trimResultLength(searchResult!!, maxAlbums = Int.MAX_VALUE))
    }

    private fun expandSongs() {
        populateList(listModel.trimResultLength(searchResult!!, maxSongs = Int.MAX_VALUE))
    }

    private fun onArtistSelected(item: ArtistOrIndex) {
        // Create action based on type
        val action = if (item is Index) {
            SearchFragmentDirections.searchToTrackCollection(
                id = item.id,
                name = item.name,
                parentId = item.id,
                isArtist = false
            )
        } else {
            SearchFragmentDirections.searchToAlbumsList(
                type = AlbumListType.SORTED_BY_NAME,
                byArtist = true,
                id = item.id,
                title = item.name,
                size = 1000,
                offset = 0
            )
        }

        // Lets go!
        findNavController().navigate(action)
    }

    private fun onAlbumSelected(album: Album, autoplay: Boolean) {

        val action = SearchFragmentDirections.searchToTrackCollection(
            id = album.id,
            name = album.title,
            autoPlay = autoplay,
            isAlbum = true
        )
        findNavController().navigate(action)
    }

    private fun onSongSelected(song: Track, append: Boolean) {
        if (!append) {
            mediaPlayerManager.clear()
        }
        mediaPlayerManager.addToPlaylist(
            listOf(song),
            autoPlay = false,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.APPEND
        )
        mediaPlayerManager.play(mediaPlayerManager.mediaItemCount - 1)
        toast(context, resources.getQuantityString(R.plurals.select_album_n_songs_added, 1, 1))
    }

    private fun onVideoSelected(track: Track) {
        playVideo(requireContext(), track)
    }

    private fun autoplay() {
        if (searchResult!!.songs.isNotEmpty()) {
            onSongSelected(searchResult!!.songs[0], false)
        } else if (searchResult!!.albums.isNotEmpty()) {
            onAlbumSelected(searchResult!!.albums[0], true)
        }
    }

    override fun onItemClick(item: Identifiable) {
        when (item) {
            is ArtistOrIndex -> {
                onArtistSelected(item)
            }
            is Track -> {
                if (item.isVideo) {
                    onVideoSelected(item)
                } else {
                    onSongSelected(item, true)
                }
            }
            is Album -> {
                onAlbumSelected(item, false)
            }
        }
    }

    @Suppress("LongMethod")
    override fun onContextMenuItemSelected(menuItem: MenuItem, item: Identifiable): Boolean {
        val isArtist = (item is Artist)

        val found = EntryListFragment.handleContextMenu(
            menuItem,
            item,
            isArtist,
            downloadHandler,
            this
        )

        if (found || item !is Track) return true

        val songs = mutableListOf<Track>()

        when (menuItem.itemId) {
            R.id.song_menu_play_now -> {
                songs.add(item)
                downloadHandler.addTracksToMediaController(
                    songs = songs,
                    append = false,
                    playNext = false,
                    autoPlay = true,
                    shuffle = false,
                    fragment = this,
                    playlistName = null
                )
            }
            R.id.song_menu_play_next -> {
                songs.add(item)
                downloadHandler.addTracksToMediaController(
                    songs = songs,
                    append = true,
                    playNext = true,
                    autoPlay = false,
                    shuffle = false,
                    fragment = this,
                    playlistName = null
                )
            }
            R.id.song_menu_play_last -> {
                songs.add(item)
                downloadHandler.addTracksToMediaController(
                    songs = songs,
                    append = true,
                    playNext = false,
                    autoPlay = false,
                    shuffle = false,
                    fragment = this,
                    playlistName = null
                )
            }
            R.id.song_menu_pin -> {
                songs.add(item)
                toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_pinned,
                        songs.size,
                        songs.size
                    )
                )
                downloadBackground(true, songs)
            }
            R.id.song_menu_download -> {
                songs.add(item)
                toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_downloaded,
                        songs.size,
                        songs.size
                    )
                )
                downloadBackground(false, songs)
            }
            R.id.song_menu_unpin -> {
                songs.add(item)
                toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_unpinned,
                        songs.size,
                        songs.size
                    )
                )
                DownloadService.unpin(songs)
            }
            R.id.song_menu_share -> {
                songs.add(item)
                shareHandler.createShare(
                    fragment = this,
                    tracks = songs,
                    swipe = searchRefresh,
                    cancellationToken = cancellationToken!!,
                    additionalId = null
                )
            }
        }

        return true
    }

    companion object {
        var DEFAULT_ARTISTS = Settings.defaultArtists
        var DEFAULT_ALBUMS = Settings.defaultAlbums
        var DEFAULT_SONGS = Settings.defaultSongs
    }
}
