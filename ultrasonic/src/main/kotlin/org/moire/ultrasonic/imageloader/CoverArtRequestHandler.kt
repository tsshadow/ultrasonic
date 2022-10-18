package org.moire.ultrasonic.imageloader

import com.squareup.picasso.Picasso.LoadedFrom.DISK
import com.squareup.picasso.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import java.io.IOException
import okio.source
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.util.FileUtil.SUFFIX_LARGE
import org.moire.ultrasonic.util.FileUtil.SUFFIX_SMALL

/**
 * Loads cover arts from subsonic api.
 */
class CoverArtRequestHandler(private val client: SubsonicAPIClient) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
        return with(data.uri) {
            scheme == SCHEME &&
                path == "/$COVER_ART_PATH"
        }
    }

    override fun load(request: Request, networkPolicy: Int): Result {
        val id = request.uri.getQueryParameter(QUERY_ID)
            ?: throw IllegalArgumentException("Nullable id")
        val size = request.uri.getQueryParameter(SIZE)?.toLong()

        // Check if we have a hit in the disk cache
        // First check for a large and fallback to the small size.
        // because scaling down a larger size image on the device is quicker than
        // requesting the down-sized image from the network.
        val key = request.stableKey!!
        val largeKey = key.replace(SUFFIX_SMALL, SUFFIX_LARGE)
        var cache = BitmapUtils.getAlbumArtBitmapFromDisk(largeKey, size?.toInt())
        if (cache == null && key != largeKey) {
            cache = BitmapUtils.getAlbumArtBitmapFromDisk(key, size?.toInt())
        }
        if (cache != null) {
            return Result(cache, DISK)
        }

        // Cancel early if we are offline
        if (client.isOffline) {
            throw UnsupportedOperationException()
        }

        // Try to fetch the image from the API
        // Inverted call order, because Mockito has problems with chained calls.
        val response = client.toStreamResponse(client.api.getCoverArt(id, size).execute())

        // Handle the response
        if (!response.hasError() && response.stream != null) {
            return Result(response.stream!!.source(), NETWORK)
        }

        // Throw an error if still not successful
        throw IOException("${response.apiError}")
    }
}
