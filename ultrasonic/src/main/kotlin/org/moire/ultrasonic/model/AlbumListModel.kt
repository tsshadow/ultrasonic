package org.moire.ultrasonic.model

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings

class AlbumListModel(application: Application) : GenericListModel(application) {

    val list: MutableLiveData<List<Album>> = MutableLiveData()
    var lastType: String? = null
    private var loadedUntil: Int = 0

    fun getAlbumList(
        refresh: Boolean,
        swipe: SwipeRefreshLayout,
        args: Bundle
    ): LiveData<List<Album>> {
        // Don't reload the data if navigating back to the view that was active before.
        // This way, we keep the scroll position
        val albumListType = args.getString(Constants.INTENT_ALBUM_LIST_TYPE)!!

        if (refresh || list.value?.isEmpty() != false || albumListType != lastType) {
            lastType = albumListType
            backgroundLoadFromServer(refresh, swipe, args)
        }
        return list
    }

    private fun getAlbumsOfArtist(
        musicService: MusicService,
        refresh: Boolean,
        id: String,
        name: String?
    ) {
        list.postValue(musicService.getAlbumsOfArtist(id, name, refresh))
    }

    override fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean,
        args: Bundle
    ) {
        super.load(isOffline, useId3Tags, musicService, refresh, args)

        val albumListType = args.getString(Constants.INTENT_ALBUM_LIST_TYPE)!!
        val size = args.getInt(Constants.INTENT_ALBUM_LIST_SIZE, 0)
        var offset = args.getInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0)
        val append = args.getBoolean(Constants.INTENT_APPEND, false)

        val musicDirectory: List<Album>
        val musicFolderId = if (showSelectFolderHeader(args)) {
            activeServerProvider.getActiveServer().musicFolderId
        } else {
            null
        }

        // If we are refreshing the random list, we want to avoid items moving across the screen,
        // by clearing the list first
        if (refresh && !append && albumListType == "random") {
            list.postValue(listOf())
        }

        // Handle the logic for endless scrolling:
        // If appending the existing list, set the offset from where to load
        if (append) offset += (size + loadedUntil)

        if (albumListType == Constants.ALBUMS_OF_ARTIST) {
            return getAlbumsOfArtist(
                musicService,
                refresh,
                args.getString(Constants.INTENT_ID, ""),
                args.getString(Constants.INTENT_NAME, "")
            )
        }

        val type = AlbumListType.fromName(albumListType)

        if (useId3Tags) {
            musicDirectory =
                musicService.getAlbumList2(
                    type, size,
                    offset, musicFolderId
                )
        } else {
            musicDirectory = musicService.getAlbumList(
                type, size,
                offset, musicFolderId
            )
        }

        currentListIsSortable = isCollectionSortable(type)

        if (append && list.value != null) {
            val newList = ArrayList<Album>()
            newList.addAll(list.value!!)
            newList.addAll(musicDirectory)
            list.postValue(newList)
        } else {
            list.postValue(musicDirectory)
        }

        loadedUntil = offset
    }

    override fun showSelectFolderHeader(args: Bundle?): Boolean {
        if (args == null) return false

        val albumListType =
            AlbumListType.fromName(args.getString(Constants.INTENT_ALBUM_LIST_TYPE)!!)

        val isAlphabetical = (albumListType == AlbumListType.SORTED_BY_NAME) ||
            (albumListType == AlbumListType.SORTED_BY_ARTIST)

        return !isOffline() && !Settings.shouldUseId3Tags && isAlphabetical
    }

    private fun isCollectionSortable(albumListType: AlbumListType): Boolean {
        return when (albumListType) {
            AlbumListType.RANDOM -> false
            AlbumListType.NEWEST -> false
            AlbumListType.HIGHEST -> false
            AlbumListType.FREQUENT -> false
            AlbumListType.RECENT -> false
            else -> true
        }
    }
}
