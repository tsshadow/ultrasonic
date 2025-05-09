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
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.MultiSpinnerView

/**
 * Fragment for selecting or saving tile presets for regular songs.
 * Allows filtering by genre, year, rating, and sort method.
 * Search results or saved tiles navigate to a TrackCollection.
 */
class SelectSongFragment : SelectFragment() {
    override val pageKey = "song"
    override val defaultLength = "short"

    private lateinit var labelContainer: LinearLayout
    private lateinit var labelSpinner: MultiSpinnerView

    override fun setTitle() {
        setTitle(this, R.string.main_songs_title)
    }

    override fun getAdditionalFilterParams(): FilterParams {
        val selectedLabels = labelSpinner.getSelectedItems().filter { it.isNotBlank() }

        return FilterParams(
            label = selectedLabels,
        )
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun defaultTileSet(): MutableList<TileInfo> {
        val currentYear = listOf(Year.now().value.toString())
        return mutableListOf(
            TileInfo(genre = listOf("Euphoric Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Mainstream Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Raw Hardstyle"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Hardcore"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Uptempo Hardcore"), length = defaultLength, year = currentYear),
            TileInfo(genre = listOf("Bouncy Uptempo"), length = defaultLength, year = currentYear)
        )
    }

    override fun initializeViews(view: View) {
        super.initializeViews(view)
        labelContainer = view.findViewById(R.id.select_label_container)
        labelSpinner = view.findViewById(R.id.select_label)
        labelContainer.visibility = View.VISIBLE
    }

    override fun load(refresh: Boolean) {
        super.load(refresh)
        val musicService = getMusicService()
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val labels = withContext(Dispatchers.IO) {
                musicService.getTags(refresh, "PUBLISHER", null, null)
            }
            labelSpinner.setItems(labels.map { it.name })
        }
    }
}