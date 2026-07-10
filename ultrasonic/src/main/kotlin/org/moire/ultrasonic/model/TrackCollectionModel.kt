/*
 * TrackCollectionModel.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/*
* Model for retrieving different collections of tracks from the API
*/
class TrackCollectionModel(application: Application) : GenericListModel(application) {

    val currentList: MutableLiveData<List<MusicDirectory.Child>> = MutableLiveData()
    val title: MutableLiveData<String> = MutableLiveData()
    val isLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    private var loadedUntil: Int = 0
    var hasMoreData = true
    var autoPlayExecuted = false

    data class LoadParams(
        val id: String? = null,
        val artistId: String? = null,
        val isAlbum: Boolean = false,
        val name: String? = null,
        val playlistId: String? = null,
        val podcastChannelId: String? = null,
        val playlistName: String? = null,
        val shareId: String? = null,
        val shareName: String? = null,
        val genre: String? = null,
        val artists: List<String>? = null,
        val festival: String? = null,
        val label: String? = null,
        val songs: String? = null,
        val year: String? = null,
        val length: String? = null,
        val sortMethod: String = "",
        val ratingMin: Int = 0,
        val festivalLineup: String? = null,
        val ratingMax: Int = 5,
        val minDuration: Int? = null,
        val maxDuration: Int? = null,
        val getVideos: Boolean = false,
        val size: Int = -1,
        val getRandomTracks: Boolean = false,
        val getStarredTracks: Boolean = false,
        val filtersJson: String? = null,
        val refresh: Boolean = false
    )

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun loadData(
        refresh: Boolean,
        append: Boolean,
        params: LoadParams
    ) {
        val size = if (params.size < 0) Settings.maxSongs else params.size
        val offset = if (append) loadedUntil else 0
        val refresh2 = params.refresh || refresh

        if (!append) {
            loadedUntil = 0
            hasMoreData = true
        }

        isLoading.value = true
        viewModelScope.launch(CommunicationError.getHandler(context)) {
            if (params.playlistId != null) {
                title.postValue(params.playlistName!!)
                getPlaylist(params.playlistId, params.playlistName)
            } else if (params.podcastChannelId != null) {
                title.postValue(context.getString(R.string.podcasts_label))
                getPodcastEpisodes(params.podcastChannelId)
            } else if (params.shareId != null) {
                title.postValue(params.shareName!!)
                getShare(params.shareId)
            } else if (params.getStarredTracks) {
                title.postValue(context.getString(R.string.main_songs_starred))
                getStarred()
            } else if (params.getVideos) {
                title.postValue(context.getString(R.string.main_videos))
                getVideos(refresh2)
            } else if (params.songs != null) {
                if (params.songs != "?") {
                    title.postValue(params.songs)
                } else {
                    val generatedTitle = buildString {
                        when (params.sortMethod) {
                            "AddedDesc" -> append("Recent ")
                            "Random" -> append("Random ")
                            "LastWrittenDesc" -> append("Recent Modified ")
                        }
                        if (!params.festival.isNullOrBlank()) append(params.festival)
                        if (!params.label.isNullOrBlank()) append(params.label)
                        if (params.artists != null && params.artists.isNotEmpty()) {
                            if (isNotEmpty()) append(" ")
                            append(
                                if (params.artists.size == 1) {
                                    params.artists.first()
                                } else {
                                    context.getString(R.string.common_artist)
                                }
                            )
                        }
                        if (!params.genre.isNullOrBlank() && params.genre != "All") {
                            if (isNotEmpty()) append(" ")
                            append(params.genre)
                        }
                        if (!params.year.isNullOrBlank() && params.year != "All") {
                            append(" (${params.year})")
                        }
                    }
                    title.postValue(generatedTitle)
                }

                val filters: Filters = params.filtersJson?.takeIf { it.isNotEmpty() }?.let {
                    Gson().fromJson(it, Filters::class.java).sanitized()
                } ?: Filters().apply {
                    params.year?.takeIf { it != "All" && it.isNotBlank() }?.let {
                        add(Filter("YEAR", it))
                    }
                    params.genre?.takeIf { it != "All" && it.isNotBlank() }?.let {
                        add(Filter("GENRE", it))
                    }
                    params.artists?.forEach { artist ->
                        if (artist.isNotBlank()) {
                            add(Filter("ARTIST", artist))
                        }
                    }
                    params.festival?.takeIf { it.isNotBlank() && it != "All" }?.let {
                        add(Filter("FESTIVAL", it))
                    }
                    params.label?.takeIf { it.isNotBlank() && it != "All" }?.let {
                        add(Filter("PUBLISHER", it))
                    }
                    params.length?.takeIf { it.isNotBlank() }?.let {
                        add(Filter("LENGTH", it))
                    }
                }
                getSongs(
                    filters,
                    params.ratingMin,
                    params.ratingMax,
                    size,
                    offset,
                    append,
                    params.sortMethod,
                    params.festivalLineup,
                    params.minDuration,
                    params.maxDuration
                )
            } else if (params.getRandomTracks) {
                title.postValue(context.getString(R.string.main_songs_random))
                getRandom(size, append)
            } else if (params.id != null) {
                if (params.name != null) {
                    title.postValue(params.name)
                }
                if (params.isAlbum) {
                    getAlbum(refresh2, params.id, params.name)
                } else {
                    getMusicDirectory(refresh2, params.id, params.name)
                }
            } else if (params.artistId != null) {
                if (params.name != null) {
                    title.postValue(params.name)
                }
                getSingles(params.artistId, refresh2)
            }

            if (append) {
                loadedUntil += size
            } else {
                loadedUntil = size
            }
            
            // Check if we have more data
            val currentItems = currentList.value ?: emptyList()
            if (currentItems.size < size + offset) {
                hasMoreData = false
            }
        }.invokeOnCompletion {
            isLoading.postValue(false)
        }
    }

