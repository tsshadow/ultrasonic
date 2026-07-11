/*
 * ArtistListModel.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.Collator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.service.MusicService
import timber.log.Timber

/**
 * Provides ViewModel which contains the list of available Artists
 */
class ArtistListModel(application: Application) : GenericListModel(application) {
    val list: MutableLiveData<List<ArtistOrIndex>> = MutableLiveData()

    /**
     * Retrieves all available Artists in a LiveData
     */
    fun getItems(refresh: Boolean, swipe: SwipeRefreshLayout): LiveData<List<ArtistOrIndex>> {
        // Don't reload the data if navigating back to the view that was active before.
        // This way, we keep the scroll position
        if (list.value?.isEmpty() != false || refresh) {
            backgroundLoadFromServer(refresh, swipe)
        }
        return list
    }

    override fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean
    ) {
        super.load(isOffline, useId3Tags, musicService, refresh)

        val musicFolderId = activeServer.musicFolderId

        val result = if (ActiveServerProvider.shouldUseId3Tags()) {
            musicService.getArtists(refresh, 0, 100)
        } else {
            musicService.getIndexes(musicFolderId, refresh)
        }

        list.postValue(result.toMutableList().sortedWith(comparator))
    }

    fun loadMore(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean,
        offset: Int,
        count: Int
    ) {
        Timber.d(if (isOffline) "Is Offline" else "Is Online")
        CoroutineScope(Dispatchers.IO).launch {
            val musicFolderId = activeServer.musicFolderId

            Timber.d("loadMore offset=$offset, count=$count")
            val result = if (useId3Tags) {
                musicService.getArtists(true, offset, count)
            } else {
                // fallback if not using ID3 tags
                musicService.getIndexes(musicFolderId, refresh).drop(offset).take(count)
            }

            val sorted = result.sortedWith(comparator)

            val current = list.value ?: emptyList()
            val combined = current + sorted

            withContext(Dispatchers.Main) {
                list.postValue(combined)
            }
        }
    }

    override fun showSelectFolderHeader(): Boolean = true

    companion object {
        val comparator: Comparator<ArtistOrIndex> =
            compareBy(Collator.getInstance()) { t -> t.name }
    }
}
