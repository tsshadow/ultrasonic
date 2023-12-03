package org.moire.ultrasonic.imageloader

import android.net.Uri

internal const val SCHEME = "subsonic_api"
internal const val COVER_ART_PATH = "cover_art"
internal const val AVATAR_PATH = "avatar"
internal const val QUERY_ID = "id"
internal const val SIZE = "size"
internal const val QUERY_USERNAME = "username"

/**
 * Picasso.load() only accepts an URI as parameter. Therefore we create a bogus URI, in which
 * we encode the data that we need in the RequestHandler.
 */
internal fun createLoadCoverArtRequest(entityId: String, size: Long? = 0): Uri = Uri.Builder()
    .scheme(SCHEME)
    .appendPath(COVER_ART_PATH)
    .appendQueryParameter(QUERY_ID, entityId)
    .appendQueryParameter(SIZE, size.toString())
    .build()

internal fun createLoadAvatarRequest(username: String): Uri = Uri.Builder()
    .scheme(SCHEME)
    .appendPath(AVATAR_PATH)
    .appendQueryParameter(QUERY_USERNAME, username)
    .build()
