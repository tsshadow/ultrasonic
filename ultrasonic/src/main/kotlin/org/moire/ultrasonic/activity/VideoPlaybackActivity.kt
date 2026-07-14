/*
 * VideoPlaybackActivity.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.moire.ultrasonic.R

/**
 * Simple activity used to play video content using ExoPlayer.
 */
class VideoPlaybackActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_playback)

        val url = intent.getStringExtra(EXTRA_URL)
        val view = findViewById<PlayerView>(R.id.video_view)

        if (url != null) {
            player = ExoPlayer.Builder(this).build().also { exoPlayer ->
                view.player = exoPlayer
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_URL = "EXTRA_URL"
    }
}
