package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.junit.Test

/** Tests for [Album.isEP] and [Album.isSingle]. */
class AlbumExtensionsTest {
    @Test
    fun `should identify EP`() {
        val album = Album(
            id = "1",
            songCount = 6,
            duration = 29 * 60,
        )
        album.isEP() `should be equal to` true
    }

    @Test
    fun `should identify non EP when too long`() {
        val album = Album(
            id = "2",
            songCount = 6,
            duration = 31 * 60,
        )
        album.isEP() `should be equal to` false
    }

    @Test
    fun `should identify single`() {
        val album = Album(id = "3", songCount = 1, duration = 200)
        album.isSingle() `should be equal to` true
    }

    @Test
    fun `should not treat multi-track album as single`() {
        val album = Album(id = "4", songCount = 2, duration = 400)
        album.isSingle() `should be equal to` false
    }
}

