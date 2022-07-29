/*
 * MediaItemConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media.utils.MediaConstants
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.StarRating
import java.text.DateFormat
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.provider.AlbumArtContentProvider

object MediaItemConverter {
    private const val CACHE_SIZE = 250
    private const val CACHE_EXPIRY_MINUTES = 10L
    val mediaItemCache: LRUCache<String, TimeLimitedCache<MediaItem>> = LRUCache(CACHE_SIZE)
    val trackCache: LRUCache<String, TimeLimitedCache<Track>> = LRUCache(CACHE_SIZE)

    /**
     * Adds a MediaItem to the cache with default expiry time
     */
    fun addToCache(key: String, item: MediaItem) {
        val cache: TimeLimitedCache<MediaItem> = TimeLimitedCache(CACHE_EXPIRY_MINUTES)
        cache.set(item)
        mediaItemCache.put(key, cache)
    }

    /**
     * Add a Track object to the cache with default expiry time
     */
    fun addToCache(key: String, item: Track) {
        val cache: TimeLimitedCache<Track> = TimeLimitedCache(CACHE_EXPIRY_MINUTES)
        cache.set(item)
        trackCache.put(key, cache)
    }
}

/**
 * Extension function to convert a Track to an MediaItem, using the cache if possible
 */
@Suppress("LongMethod")
fun Track.toMediaItem(
    mediaId: String = id,
): MediaItem {

    // Check Cache
    val cachedItem = MediaItemConverter.mediaItemCache[mediaId]?.get()
    if (cachedItem != null) return cachedItem

    // No cache hit, generate it
    val filePath = FileUtil.getSongFile(this)
    val bitrate = Settings.maxBitRate
    val uri = "$id|$bitrate|$filePath"

    val artworkUri = AlbumArtContentProvider.mapArtworkToContentProviderUri(this)

    val mediaItem = buildMediaItem(
        title = title ?: "",
        mediaId = mediaId,
        isPlayable = !isDirectory,
        folderType = if (isDirectory) MediaMetadata.FOLDER_TYPE_TITLES
        else MediaMetadata.FOLDER_TYPE_NONE,
        album = album,
        artist = artist,
        genre = genre,
        sourceUri = uri.toUri(),
        imageUri = artworkUri,
        starred = starred,
        group = null
    )

    val metadataBuilder = mediaItem.mediaMetadata.buildUpon()
        .setTrackNumber(track)
        .setReleaseYear(year)
        .setTotalTrackCount(songCount?.toInt())
        .setDiscNumber(discNumber)

    mediaItem.mediaMetadata.extras?.putInt("serverId", serverId)
    mediaItem.mediaMetadata.extras?.putString("parent", parent)
    mediaItem.mediaMetadata.extras?.putString("albumId", albumId)
    mediaItem.mediaMetadata.extras?.putString("artistId", artistId)
    mediaItem.mediaMetadata.extras?.putString("contentType", contentType)
    mediaItem.mediaMetadata.extras?.putString("suffix", suffix)
    mediaItem.mediaMetadata.extras?.putString("transcodedContentType", transcodedContentType)
    mediaItem.mediaMetadata.extras?.putString("transcodedSuffix", transcodedSuffix)
    mediaItem.mediaMetadata.extras?.putString("coverArt", coverArt)
    if (size != null) mediaItem.mediaMetadata.extras?.putLong("size", size!!)
    if (duration != null) mediaItem.mediaMetadata.extras?.putInt("duration", duration!!)
    if (bitRate != null) mediaItem.mediaMetadata.extras?.putInt("bitRate", bitRate!!)
    mediaItem.mediaMetadata.extras?.putString("path", path)
    mediaItem.mediaMetadata.extras?.putBoolean("isVideo", isVideo)
    mediaItem.mediaMetadata.extras?.putBoolean("starred", starred)
    mediaItem.mediaMetadata.extras?.putString("type", type)
    if (created != null) mediaItem.mediaMetadata.extras?.putString(
        "created", DateFormat.getDateInstance().format(created!!)
    )
    mediaItem.mediaMetadata.extras?.putInt("closeness", closeness)
    mediaItem.mediaMetadata.extras?.putInt("bookmarkPosition", bookmarkPosition)
    mediaItem.mediaMetadata.extras?.putString("name", name)

    if (userRating != null) {
        mediaItem.mediaMetadata.extras?.putInt("userRating", userRating!!)
        metadataBuilder.setUserRating(StarRating(5, userRating!!.toFloat()))
    }
    if (averageRating != null) {
        mediaItem.mediaMetadata.extras?.putFloat("averageRating", averageRating!!)
        metadataBuilder.setOverallRating(StarRating(5, averageRating!!))
    }

    val item = mediaItem.buildUpon().setMediaMetadata(metadataBuilder.build()).build()

    // Add MediaItem and Track to the cache
    MediaItemConverter.addToCache(mediaId, item)
    MediaItemConverter.addToCache(mediaId, this)

    return item
}

