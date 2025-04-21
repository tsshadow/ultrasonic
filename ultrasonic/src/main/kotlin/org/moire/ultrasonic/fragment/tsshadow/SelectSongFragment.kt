/*
 * SelectSongFragment.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import android.os.Build
import android.view.*
import java.time.Year
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Fragment for selecting or saving tile presets for regular songs.
 * Allows filtering by genre, year, rating, and sort method.
 * Search results or saved tiles navigate to a TrackCollection.
 */
class SelectSongFragment : SelectFragment(){
    override val pageKey = "song"
    override val defaultLength = "short"
    private val labelList = arrayListOf("All")

    // Song only filters
    private lateinit var labelContainer: LinearLayout
    override val additionalSpinnerIds = listOf(R.id.select_label)
    override val additionalFilterLists: MutableList<MutableList<String>> = mutableListOf(labelList)

    override fun setTitle() {
        setTitle(this, R.string.main_songs_title)
    }

    override fun getAdditionalFilterParams(): FilterParams {
        val label = getSelectedOrNull(requireView().findViewById(R.id.select_label))
        return FilterParams(label = label)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun defaultTileSet(): MutableList<TileInfo> {
        val currentYear = Year.now().value.toString()
        return mutableListOf(
            TileInfo(genre = "Euphoric Hardstyle", length = defaultLength, year = currentYear),
            TileInfo(genre = "Hardstyle", length = defaultLength, year = currentYear),
            TileInfo(genre = "Mainstream Hardstyle", length = defaultLength, year = currentYear),
            TileInfo(genre = "Raw Hardstyle", length = defaultLength, year = currentYear),
            TileInfo(genre = "Hardcore", length = defaultLength, year = currentYear),
            TileInfo(genre = "Uptempo Hardcore", length = defaultLength, year = currentYear),
            TileInfo(genre = "Bouncy Uptempo", length = defaultLength, year = currentYear)
        )
    }

    override fun initializeViews(view: View) {
        super.initializeViews(view)
        labelContainer = view.findViewById(R.id.select_label_container)
        labelContainer.visibility = View.VISIBLE
    }

    override fun load(refresh: Boolean) {
        super.load(refresh)
        val musicService = getMusicService()
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            if (labelList.size == 1 && labelList[0] == "All") {
                val labels = withContext(Dispatchers.IO) {
                    musicService.getTags(refresh, "PUBLISHER", null, null)
                }
                labelList.addAll(labels.map { it.name })
            }
        }
    }
}
