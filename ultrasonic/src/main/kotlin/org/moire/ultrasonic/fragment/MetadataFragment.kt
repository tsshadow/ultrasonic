/*
 * MetadataFragment.kt
 * Copyright (C) 2009-2025 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

/*
 * MetadataFragment.kt
 * Displays detailed metadata for a track
 */
package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.applyTheme

class MetadataFragment : Fragment() {
    private val navArgs by navArgs<MetadataFragmentArgs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.song_metadata, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.metadata_label)
        val track = navArgs.track
        view.findViewById<TextView>(R.id.meta_title).text =
            getString(R.string.meta_title, track.title ?: "")
        view.findViewById<TextView>(R.id.meta_artist).text =
            getString(R.string.meta_artist, track.artist ?: "")
        view.findViewById<TextView>(R.id.meta_album).text =
            getString(R.string.meta_album, track.album ?: "")
        view.findViewById<TextView>(R.id.meta_year).text =
            getString(R.string.meta_year, track.year?.toString() ?: "")
        view.findViewById<TextView>(R.id.meta_genre).text =
            getString(R.string.meta_genre, track.genre ?: "")
        view.findViewById<TextView>(R.id.meta_genres).text =
            getString(R.string.meta_genre, track.genres?.joinToString(",") ?: "")
        view.findViewById<TextView>(R.id.meta_track).text =
            getString(R.string.meta_track, track.track?.toString() ?: "")
        view.findViewById<TextView>(R.id.meta_duration).text =
            getString(
                R.string.meta_duration,
                Util.formatTotalDuration(track.duration?.toLong())
            )
        view.findViewById<TextView>(R.id.meta_bitrate).text =
            getString(R.string.meta_bitrate, track.bitRate?.toString() ?: "")
        view.findViewById<TextView>(R.id.meta_path).text =
            getString(R.string.meta_path, track.path ?: "")
    }
}
