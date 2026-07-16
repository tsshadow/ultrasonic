/*
 * Scrobbler.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isScrobblingEnabled
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/**
 * Scrobbles played songs to Last.fm.
 */
class Scrobbler : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private var lastSubmission: String? = null
    private var lastNowPlaying: String? = null
    fun scrobble(song: Track?, submission: Boolean) {
        if (song == null || !isScrobblingEnabled()) return
        val id = song.id

        // Avoid duplicate registrations.
        if (submission && id == lastSubmission) return
        if (!submission && id == lastNowPlaying) return
        if (submission) lastSubmission = id else lastNowPlaying = id

        if (submission) {
            updateLastPlayedHistory(song)
            launch {
                val service = getMusicService()
                try {
                    service.scrobble(id, submission)
                    Timber.i(
                        "Scrobbled '%s' for %s",
                        "submission",
                        song
                    )
                } catch (all: Exception) {
                    Timber.i(
                        all,
                        "Failed to scrobble'%s' for %s",
                        "submission",
                        song
                    )
                }
            }
        } else {
            launch {
                val service = getMusicService()
                try {
                    service.scrobble(id, submission)
                    Timber.i(
                        "Scrobbled '%s' for %s",
                        "now playing",
                        song
                    )
                } catch (all: Exception) {
                    Timber.i(
                        all,
                        "Failed to scrobble'%s' for %s",
                        "now playing",
                        song
                    )
                }
            }
        }
    }

    private fun updateLastPlayedHistory(song: Track) {
        val isLive = song.duration != null && song.duration!! > 60 * 15 // Assuming > 15 mins is a liveset
        val gson = Gson()
        val type = object : TypeToken<MutableList<String>>() {}.type

        // Update Genres
        val genre = song.genre
        if (!genre.isNullOrBlank()) {
            val genresJson = if (isLive) Settings.lastPlayedLiveGenres else Settings.lastPlayedGenres
            val genres: MutableList<String> = gson.fromJson(genresJson, type) ?: mutableListOf()

            val splitGenres = mutableListOf<String>()
            song.genres?.forEach {
                splitGenres.addAll(it.split(';', ',', '/').map { s -> s.trim() }.filter { s -> s.isNotEmpty() })
            }
            if (splitGenres.isEmpty()) {
                splitGenres.addAll(genre.split(';', ',', '/').map { it.trim() }.filter { it.isNotEmpty() })
            }

            splitGenres.distinct().reversed().forEach { g ->
                genres.remove(g)
                genres.add(0, g)
            }

            while (genres.size > 10) genres.removeAt(genres.size - 1)
            if (isLive) Settings.lastPlayedLiveGenres = gson.toJson(genres) else Settings.lastPlayedGenres = gson.toJson(genres)
        }

        // Update Songs
        val tracksJson = Settings.lastPlayedSongs
        val tracks: MutableList<String> = gson.fromJson(tracksJson, object : TypeToken<MutableList<String>>() {}.type) ?: mutableListOf()
        tracks.remove(song.id)
        tracks.add(0, song.id)
        if (tracks.size > 20) tracks.removeAt(tracks.size - 1)
        Settings.lastPlayedSongs = gson.toJson(tracks)

        // Update Albums
        val albumId = song.albumId
        if (!albumId.isNullOrBlank()) {
            val albumsJson = Settings.lastPlayedAlbums
            val albums: MutableList<String> = gson.fromJson(albumsJson, object : TypeToken<MutableList<String>>() {}.type) ?: mutableListOf()
            albums.remove(albumId)
            albums.add(0, albumId)
            if (albums.size > 10) albums.removeAt(albums.size - 1)
            Settings.lastPlayedAlbums = gson.toJson(albums)
        }

        // Update Artists
        val artistId = song.artistId
        if (!artistId.isNullOrBlank()) {
            val artistsJson = Settings.lastPlayedArtists
            val artists: MutableList<String> = gson.fromJson(artistsJson, object : TypeToken<MutableList<String>>() {}.type) ?: mutableListOf()
            artists.remove(artistId)
            artists.add(0, artistId)
            if (artists.size > 10) artists.removeAt(artists.size - 1)
            Settings.lastPlayedArtists = gson.toJson(artists)
        }
    }
}
