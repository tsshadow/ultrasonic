/*
 * LyricsFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Lyrics
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.BackgroundTask
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.FragmentBackgroundTask
import org.moire.ultrasonic.util.Util.applyTheme
import timber.log.Timber

/**
 * Displays the lyrics of a song
 *
 * TODO: This file has been converted from Java, but not modernized yet.
 */
class LyricsFragment : Fragment() {
    private var artistView: TextView? = null
    private var titleView: TextView? = null
    private var textView: TextView? = null
    private var swipe: SwipeRefreshLayout? = null
    private var cancellationToken: CancellationToken? = null

    private val navArgs by navArgs<LyricsFragmentArgs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.lyrics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cancellationToken = CancellationToken()
        Timber.d("Lyrics set title")
        setTitle(this, R.string.download_menu_lyrics)
        swipe = view.findViewById(R.id.lyrics_refresh)
        swipe?.isEnabled = false
        artistView = view.findViewById(R.id.lyrics_artist)
        titleView = view.findViewById(R.id.lyrics_title)
        textView = view.findViewById(R.id.lyrics_text)
        load()
    }

    override fun onDestroyView() {
        cancellationToken!!.cancel()
        super.onDestroyView()
    }

    private fun load() {
        val task: BackgroundTask<Lyrics> = object : FragmentBackgroundTask<Lyrics>(
            activity, true, swipe, cancellationToken
        ) {
            @Throws(Throwable::class)
            override fun doInBackground(): Lyrics {
                val musicService = getMusicService()
                return musicService.getLyrics(navArgs.artist, navArgs.title)!!
            }

            override fun done(result: Lyrics) {
                if (result.artist != null) {
                    artistView!!.text = result.artist
                    titleView!!.text = result.title
                    textView!!.text = result.text
                } else {
                    artistView!!.setText(R.string.lyrics_nomatch)
                }
            }
        }
        task.execute()
    }
}
