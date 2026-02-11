package org.moire.ultrasonic.fragment

import androidx.fragment.app.Fragment
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.isEP
import org.moire.ultrasonic.domain.isSingle

/**
 * Fragment showing details for a single artist including discography lists.
 * This implementation is minimal and focuses on separating albums by type.
 */
class ArtistDetailFragment : Fragment() {

    /**
     * Splits a list of [Album]s into albums, EPs and singles.
     */
    fun splitDiscography(albums: List<Album>): Triple<List<Album>, List<Album>, List<Album>> {
        val fullAlbums = mutableListOf<Album>()
        val eps = mutableListOf<Album>()
        val singles = mutableListOf<Album>()

        albums.forEach { album ->
            when {
                album.isSingle() -> singles += album
                album.isEP() -> eps += album
                else -> fullAlbums += album
            }
        }
        return Triple(fullAlbums, eps, singles)
    }
}