    /*
     * Especially when dealing with indexes, this method can return Albums, Entries or a mix of both!
     */
    suspend fun getMusicDirectory(refresh: Boolean, id: String, name: String?) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getMusicDirectory(id, name, refresh)
            currentListIsSortable = true
            updateList(musicDirectory)
        }
    }

    suspend fun getAlbum(refresh: Boolean, id: String, name: String?) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory: MusicDirectory = service.getAlbumAsDir(id, name, refresh)
            currentListIsSortable = true
            updateList(musicDirectory)
        }
    }

    suspend fun getSongs(
        filters: Filters,
        ratingMin: Int?,
        ratingMax: Int?,
        count: Int,
        offset: Int,
        append: Boolean,
        sortMethod: String,
        festivalLineup: String?,
        minDuration: Int? = null,
        maxDuration: Int? = null
    ) {
        Timber.d(
            """
            getSongs called with:
            - filters: $filters
            - ratingMin: $ratingMin
            - ratingMax: $ratingMax
            - count: $count
            - offset: $offset
            - sortMethod: $sortMethod
            - festivalLineup: $festivalLineup
            - minDuration: $minDuration
            - maxDuration: $maxDuration
            """.trimIndent()
        )
        // Handle the logic for endless scrolling:
        // If appending the existing list, set the offset from where to load
        var newOffset = offset
        if (append) newOffset += (count + loadedUntil)
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getSongs(
                filters,
                ratingMin,
                ratingMax,
                count,
                newOffset,
                sortMethod,
                festivalLineup,
                minDuration,
                maxDuration
            )
            currentListIsSortable = false
            updateList(musicDirectory, append)

            // Update current offset
            loadedUntil = newOffset
        }
    }

    suspend fun getStarred() {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory: MusicDirectory

            musicDirectory = if (ActiveServerProvider.shouldUseId3Tags()) {
                Util.getSongsFromSearchResult(service.getStarred2())
            } else {
                Util.getSongsFromSearchResult(service.getStarred())
            }
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    suspend fun getVideos(refresh: Boolean) {
        showHeader = false

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val videos = service.getVideos(refresh)
            if (videos != null) {
                currentListIsSortable = false
                updateList(videos)
            }
        }
    }

    suspend fun getRandom(size: Int, append: Boolean) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getRandomSongs(size)
            currentListIsSortable = false
            updateList(musicDirectory, append)
        }
    }

    suspend fun getSingles(artistId: String, refresh: Boolean) {
        withContext(Dispatchers.IO) {
            Timber.d("getSingles", artistId, refresh)
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getSingles(artistId, refresh)
            currentListIsSortable = true
            updateList(musicDirectory)
        }
    }

    suspend fun getPlaylist(playlistId: String, playlistName: String) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPlaylist(playlistId, playlistName)
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    suspend fun getPodcastEpisodes(podcastChannelId: String) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPodcastEpisodes(podcastChannelId)
            if (musicDirectory != null) {
                currentListIsSortable = false
                updateList(musicDirectory)
            }
        }
    }

    suspend fun getShare(shareId: String) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = MusicDirectory()

            val shares = service.getShares(true)

            for (share in shares) {
                if (share.id == shareId) {
                    for (entry in share.getEntries()) {
                        musicDirectory.add(entry)
                    }
                    break
                }
            }
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    suspend fun getBookmarks() {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = Util.getSongsFromBookmarks(service.getBookmarks())
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    private fun updateList(root: MusicDirectory, append: Boolean = false) {
        val newList = if (append) {
            currentList.value!! + root.getChildren()
        } else {
            root.getChildren()
        }

        currentList.postValue(newList)
    }

    @Synchronized
    fun calculateButtonState(selection: List<Track>, onComplete: (ButtonStates) -> Unit) {
        val enabled = selection.isNotEmpty()
        var unpinEnabled = false
        var deleteEnabled = false
        var downloadEnabled = false
        var pinnedCount = 0

        viewModelScope.launch(Dispatchers.IO) {
            for (song in selection) {
                when (DownloadService.getDownloadState(song)) {
                    DownloadState.DONE -> {
                        deleteEnabled = true
                    }

                    DownloadState.PINNED -> {
                        deleteEnabled = true
                        pinnedCount++
                        unpinEnabled = true
                    }

                    DownloadState.IDLE, DownloadState.FAILED -> {
                        downloadEnabled = true
                    }

                    else -> {}
                }
            }
        }.invokeOnCompletion {
            val pinEnabled = selection.size > pinnedCount

            onComplete(
                ButtonStates(
                    all = enabled,
                    pin = pinEnabled,
                    unpin = unpinEnabled,
                    delete = deleteEnabled,
                    download = downloadEnabled
                )
            )
        }
    }

    companion object {
        data class ButtonStates(
            val all: Boolean,
            val pin: Boolean,
            val unpin: Boolean,
            val delete: Boolean,
            val download: Boolean
        )
    }
}
