package org.moire.ultrasonic.domain

/**
 * Helper extension functions for [Album] classification.
 */

private const val EP_MAX_TRACKS = 6
private const val EP_MAX_DURATION_SECONDS = 30 * 60

/** Returns true if this album should be treated as an EP. */
fun Album.isEP(): Boolean {
    val tracks = this.songCount ?: return false
    val totalDuration = this.duration ?: 0
    return tracks <= EP_MAX_TRACKS && totalDuration < EP_MAX_DURATION_SECONDS
}

/** Returns true if this album consists of exactly one track. */
fun Album.isSingle(): Boolean {
    val tracks = this.songCount ?: return false
    return tracks == 1L
}
