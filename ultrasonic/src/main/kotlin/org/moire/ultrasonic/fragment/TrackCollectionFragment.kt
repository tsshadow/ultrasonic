/*
 * TrackCollectionFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumHeader
import org.moire.ultrasonic.adapters.AlbumRowDelegate
import org.moire.ultrasonic.adapters.HeaderViewBinder
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.model.TrackCollectionModel
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.DownloadAction
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities
import timber.log.Timber

/**
 * Displays a group of tracks, eg. the songs of an album, of a playlist etc.
 *
 * In most cases the data should be just a list of Entries, but there are some cases
 * where the list can contain Albums as well. This happens especially when having ID3 tags disabled,
 * or using Offline mode, both in which Indexes instead of Artists are being used.
 */
@Suppress("TooManyFunctions")
open class TrackCollectionFragment(
    initialOrder: SortOrder? = null
) : MultiListFragment<MusicDirectory.Child>(), FilterableFragment {

    private var albumButtons: View? = null
    private var selectButton: MaterialButton? = null
    internal var playNowButton: MaterialButton? = null
    private var playNextButton: MaterialButton? = null
    private var playLastButton: MaterialButton? = null
    private var pinButton: MaterialButton? = null
    private var unpinButton: MaterialButton? = null
    private var downloadButton: MaterialButton? = null
    private var deleteButton: MaterialButton? = null
    private var playAllButtonVisible = false
    private var shareButtonVisible = false
    private var playAllButton: MenuItem? = null
    private var shareButton: MenuItem? = null

    internal val mediaPlayerManager: MediaPlayerManager by inject()
    private val shareHandler: ShareHandler by inject()
    internal var cancellationToken: CancellationToken? = null

    override val listModel: TrackCollectionModel by viewModels()
    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private var sortOrder = initialOrder

    /**
     * The id of the main layout
     */
    override val mainLayout: Int = R.layout.list_layout_track

    private val navArgs: TrackCollectionFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancellationToken = CancellationToken()

        albumButtons = view.findViewById(R.id.menu_album)

        // Setup refresh handler
        refreshListView = view.findViewById(refreshListId)
        refreshListView?.setOnRefreshListener {
            handleRefresh()
        }

        setupButtons(view)

        registerForContextMenu(listView!!)

        // Register our options menu
        (requireActivity() as MenuHost).addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        viewAdapter.register(
            HeaderViewBinder(
                context = requireContext()
            )
        )

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = { file, _ -> onItemClick(file) },
                onContextMenuClick = { menu, id -> onContextMenuItemSelected(menu, id) },
                checkable = true,
                draggable = false,
                lifecycleOwner = viewLifecycleOwner
            )
        )

        viewAdapter.register(
            AlbumRowDelegate(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) }
            )
        )

        // Change the buttons if the status of any selected track changes
        rxBusSubscription += RxBus.trackDownloadStateObservable.subscribe {
            if (it.progress != null) return@subscribe
            val selectedSongs = getSelectedSongs()
            if (!selectedSongs.any { song -> song.id == it.id }) return@subscribe
            triggerButtonUpdate(selectedSongs)
        }

        triggerButtonUpdate()

        // Update the buttons when the selection has changed
        viewAdapter.selectionRevision.observe(
            viewLifecycleOwner
        ) {
            triggerButtonUpdate()
        }

        // Attach our onScrollListener
        val scrollListener = object : EndlessScrollListener(viewManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                Timber.w("LOAD MORE")
                // Triggered only when new data needs to be appended to the list
                // Add whatever code is needed to append new items to the bottom of the list
                loadMoreTracks()
            }
        }

        listView!!.addOnScrollListener(scrollListener)
    }

    private fun loadMoreTracks() {
        if (displayRandom() || navArgs.genreName != null) {
            getLiveData(append = true)
        }
    }

    internal open fun handleRefresh() {
        getLiveData(refresh = true)
    }

    internal open fun setupButtons(view: View) {
        selectButton = view.findViewById(R.id.select_album_select)
        playNowButton = view.findViewById(R.id.select_album_play_now)
        playNextButton = view.findViewById(R.id.select_album_play_next)
        playLastButton = view.findViewById(R.id.select_album_play_last)
        pinButton = view.findViewById(R.id.select_album_pin)
        unpinButton = view.findViewById(R.id.select_album_unpin)
        downloadButton = view.findViewById(R.id.select_album_download)
        deleteButton = view.findViewById(R.id.select_album_delete)

        selectButton?.setOnClickListener {
            selectAllOrNone()
        }

        playNowButton?.setOnClickListener {
            playNow(false)
        }

        playNextButton?.setOnClickListener {
            downloadHandler.addTracksToMediaController(
                songs = getSelectedSongs(),
                append = true,
                playNext = true,
                autoPlay = false,
                shuffle = false,
                playlistName = navArgs.playlistName,
                this@TrackCollectionFragment
            )
        }

        playLastButton!!.setOnClickListener {
            playNow(true)
        }

        pinButton?.setOnClickListener {
            downloadBackground(true)
        }

        unpinButton?.setOnClickListener {
            if (Settings.showConfirmationDialog) {
                ConfirmationDialog.Builder(requireContext())
                    .setMessage(R.string.common_unpin_selection_confirmation)
                    .setPositiveButton(R.string.common_unpin) { _, _ ->
                        unpin()
                    }.show()
            } else {
                unpin()
            }
        }

        downloadButton?.setOnClickListener {
            downloadBackground(false)
        }

        deleteButton?.setOnClickListener {
            if (Settings.showConfirmationDialog) {
                ConfirmationDialog.Builder(requireContext())
                    .setMessage(R.string.common_delete_selection_confirmation)
                    .setPositiveButton(R.string.common_delete) { _, _ ->
                        delete()
                    }.show()
            } else {
                delete()
            }
        }
    }

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
            // Hide search button (from xml)
            menu.findItem(R.id.action_search).isVisible = false

            playAllButton = menu.findItem(R.id.select_album_play_all)

            if (playAllButton != null) {
                playAllButton!!.isVisible = playAllButtonVisible
            }

            shareButton = menu.findItem(R.id.menu_item_share)

            if (shareButton != null) {
                shareButton!!.isVisible = shareButtonVisible
            }
        }

        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.track_collection_menu, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.select_album_play_all) {
                playAll()
                return true
            } else if (item.itemId == R.id.menu_item_share) {
                shareHandler.createShare(
                    this@TrackCollectionFragment, getSelectedSongs(),
                    refreshListView, cancellationToken!!,
                    navArgs.id
                )
                return true
            }
            return false
        }
    }

    override fun onDestroyView() {
        cancellationToken!!.cancel()
        rxBusSubscription.dispose()
        super.onDestroyView()
    }

    private fun playNow(
        append: Boolean,
        selectedSongs: List<Track> = getSelectedSongs()
    ) {
        if (selectedSongs.isNotEmpty()) {
            downloadHandler.addTracksToMediaController(
                songs = selectedSongs,
                append = append,
                playNext = false,
                autoPlay = !append,
                playlistName = null,
                fragment = this
            )
        } else {
            playAll(false, append)
        }
    }

    /**
     * Get the size of the underlying list
     */
    private val childCount: Int
        get() {
            val count = viewAdapter.getCurrentList().count()
            return if (listModel.showHeader) {
                count - 1
            } else {
                count
            }
        }

    private fun playAll(shuffle: Boolean = false, append: Boolean = false) {
        var hasSubFolders = false

        for (item in viewAdapter.getCurrentList()) {
            if (item is MusicDirectory.Child && item.isDirectory) {
                hasSubFolders = true
                break
            }
        }

        val isArtist = navArgs.isArtist

        // Need a valid id to recurse sub directories stuff
        if (hasSubFolders && navArgs.id != null) {
            downloadHandler.fetchTracksAndAddToController(
                fragment = this,
                id = navArgs.id!!,
                append = append,
                autoPlay = !append,
                shuffle = shuffle,
                playNext = false,
                isArtist = isArtist
            )
        } else {
            downloadHandler.addTracksToMediaController(
                songs = getAllSongs(),
                append = append,
                playNext = false,
                autoPlay = !append,
                shuffle = shuffle,
                playlistName = navArgs.playlistName,
                fragment = this
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAllSongs(): List<Track> {
        return viewAdapter.getCurrentList().filter {
            it is Track && !it.isDirectory
        } as List<Track>
    }

    private fun selectAllOrNone() {
        val someUnselected = viewAdapter.selectedSet.size < childCount
        selectAll(someUnselected)
    }

    private fun selectAll(selected: Boolean) {
        var selectedCount = viewAdapter.selectedSet.size * -1

        selectedCount += viewAdapter.setSelectionStatusOfAll(selected)

        // Display toast: N tracks selected
        val toastResId = R.string.select_album_n_selected
        Util.toast(activity, getString(toastResId, selectedCount.coerceAtLeast(0)))
    }

    @Synchronized
    fun triggerButtonUpdate(selection: List<Track> = getSelectedSongs()) {
        listModel.calculateButtonState(selection, ::updateButtonState)
    }

    private fun updateButtonState(
        show: TrackCollectionModel.Companion.ButtonStates,
    ) {
        // We are coming back from unknown context
        // and need to ensure Main Thread in order to manipulate the UI
        // If view is null, our view was disposed in the meantime
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val multipleSelection = viewAdapter.hasMultipleSelection()

            playNowButton?.isVisible = show.all
            playNextButton?.isVisible = show.all && multipleSelection
            playLastButton?.isVisible = show.all && multipleSelection
            pinButton?.isVisible = show.all && !isOffline() && show.pin
            unpinButton?.isVisible = show.all && show.unpin
            downloadButton?.isVisible = show.all && show.download && !isOffline()
            deleteButton?.isVisible = show.all && show.delete
        }
    }

    private fun downloadBackground(save: Boolean, tracks: List<Track> = getSelectedSongs()) {
        var songs = tracks

        if (songs.isEmpty()) {
            songs = getAllSongs()
        }

        val action = if (save) DownloadAction.PIN else DownloadAction.DOWNLOAD
        downloadHandler.justDownload(
            action = action,
            fragment = this,
            tracks = songs
        )
    }

    internal fun delete(songs: List<Track> = getSelectedSongs()) {
        downloadHandler.justDownload(
            action = DownloadAction.DELETE,
            fragment = this,
            tracks = songs
        )
    }

    internal fun unpin(songs: List<Track> = getSelectedSongs()) {
        downloadHandler.justDownload(
            action = DownloadAction.UNPIN,
            fragment = this,
            tracks = songs
        )
    }

    override val defaultObserver: (List<MusicDirectory.Child>) -> Unit = {

        Timber.i("Received list")
        val entryList: MutableList<MusicDirectory.Child> = it.toMutableList()

        if (listModel.currentListIsSortable && Settings.shouldSortByDisc) {
            Collections.sort(entryList, EntryByDiscAndTrackComparator())
        }

        var allVideos = true
        var songCount = 0

        for (entry in entryList) {
            if (!entry.isVideo) {
                allVideos = false
            }
            if (!entry.isDirectory) {
                songCount++
            }
        }

        // Hide select button for video lists and singular selection lists
        selectButton!!.isVisible = !allVideos && viewAdapter.hasMultipleSelection() && songCount > 0

        // Show a text if we have no entries
        emptyView.isVisible = entryList.isEmpty()

        triggerButtonUpdate()

        val isAlbumList = (navArgs.albumListType != null)

        playAllButtonVisible = !(isAlbumList || entryList.isEmpty()) && !allVideos
        shareButtonVisible = !isOffline() && songCount > 0

        playAllButton?.isVisible = playAllButtonVisible
        shareButton?.isVisible = shareButtonVisible

        if (songCount > 0 && listModel.showHeader) {
            val intentAlbumName = navArgs.name
            val albumHeader = AlbumHeader(it, intentAlbumName)
            val mixedList: MutableList<Identifiable> = mutableListOf(albumHeader)
            mixedList.addAll(entryList)
            viewAdapter.submitList(mixedList)
        } else {
            viewAdapter.submitList(entryList)
        }

        val playAll = navArgs.autoPlay

        if (playAll && songCount > 0) {
            playAll(
                navArgs.shuffle,
                false
            )
        }

        listModel.currentListIsSortable = true

        Timber.i("Processed list")
    }

    internal fun getSelectedSongs(): List<Track> {
        // Walk through selected set and get the Entries based on the saved ids.
        return viewAdapter.getCurrentList().mapNotNull {
            if (it is Track && viewAdapter.isSelected(it.longId))
                it
            else
                null
        }
    }

    override fun setTitle(title: String?) {
        setTitle(this@TrackCollectionFragment, title)
    }

    fun setTitle(id: Int) {
        setTitle(this@TrackCollectionFragment, id)
    }

    @Suppress("LongMethod")
    override fun getLiveData(
        refresh: Boolean,
        append: Boolean
    ): LiveData<List<MusicDirectory.Child>> {
        Timber.i("Starting gathering track collection data...")
        val id = navArgs.id
        val isAlbum = navArgs.isAlbum
        val name = navArgs.name
        val playlistId = navArgs.playlistId
        val podcastChannelId = navArgs.podcastChannelId
        val playlistName = navArgs.playlistName
        val shareId = navArgs.shareId
        val shareName = navArgs.shareName
        val genreName = navArgs.genreName

        val getStarredTracks = displayStarred()
        val getVideos = navArgs.getVideos
        val getRandomTracks = displayRandom()
        val size = if (navArgs.size < 0) Settings.maxSongs else navArgs.size
        val offset = navArgs.offset
        val refresh2 = navArgs.refresh || refresh

        listModel.viewModelScope.launch(handler) {
            refreshListView?.isRefreshing = true

            if (playlistId != null) {
                setTitle(playlistName!!)
                listModel.getPlaylist(playlistId, playlistName)
            } else if (podcastChannelId != null) {
                setTitle(getString(R.string.podcasts_label))
                listModel.getPodcastEpisodes(podcastChannelId)
            } else if (shareId != null) {
                setTitle(shareName)
                listModel.getShare(shareId)
            } else if (genreName != null) {
                setTitle(genreName)
                listModel.getSongsForGenre(genreName, size, offset, append)
            } else if (getStarredTracks) {
                setTitle(getString(R.string.main_songs_starred))
                listModel.getStarred()
            } else if (getVideos) {
                setTitle(R.string.main_videos)
                listModel.getVideos(refresh2)
            } else if (id == null || getRandomTracks) {
                // There seems to be a bug in ViewPager when resuming the Activity that sub-fragments
                // arguments are empty. If we have no id, just show some random tracks
                setTitle(R.string.main_songs_random)
                listModel.getRandom(size, append)
            } else {
                setTitle(name)

                if (isAlbum) {
                    listModel.getAlbum(refresh2, id, name)
                } else {
                    listModel.getMusicDirectory(refresh2, id, name)
                }
            }

            refreshListView?.isRefreshing = false
        }
        return listModel.currentList
    }

    private fun displayStarred() = (sortOrder == SortOrder.STARRED) || navArgs.getStarred

    private fun displayRandom() = (sortOrder == SortOrder.RANDOM) || navArgs.getRandom

    @Suppress("LongMethod")
    override fun onContextMenuItemSelected(
        menuItem: MenuItem,
        item: MusicDirectory.Child
    ): Boolean {
        val songs = getClickedSong(item)

        when (menuItem.itemId) {
            R.id.song_menu_play_now -> {
                playNow(false, songs)
            }
            R.id.song_menu_play_next -> {
                downloadHandler.addTracksToMediaController(
                    songs = songs,
                    append = true,
                    playNext = true,
                    autoPlay = false,
                    playlistName = navArgs.playlistName,
                    fragment = this@TrackCollectionFragment
                )
            }
            R.id.song_menu_play_last -> {
                playNow(true, songs)
            }
            R.id.song_menu_pin -> {
                downloadBackground(true, songs)
            }
            R.id.song_menu_unpin -> {
                unpin(songs)
            }
            R.id.song_menu_download -> {
                downloadBackground(false, songs)
            }
            R.id.song_menu_share -> {
                if (item is Track) {
                    shareHandler.createShare(
                        this,
                        tracks = listOf(item),
                        swipe = refreshListView,
                        cancellationToken = cancellationToken!!,
                        additionalId = navArgs.id
                    )
                }
            }
            else -> {
                return super.onContextItemSelected(menuItem)
            }
        }
        return true
    }

    private fun getClickedSong(item: MusicDirectory.Child): List<Track> {
        // This can probably be done better
        return viewAdapter.getCurrentList().mapNotNull {
            if (it is Track && (it.id == item.id))
                it
            else
                null
        }
    }

    override fun onItemClick(item: MusicDirectory.Child) {
        when {
            item.isDirectory -> {
                val action = NavigationGraphDirections.toTrackCollection(
                    id = item.id,
                    isAlbum = true,
                    name = item.title,
                    parentId = item.parent
                )
                findNavController().navigate(action)
            }
            item is Track && item.isVideo -> {
                VideoPlayer.playVideo(requireContext(), item)
            }
            else -> {
                triggerButtonUpdate()
            }
        }
    }

    override fun setOrderType(newOrder: SortOrder) {
        sortOrder = newOrder
        getLiveData(refresh = true)
    }

    override var viewCapabilities: ViewCapabilities = ViewCapabilities(
        supportsGrid = false,
        supportedSortOrders = getListOfSortOrders()
    )

    private fun getListOfSortOrders(): List<SortOrder> {
        val isOnline = !isOffline()
        val supported = mutableListOf(SortOrder.RANDOM)

        if (isOnline) {
            supported.add(SortOrder.STARRED)
        }
        return supported
    }
}
