/*
 * MusicService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import java.io.InputStream
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Bookmark
import org.moire.ultrasonic.domain.ChatMessage
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Custom1
import org.moire.ultrasonic.domain.Custom2
import org.moire.ultrasonic.domain.Custom3
import org.moire.ultrasonic.domain.Custom4
import org.moire.ultrasonic.domain.Custom5
import org.moire.ultrasonic.domain.Mood
import org.moire.ultrasonic.domain.Year
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.JukeboxStatus
import org.moire.ultrasonic.domain.Lyrics
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.PodcastsChannel
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Share
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.domain.UserInfo

@Suppress("TooManyFunctions")

interface MusicService {
    @Throws(Exception::class)
    fun ping()

    @Throws(Exception::class)
    fun isLicenseValid(): Boolean

    @Throws(Exception::class)
    fun getGenres(refresh: Boolean, year: String): List<Genre>?

    @Throws(Exception::class)
    fun getCustom1(refresh: Boolean): List<Custom1>?

    @Throws(Exception::class)
    fun getCustom2(refresh: Boolean): List<Custom2>?

    @Throws(Exception::class)
    fun getCustom3(refresh: Boolean): List<Custom3>?

    @Throws(Exception::class)
    fun getCustom4(refresh: Boolean): List<Custom4>?

    @Throws(Exception::class)
    fun getCustom5(refresh: Boolean): List<Custom5>?

    @Throws(Exception::class)
    fun getMoods(refresh: Boolean, year: String): List<Mood>?
    
    @Throws(Exception::class)
    fun getYears(refresh: Boolean): List<Year>?

    @Throws(Exception::class)
    fun star(id: String?, albumId: String?, artistId: String?)

    @Throws(Exception::class)
    fun unstar(id: String?, albumId: String?, artistId: String?)

    @Throws(Exception::class)
    fun setRating(id: String, rating: Int)

    @Throws(Exception::class)
    fun getMusicFolders(refresh: Boolean): List<MusicFolder>

    @Throws(Exception::class)
    fun getIndexes(musicFolderId: String?, refresh: Boolean): List<Index>

    @Throws(Exception::class)
    fun getArtists(refresh: Boolean): List<Artist>

    @Throws(Exception::class)
    fun getMusicDirectory(id: String, name: String?, refresh: Boolean): MusicDirectory

    @Throws(Exception::class)
    fun getAlbumsOfArtist(id: String, name: String?, refresh: Boolean): List<Album>

    @Throws(Exception::class)
    fun getAlbum(id: String, name: String?, refresh: Boolean): MusicDirectory

    @Throws(Exception::class)
    fun search(criteria: SearchCriteria): SearchResult?

    @Throws(Exception::class)
    fun getPlaylist(id: String, name: String): MusicDirectory

    @Throws(Exception::class)
    fun getPodcastsChannels(refresh: Boolean): List<PodcastsChannel>

    @Throws(Exception::class)
    fun getPlaylists(refresh: Boolean): List<Playlist>

    @Throws(Exception::class)
    fun createPlaylist(id: String?, name: String?, tracks: List<Track>)

    @Throws(Exception::class)
    fun deletePlaylist(id: String)

    @Throws(Exception::class)
    fun updatePlaylist(id: String, name: String?, comment: String?, pub: Boolean)

    @Throws(Exception::class)
    fun getLyrics(artist: String, title: String): Lyrics?

    @Throws(Exception::class)
    fun scrobble(id: String, submission: Boolean)

    @Throws(Exception::class)
    fun getAlbumList(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): List<Album>

    @Throws(Exception::class)
    fun getAlbumList2(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): List<Album>

    @Throws(Exception::class)
    fun getRandomSongs(size: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByGenre(genre: String, year: Int, ratingMin: Int, ratingMax: Int,  count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByCustom1(custom1: String, count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByCustom2(custom2: String, count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByCustom3(custom3: String, count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByCustom4(custom4: String, count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByCustom5(custom5: String, count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByMood(mood: String, year: Int, ratingMin: Int, ratingMax: Int,  count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByYear(year: String, count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getStarred(): SearchResult

    @Throws(Exception::class)
    fun getStarred2(): SearchResult

    /**
     * Return response [InputStream] and a [Boolean] that indicates if this response is
     * partial.
     */
    @Throws(Exception::class)
    fun getDownloadInputStream(
        song: Track,
        offset: Long,
        maxBitrate: Int,
        save: Boolean
    ): Pair<InputStream, Boolean>

    @Throws(Exception::class)
    fun getVideoUrl(id: String): String?

    @Throws(Exception::class)
    fun updateJukeboxPlaylist(ids: List<String>?): JukeboxStatus

    @Throws(Exception::class)
    fun skipJukebox(index: Int, offsetSeconds: Int): JukeboxStatus

    @Throws(Exception::class)
    fun stopJukebox(): JukeboxStatus

    @Throws(Exception::class)
    fun startJukebox(): JukeboxStatus

    @Throws(Exception::class)
    fun getJukeboxStatus(): JukeboxStatus

    @Throws(Exception::class)
    fun setJukeboxGain(gain: Float): JukeboxStatus

    @Throws(Exception::class)
    fun getShares(refresh: Boolean): List<Share>

    @Throws(Exception::class)
    fun getChatMessages(since: Long?): List<ChatMessage?>?

    @Throws(Exception::class)
    fun addChatMessage(message: String)

    @Throws(Exception::class)
    fun getBookmarks(): List<Bookmark>

    @Throws(Exception::class)
    fun deleteBookmark(id: String)

    @Throws(Exception::class)
    fun createBookmark(id: String, position: Int)

    @Throws(Exception::class)
    fun getVideos(refresh: Boolean): MusicDirectory?

    @Throws(Exception::class)
    fun getUser(username: String): UserInfo

    @Throws(Exception::class)
    fun createShare(ids: List<String>, description: String?, expires: Long?): List<Share>

    @Throws(Exception::class)
    fun deleteShare(id: String)

    @Throws(Exception::class)
    fun updateShare(id: String, description: String?, expires: Long?)

    @Throws(Exception::class)
    fun getPodcastEpisodes(podcastChannelId: String?): MusicDirectory?
}
