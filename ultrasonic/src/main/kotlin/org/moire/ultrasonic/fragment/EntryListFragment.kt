/*
 * EntryListFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.FolderSelectorBinder
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.GenericEntry
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.DownloadAction
import org.moire.ultrasonic.subsonic.DownloadHandler

/**
 * An extension of the MultiListFragment, with a few helper functions geared
 * towards the display of MusicDirectory.Entries.
 * @param T: The type of data which will be used (must extend GenericEntry)
 */
abstract class EntryListFragment<T : GenericEntry> : MultiListFragment<T>() {

    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()

    /**
     * Whether to show the folder selector
     */
    private fun showFolderHeader(): Boolean {
        return listModel.showSelectFolderHeader() && !listModel.isOffline() &&
            !ActiveServerProvider.shouldUseId3Tags()
    }

    override fun onContextMenuItemSelected(menuItem: MenuItem, item: T): Boolean {
        val isArtist = (item is Artist)

        return handleContextMenu(menuItem, item, isArtist, downloadHandler, this)
    }

    override fun onItemClick(item: T) {
        val action = EntryListFragmentDirections.entryListToTrackCollection(
            id = item.id,
            name = item.name,
            parentId = item.id,
            isArtist = (item is Artist),
        )

        findNavController().navigate(action)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Call a cheap function on ServerSettingsModel to make sure it is initialized by Koin,
        // because it can't be initialized from inside the callback
        serverSettingsModel.toString()

        rxBusSubscription += RxBus.musicFolderChangedEventObservable.subscribe {
            if (!listModel.isOffline()) {
                val currentSetting = listModel.activeServer
                currentSetting.musicFolderId = it.id
                serverSettingsModel.updateItem(currentSetting)
            }
            listModel.refresh(refreshListView!!)
        }

        viewAdapter.register(
            FolderSelectorBinder(view.context)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rxBusSubscription.dispose()
    }

    /**
     * What to do when the list has changed
     */
    override val defaultObserver: (List<T>) -> Unit = {
        emptyView.isVisible = it.isEmpty() && !(refreshListView?.isRefreshing ?: false)

        if (showFolderHeader()) {
            val list = mutableListOf<Identifiable>(folderHeader)
            list.addAll(it)
            viewAdapter.submitList(list)
        } else {
            viewAdapter.submitList(it)
        }
    }

    /**
     * Get a folder header and update it on changes
     */
    private val folderHeader: FolderSelectorBinder.FolderHeader by lazy {
        val header = FolderSelectorBinder.FolderHeader(
            listModel.musicFolders.value!!,
            listModel.activeServer.musicFolderId
        )

        listModel.musicFolders.observe(
            viewLifecycleOwner
        ) {
            header.folders = it
            viewAdapter.notifyItemChanged(0)
        }

        header
    }

    companion object {
        @Suppress("LongMethod")
        internal fun handleContextMenu(
            menuItem: MenuItem,
            item: Identifiable,
            isArtist: Boolean,
            downloadHandler: DownloadHandler,
            fragment: Fragment
        ): Boolean {
            when (menuItem.itemId) {
                R.id.menu_play_now ->
                    downloadHandler.fetchTracksAndAddToController(
                        fragment,
                        item.id,
                        append = false,
                        autoPlay = true,
                        playNext = false,
                        isArtist = isArtist
                    )
                R.id.menu_play_next ->
                    downloadHandler.fetchTracksAndAddToController(
                        fragment,
                        item.id,
                        append = false,
                        autoPlay = true,
                        playNext = true,
                        isArtist = isArtist
                    )
                R.id.menu_play_last ->
                    downloadHandler.fetchTracksAndAddToController(
                        fragment,
                        item.id,
                        append = true,
                        autoPlay = false,
                        playNext = false,
                        isArtist = isArtist
                    )
                R.id.menu_pin ->
                    downloadHandler.justDownload(
                        action = DownloadAction.PIN,
                        fragment,
                        item.id,
                        isArtist = isArtist
                    )
                R.id.menu_unpin ->
                    downloadHandler.justDownload(
                        action = DownloadAction.UNPIN,
                        fragment,
                        item.id,
                        isArtist = isArtist
                    )
                R.id.menu_download ->
                    downloadHandler.justDownload(
                        action = DownloadAction.DOWNLOAD,
                        fragment,
                        item.id,
                        isArtist = isArtist
                    )
                else -> return false
            }
            return true
        }
    }
}
