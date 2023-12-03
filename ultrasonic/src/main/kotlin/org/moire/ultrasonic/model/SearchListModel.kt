package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Settings

class SearchListModel(application: Application) : GenericListModel(application) {

    var searchResult: MutableLiveData<SearchResult?> = MutableLiveData()

    suspend fun search(query: String): SearchResult? {
        val maxArtists = Settings.maxArtists
        val maxAlbums = Settings.maxAlbums
        val maxSongs = Settings.maxSongs

        return withContext(Dispatchers.IO) {
            val criteria = SearchCriteria(query, maxArtists, maxAlbums, maxSongs)
            val service = MusicServiceFactory.getMusicService()
            val result = service.search(criteria)

            if (result != null) searchResult.postValue(result)
            result
        }
    }

    fun trimResultLength(
        result: SearchResult,
        maxArtists: Int = Settings.defaultArtists,
        maxAlbums: Int = Settings.defaultAlbums,
        maxSongs: Int = Settings.defaultSongs
    ): SearchResult {
        return SearchResult(
            artists = result.artists.take(maxArtists),
            albums = result.albums.take(maxAlbums),
            songs = result.songs.take(maxSongs)
        )
    }
}
