/*
 * SelectLivesetFragment.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import android.view.*
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Fragment for composing and saving advanced filters to explore long-form music content,
 * such as livesets, based on genre, rating, year, sort method, and optionally festival.
 *
 * Users can perform a search or save their filter configuration as reusable "tiles."
 */
class SelectLivesetFragment : SelectFragment(){
    override val pageKey = "liveset"
    override val defaultLength = "long"
    private val festivalList = arrayListOf("All")

    override fun defaultTileSet(): MutableList<TileInfo> {
        return mutableListOf(
            TileInfo(
                genre = "Hardstyle",
                length = defaultLength,
                sortMethod = "LastWrittenDesc"
            ),
            TileInfo(
                genre = "Raw Hardstyle",
                length = defaultLength,
                sortMethod = "LastWrittenDesc"
            ),
            TileInfo(
                length = defaultLength,
                sortMethod = "LastWrittenDesc"
            ),
            TileInfo(
                genre = "Mainstream Hardcore",
                length = defaultLength,
                sortMethod = "LastWrittenDesc"
            ),
            TileInfo(
                genre = "Uptempo Hardcore",
                length = defaultLength,
                sortMethod = "LastWrittenDesc"
            ),
            TileInfo(
                genre = "Bouncy Uptempo",
                length = defaultLength,
                sortMethod = "LastWrittenDesc"
            )
        )
    }

    // Song liveset filters
    private lateinit var festivalContainer: LinearLayout
    override val additionalSpinnerIds = listOf(R.id.select_festival)
    override val additionalFilterLists: MutableList<MutableList<String>> = mutableListOf(festivalList)

    override fun setTitle() {
        setTitle(this, R.string.main_livesets_title)
    }

    override fun initializeViews(view: View) {
        super.initializeViews(view)
        festivalContainer = view.findViewById(R.id.select_festival_container)
        festivalContainer.visibility = View.VISIBLE
    }

    override fun getAdditionalFilterParams(): FilterParams {
        val festival = getSelectedOrNull(requireView().findViewById(R.id.select_festival))
        return FilterParams(festival = festival)
    }

    override fun load(refresh: Boolean) {
        super.load(refresh);
        val musicService = getMusicService()

        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            if (festivalList.size == 1 && festivalList[0] == "All") {
                val labels = withContext(Dispatchers.IO) {
                    musicService.getTags(refresh, "FESTIVAL", null, null)
                }
                festivalList.addAll(labels.map { it.name })
            }
        }
    }
}
