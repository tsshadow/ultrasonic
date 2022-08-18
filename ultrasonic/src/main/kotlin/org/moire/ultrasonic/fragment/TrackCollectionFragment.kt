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
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Collections
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumHeader
import org.moire.ultrasonic.adapters.AlbumRowBinder
import org.moire.ultrasonic.adapters.HeaderViewBinder
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.model.TrackCollectionModel
import org.moire.ultrasonic.service.DownloadStatus
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Displays a group of tracks, eg. the songs of an album, of a playlist etc.
 *
 * In most cases the data should be just a list of Entries, but there are some cases
 * where the list can contain Albums as well. This happens especially when having ID3 tags disabled,
 * or using Offline mode, both in which Indexes instead of Artists are being used.
 *
 * TODO: Remove more button and introduce endless scrolling
 */
@Suppress("TooManyFunctions")
open class TrackCollectionFragment : MultiListFragment<MusicDirectory.Child>() {

    private var albumButtons: View? = null
    private var selectButton: ImageView? = null
    internal var playNowButton: ImageView? = null
    private var playNextButton: ImageView? = null
    private var playLastButton: ImageView? = null
    private var pinButton: ImageView? = null
    private var unpinButton: ImageView? = null
    private var downloadButton: ImageView? = null
    private var deleteButton: ImageView? = null
    private var moreButton: ImageView? = null
    private var playAllButtonVisible = false
    private var shareButtonVisible = false
    private var playAllButton: MenuItem? = null
    private var shareButton: MenuItem? = null

    internal val mediaPlayerController: MediaPlayerController by inject()
    internal val downloader: Downloader by inject()
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val shareHandler: ShareHandler by inject()
    internal var cancellationToken: CancellationToken? = null

