/*
 * MediaLibraryBase.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service.androidauto

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.Player
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.buildMediaItem
import timber.log.Timber

/**
 * @class MediaLibraryBase
 * @brief Provides a base class for media library components.
 *
 * This abstract class defines shared constants and utility methods used across
 * different media library handlers. It acts as a foundation for managing
 * media browsing, playback, and data retrieval in the Android Auto media session.
 *
 * @details
 * - Stores predefined media constants (e.g., media categories, genres, and commands).
 * - Provides a utility method for executing functions with error handling.
 * - Defines standardized display limits for long media lists.
 *
 * @note This class should be extended by other media-related classes to ensure consistency
 * in handling media identifiers and playback operations.
 */
abstract class MediaLibraryBase {
    companion object {
        fun <T> callWithErrorHandling(function: () -> T): T? {
            // TODO Implement better error handling
            return try {
                function()
            } catch (all: Exception) {
                Timber.i(all)
                null
            }
        }

        // Media item extensions
        fun MutableList<MediaItem>.addPlayAllItem(mediaId: String) {
            this.add(
                R.string.select_album_play_all,
                mediaId,
                null,
                false,
                icon = R.drawable.media_start
            )
        }


        fun MutableList<MediaItem>.add(
            resId: String,
            mediaId: String,
            groupNameId: Int?,
            isBrowsable: Boolean = true,
            mediaType: Int = MEDIA_TYPE_FOLDER_MIXED,
            icon: Int? = null
        ) {
            val applicationContext = UApp.applicationContext()

            val mediaItem = buildMediaItem(
                resId,
                mediaId,
                isPlayable = !isBrowsable,
                isBrowsable = isBrowsable,
                imageUri = if (icon != null) {
                    Util.getUriToDrawable(applicationContext, icon)
                } else {
                    null
                },
                group = if (groupNameId != null) {
                    applicationContext.getString(groupNameId)
                } else {
                    null
                },
                mediaType = mediaType
            )

            this.add(mediaItem)
        }

        fun MutableList<MediaItem>.add(
            title: String,
            mediaId: String,
            mediaType: Int = MEDIA_TYPE_MIXED,
            isBrowsable: Boolean = false
        ) {
            val mediaItem = buildMediaItem(
                title,
                mediaId,
                isPlayable = false,
                isBrowsable = isBrowsable,
                mediaType = mediaType
            )

            this.add(mediaItem)
        }

        fun MutableList<MediaItem>.add(
            resId: Int,
            mediaId: String,
            groupNameId: Int?,
            isBrowsable: Boolean = true,
            mediaType: Int = MEDIA_TYPE_FOLDER_MIXED,
            icon: Int? = null
        ) {
            val applicationContext = UApp.applicationContext()

            val mediaItem = buildMediaItem(
                applicationContext.getString(resId),
                mediaId,
                isPlayable = !isBrowsable,
                isBrowsable = isBrowsable,
                imageUri = if (icon != null) {
                    Util.getUriToDrawable(applicationContext, icon)
                } else {
                    null
                },
                group = if (groupNameId != null) {
                    applicationContext.getString(groupNameId)
                } else {
                    null
                },
                mediaType = mediaType
            )

            this.add(mediaItem)
        }


        fun Player.setNextRepeatMode() {
            repeatMode =
                when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
        }

        internal const val MEDIA_ROOT_ID = "MEDIA_ROOT_ID"
        internal const val MEDIA_ALBUM_ID = "MEDIA_ALBUM_ID"
        internal const val MEDIA_ALBUM_PAGE_ID = "MEDIA_ALBUM_PAGE_ID"
        internal const val MEDIA_ALBUM_NEWEST_ID = "MEDIA_ALBUM_NEWEST_ID"
        internal const val MEDIA_ALBUM_RECENT_ID = "MEDIA_ALBUM_RECENT_ID"
        internal const val MEDIA_ALBUM_FREQUENT_ID = "MEDIA_ALBUM_FREQUENT_ID"
        internal const val MEDIA_ALBUM_RANDOM_ID = "MEDIA_ALBUM_RANDOM_ID"
        internal const val MEDIA_ALBUM_STARRED_ID = "MEDIA_ALBUM_STARRED_ID"
        internal const val MEDIA_SONG_RANDOM_ID = "MEDIA_SONG_RANDOM_ID"
        internal const val MEDIA_SONG_RECENT = "MEDIA_SONG_RECENT"
        internal const val MEDIA_SONG_STARRED_ID = "MEDIA_SONG_STARRED_ID"
        internal const val MEDIA_ARTIST_ID = "MEDIA_ARTIST_ID"
        internal const val MEDIA_LIBRARY_ID = "MEDIA_LIBRARY_ID"
        internal const val MEDIA_SONGS_ID = "MEDIA_SONGS_ID"
        internal const val MEDIA_LIVESETS_ID = "MEDIA_LIVESETS_ID"
        internal const val MEDIA_PLAYLIST_ID = "MEDIA_PLAYLIST_ID"
        internal const val MEDIA_SHARE_ID = "MEDIA_SHARE_ID"
        internal const val MEDIA_BOOKMARK_ID = "MEDIA_BOOKMARK_ID"
        internal const val MEDIA_PODCAST_ID = "MEDIA_PODCAST_ID"
        internal const val MEDIA_ALBUM_ITEM = "MEDIA_ALBUM_ITEM"
        internal const val MEDIA_PLAYLIST_SONG_ITEM = "MEDIA_PLAYLIST_SONG_ITEM"
        internal const val MEDIA_PLAYLIST_ITEM = "MEDIA_PLAYLIST_ITEM"
        internal const val MEDIA_ARTIST_ITEM = "MEDIA_ARTIST_ITEM"
        internal const val MEDIA_ARTIST_SECTION = "MEDIA_ARTIST_SECTION"
        internal const val MEDIA_ALBUM_SONG_ITEM = "MEDIA_ALBUM_SONG_ITEM"
        internal const val MEDIA_SONG_STARRED_ITEM = "MEDIA_SONG_STARRED_ITEM"
        internal const val MEDIA_SONG_RANDOM_ITEM = "MEDIA_SONG_RANDOM_ITEM"
        internal const val MEDIA_SHARE_ITEM = "MEDIA_SHARE_ITEM"
        internal const val MEDIA_SHARE_SONG_ITEM = "MEDIA_SHARE_SONG_ITEM"
        internal const val MEDIA_BOOKMARK_ITEM = "MEDIA_BOOKMARK_ITEM"
        internal const val MEDIA_PODCAST_ITEM = "MEDIA_PODCAST_ITEM"
        internal const val MEDIA_PODCAST_EPISODE_ITEM = "MEDIA_PODCAST_EPISODE_ITEM"
        internal const val MEDIA_SEARCH_SONG_ITEM = "MEDIA_SEARCH_SONG_ITEM"

        // Genres -> songs
        internal const val MEDIA_GET_GENRES = "MEDIA_GET_GENRES"
        internal const val MEDIA_GET_YEARS = "MEDIA_GET_YEARS"
        internal const val MEDIA_GET_SORT_METHOD = "MEDIA_GET_SORT_METHOD"
        internal const val MEDIA_GET_SONGS_BY_GENRE = "MEDIA_GENRE_SONGS"

        // Currently the display limit for long lists is 100 items
        internal const val DISPLAY_LIMIT = 100
        internal const val SEARCH_LIMIT = 10

        // List of available custom SessionCommands
        const val PLAY_COMMAND = "play "
    }
}