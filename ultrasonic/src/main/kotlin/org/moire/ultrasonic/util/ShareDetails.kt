package org.moire.ultrasonic.util

import org.moire.ultrasonic.domain.Track

/**
 * Created by Josh on 12/17/13.
 */
data class ShareDetails(val entries: List<Track>) {
    var description: String? = null
    var shareOnServer = false
    var expiration: Long = 0
}