    override val listModel: TrackCollectionModel by viewModels()
    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

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
        setHasOptionsMenu(true)

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
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner
            )
        )

        viewAdapter.register(
            AlbumRowBinder(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
                imageLoaderProvider.getImageLoader()
            )
        )

        // Change the buttons if the status of any selected track changes
        rxBusSubscription += RxBus.trackDownloadStateObservable.subscribe {
            if (it.progress != null) return@subscribe
            val selectedSongs = getSelectedSongs()
            if (!selectedSongs.any { song -> song.id == it.id }) return@subscribe
            enableButtons(selectedSongs)
        }

        enableButtons()

        // Update the buttons when the selection has changed
        viewAdapter.selectionRevision.observe(
            viewLifecycleOwner
        ) {
            enableButtons()
        }
    }

    internal open fun handleRefresh() {
        getLiveData(true)
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
        moreButton = view.findViewById(R.id.select_album_more)

        selectButton?.setOnClickListener {
            selectAllOrNone()
        }

        playNowButton?.setOnClickListener {
            playNow(false)
        }

        playNextButton?.setOnClickListener {
            downloadHandler.download(
                this@TrackCollectionFragment, append = true,
                save = false, autoPlay = false, playNext = true, shuffle = false,
                songs = getSelectedSongs(),
                playlistName = navArgs.playlistName
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
                ConfirmationDialog.Builder(context)
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
                ConfirmationDialog.Builder(context)
                    .setMessage(R.string.common_delete_selection_confirmation)
                    .setPositiveButton(R.string.common_delete) { _, _ ->
                        delete()
                    }.show()
            } else {
                delete()
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        playAllButton = menu.findItem(R.id.select_album_play_all)

        if (playAllButton != null) {
            playAllButton!!.isVisible = playAllButtonVisible
        }

        shareButton = menu.findItem(R.id.menu_item_share)

        if (shareButton != null) {
            shareButton!!.isVisible = shareButtonVisible
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.select_album, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.select_album_play_all) {
            playAll()
            return true
        } else if (itemId == R.id.menu_item_share) {
            shareHandler.createShare(
                this, getSelectedSongs(),
                refreshListView, cancellationToken!!,
                navArgs.id
            )
            return true
        }

        return false
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
            downloadHandler.download(
                this, append, false, !append, playNext = false,
                shuffle = false, songs = selectedSongs, null
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
        val id = navArgs.id

        if (hasSubFolders) {
            downloadHandler.downloadRecursively(
                fragment = this,
                id = id,
                save = false,
                append = append,
                autoPlay = !append,
                shuffle = shuffle,
                background = false,
                playNext = false,
                unpin = false,
                isArtist = isArtist
            )
        } else {
            downloadHandler.download(
                fragment = this,
                append = append,
                save = false,
                autoPlay = !append,
                playNext = false,
                shuffle = shuffle,
                songs = getAllSongs(),
                playlistName = navArgs.playlistName
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

        selectAll(someUnselected, true)
    }

    private fun selectAll(selected: Boolean, toast: Boolean) {
        var selectedCount = viewAdapter.selectedSet.size * -1

        selectedCount += viewAdapter.setSelectionStatusOfAll(selected)

        // Display toast: N tracks selected
        if (toast) {
            val toastResId = R.string.select_album_n_selected
            Util.toast(activity, getString(toastResId, selectedCount.coerceAtLeast(0)))
        }
    }

    @Suppress("ComplexMethod")
    internal open fun enableButtons(selection: List<Track> = getSelectedSongs()) {
        val enabled = selection.isNotEmpty()
        var unpinEnabled = false
        var deleteEnabled = false
        var downloadEnabled = false
        var isNotInProgress = true
        val multipleSelection = viewAdapter.hasMultipleSelection()

        var pinnedCount = 0

        for (song in selection) {
            val state = downloader.getDownloadState(song)
            when (state) {
                DownloadStatus.DONE -> {
                    deleteEnabled = true
                }
                DownloadStatus.PINNED -> {
                    deleteEnabled = true
                    pinnedCount++
                    unpinEnabled = true
                }
                DownloadStatus.IDLE, DownloadStatus.FAILED -> {
                    downloadEnabled = true
                }
                DownloadStatus.DOWNLOADING,
                DownloadStatus.QUEUED,
                DownloadStatus.RETRYING -> {
                    isNotInProgress = false
                }
                else -> {}
            }
        }

        playNowButton?.isVisible = enabled
        playNextButton?.isVisible = enabled && multipleSelection
        playLastButton?.isVisible = enabled && multipleSelection
        pinButton?.isVisible =
            isNotInProgress && enabled && !isOffline() && selection.size > pinnedCount
        unpinButton?.isVisible = isNotInProgress && enabled && unpinEnabled
        downloadButton?.isVisible = isNotInProgress && enabled && downloadEnabled && !isOffline()
        deleteButton?.isVisible = isNotInProgress && enabled && deleteEnabled
    }

    private fun downloadBackground(save: Boolean) {
        var songs = getSelectedSongs()

        if (songs.isEmpty()) {
            songs = getAllSongs()
        }

        downloadBackground(save, songs)
    }

    private fun downloadBackground(
        save: Boolean,
        songs: List<Track?>
    ) {
        val onValid = Runnable {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.downloadBackground(songs, save)

            if (save) {
                Util.toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_pinned, songs.size, songs.size
                    )
                )
            } else {
                Util.toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_downloaded, songs.size, songs.size
                    )
                )
            }
        }
        onValid.run()
    }

    internal fun delete(songs: List<Track> = getSelectedSongs()) {
        Util.toast(
            context,
            resources.getQuantityString(
                R.plurals.select_album_n_songs_deleted, songs.size, songs.size
            )
        )

        mediaPlayerController.delete(songs)
    }

    internal fun unpin(songs: List<Track> = getSelectedSongs()) {
        Util.toast(
            context,
            resources.getQuantityString(
                R.plurals.select_album_n_songs_unpinned, songs.size, songs.size
            )
        )
        mediaPlayerController.unpin(songs)
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

        val listSize = navArgs.size

        // Hide select button for video lists and singular selection lists
        selectButton!!.isVisible = !allVideos && viewAdapter.hasMultipleSelection() && songCount > 0

        if (songCount > 0) {
            if (listSize == 0 || songCount < listSize) {
                moreButton!!.visibility = View.GONE
            } else {
                moreButton!!.visibility = View.VISIBLE
                if (navArgs.getRandom) {
                    moreRandomTracks()
                } else if (navArgs.genreName != null) {
                    moreSongsForGenre()
                }
            }
        }

        // Show a text if we have no entries
        emptyView.isVisible = entryList.isEmpty()

        enableButtons()

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

    private fun moreSongsForGenre() {
        moreButton!!.setOnClickListener {
            val action = TrackCollectionFragmentDirections.loadMoreTracks(
                genreName = navArgs.genreName,
                size = navArgs.size,
                offset = navArgs.offset + navArgs.size
            )
            findNavController().navigate(action)
        }
    }

    private fun moreRandomTracks() {

        val listSize = navArgs.size

        moreButton!!.setOnClickListener {
            val offset = navArgs.offset + listSize

            val action = TrackCollectionFragmentDirections.loadMoreTracks(
                getRandom = true,
                size = listSize,
                offset = offset
            )
            findNavController().navigate(action)
        }
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
        refresh: Boolean
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

        val getStarredTracks = navArgs.getStarred
        val getVideos = navArgs.getVideos
        val getRandomTracks = navArgs.getRandom
        val albumListSize = navArgs.size
        val albumListOffset = navArgs.offset
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
                listModel.getSongsForGenre(genreName, albumListSize, albumListOffset)
            } else if (getStarredTracks) {
                setTitle(getString(R.string.main_songs_starred))
                listModel.getStarred()
            } else if (getVideos) {
                setTitle(R.string.main_videos)
                listModel.getVideos(refresh2)
            } else if (getRandomTracks) {
                setTitle(R.string.main_songs_random)
                listModel.getRandom(albumListSize)
            } else {
                setTitle(name)
                if (ActiveServerProvider.isID3Enabled()) {
                    if (isAlbum) {
                        listModel.getAlbum(refresh2, id!!, name)
                    } else {
                        throw IllegalAccessException("Use AlbumFragment instead!")
                    }
                } else {
                    listModel.getMusicDirectory(refresh2, id!!, name)
                }
            }

            refreshListView?.isRefreshing = false
        }
        return listModel.currentList
    }

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
                downloadHandler.download(
                    fragment = this@TrackCollectionFragment,
                    append = true,
                    save = false,
                    autoPlay = false,
                    playNext = true,
                    shuffle = false,
                    songs = songs,
                    playlistName = navArgs.playlistName
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
            R.id.select_album_play_all -> {
                // TODO: Why is this being handled here?!
                playAll()
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
                val action = TrackCollectionFragmentDirections.loadMoreTracks(
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
                enableButtons()
            }
        }
    }
}
