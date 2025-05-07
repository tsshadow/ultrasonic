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
import org.moire.ultrasonic.view.MultiSpinnerView

/**
 * Fragment for composing and saving advanced filters to explore long-form music content,
 * such as livesets, based on genre, rating, year, sort method, and optionally festival.
 *
 * Users can perform a search or save their filter configuration as reusable "tiles."
 */
class SelectLivesetFragment : SelectFragment() {
    override val pageKey = "liveset"
    override val defaultLength = "long"

    private lateinit var festivalContainer: LinearLayout
    private lateinit var festivalSpinner: MultiSpinnerView

    override fun defaultTileSet(): MutableList<TileInfo> {
        return mutableListOf(
            TileInfo(genre = listOf("Hardstyle"), length = defaultLength),
            TileInfo(genre = listOf("Raw Hardstyle"), length = defaultLength),
            TileInfo(genre = listOf("Hardcore"), length = defaultLength),
            TileInfo(genre = listOf("Mainstream Hardcore"), length = defaultLength),
            TileInfo(genre = listOf("Uptempo Hardcore"), length = defaultLength),
            TileInfo(genre = listOf("Bouncy Uptempo"), length = defaultLength)
        )
    }

    override fun setTitle() {
        setTitle(this, R.string.main_livesets_title)
    }

    override fun initializeViews(view: View) {
        super.initializeViews(view)
        festivalContainer = view.findViewById(R.id.select_festival_container)
        festivalSpinner = view.findViewById(R.id.select_festival)
        festivalContainer.visibility = View.VISIBLE
    }

    override fun getAdditionalFilterParams(): FilterParams {
        val selectedFestival = festivalSpinner.getSelectedItems().firstOrNull()
        return FilterParams(festival = selectedFestival)
    }

    override fun load(refresh: Boolean) {
        super.load(refresh)

        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val musicService = getMusicService()

            val festivals = withContext(Dispatchers.IO) {
                musicService.getTags(refresh, "FESTIVAL", null, null)
            }
            festivalSpinner.setItems(festivals.map { it.name })
        }
    }
}