/**
 * Extension function to convert a MediaItem to a Track, using the cache if possible
 */
@Suppress("ComplexMethod")
fun MediaItem.toTrack(): Track {

    // Check Cache
    val cachedTrack = MediaItemConverter.trackCache[mediaId]?.get()
    if (cachedTrack != null) return cachedTrack

    // No cache hit, generate it
    val created = mediaMetadata.extras?.getString("created")
    val createdDate = if (created != null) DateFormat.getDateInstance().parse(created) else null

    val track = Track(
        mediaId,
        mediaMetadata.extras?.getInt("serverId") ?: -1,
        mediaMetadata.extras?.getString("parent"),
        !(mediaMetadata.isPlayable ?: true),
        mediaMetadata.title as String?,
        mediaMetadata.albumTitle as String?,
        mediaMetadata.extras?.getString("albumId"),
        mediaMetadata.artist as String?,
        mediaMetadata.extras?.getString("artistId"),
        mediaMetadata.trackNumber,
        mediaMetadata.releaseYear,
        mediaMetadata.genre as String?,
        mediaMetadata.extras?.getString("contentType"),
        mediaMetadata.extras?.getString("suffix"),
        mediaMetadata.extras?.getString("transcodedContentType"),
        mediaMetadata.extras?.getString("transcodedSuffix"),
        mediaMetadata.extras?.getString("coverArt"),
        if (mediaMetadata.extras?.containsKey("size") == true)
            mediaMetadata.extras?.getLong("size") else null,
        mediaMetadata.totalTrackCount?.toLong(),
        if (mediaMetadata.extras?.containsKey("duration") == true)
            mediaMetadata.extras?.getInt("duration") else null,
        if (mediaMetadata.extras?.containsKey("bitRate") == true)
            mediaMetadata.extras?.getInt("bitRate") else null,
        mediaMetadata.extras?.getString("path"),
        mediaMetadata.extras?.getBoolean("isVideo") ?: false,
        mediaMetadata.extras?.getBoolean("starred", false) ?: false,
        mediaMetadata.discNumber,
        mediaMetadata.extras?.getString("type"),
        createdDate,
        mediaMetadata.extras?.getInt("closeness", 0) ?: 0,
        mediaMetadata.extras?.getInt("bookmarkPosition", 0) ?: 0,
        mediaMetadata.extras?.getInt("userRating", 0) ?: 0,
        mediaMetadata.extras?.getFloat("averageRating", 0F) ?: 0F,
        mediaMetadata.extras?.getString("name"),
    )
    if (mediaMetadata.userRating is HeartRating) {
        track.starred = (mediaMetadata.userRating as HeartRating).isHeart
    }

    // Add MediaItem and Track to the cache
    MediaItemConverter.addToCache(mediaId, track)
    MediaItemConverter.addToCache(mediaId, this)

    return track
}

fun MediaItem.setPin(pin: Boolean) {
    this.mediaMetadata.extras?.putBoolean("pin", pin)
}

fun MediaItem.shouldBePinned(): Boolean {
    return this.mediaMetadata.extras?.getBoolean("pin") ?: false
}

/**
 * Build a new MediaItem from a list of attributes.
 * Especially useful to create folder entries in the Auto interface.
 */
@Suppress("LongParameterList")
fun buildMediaItem(
    title: String,
    mediaId: String,
    isPlayable: Boolean,
    @MediaMetadata.FolderType folderType: Int,
    album: String? = null,
    artist: String? = null,
    genre: String? = null,
    sourceUri: Uri? = null,
    imageUri: Uri? = null,
    starred: Boolean = false,
    group: String? = null
): MediaItem {

    val metadataBuilder = MediaMetadata.Builder()
        .setAlbumTitle(album)
        .setTitle(title)
        .setSubtitle(artist) // Android Auto only displays this field with Title
        .setArtist(artist)
        .setAlbumArtist(artist)
        .setGenre(genre)
        .setUserRating(HeartRating(starred))
        .setFolderType(folderType)
        .setIsPlayable(isPlayable)

    if (imageUri != null) {
        metadataBuilder.setArtworkUri(imageUri)
    }

    if (group != null) {
        metadataBuilder.setExtras(
            Bundle().apply {
                putString(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                    group
                )
            }
        )
    } else metadataBuilder.setExtras(Bundle())

    val metadata = metadataBuilder.build()

    val mediaItemBuilder = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(metadata)
        .setUri(sourceUri)

    if (sourceUri != null) {
        mediaItemBuilder.setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(sourceUri)
                .build()
        )
    }

    return mediaItemBuilder.build()
}
