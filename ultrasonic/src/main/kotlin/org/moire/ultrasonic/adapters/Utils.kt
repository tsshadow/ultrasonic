package org.moire.ultrasonic.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.util.Settings

object Utils {
    @JvmStatic
    fun createPopupMenu(view: View, layout: Int = R.menu.context_menu_artist): PopupMenu {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(layout, popup.menu)

        val downloadMenuItem = popup.menu.findItem(R.id.menu_download)
        downloadMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        var shareButton = popup.menu.findItem(R.id.menu_item_share)
        shareButton?.isVisible = !ActiveServerProvider.isOffline()

        shareButton = popup.menu.findItem(R.id.song_menu_share)
        shareButton?.isVisible = !ActiveServerProvider.isOffline()

        popup.show()
        return popup
    }

    /**
     * Provides cached drawables for the UI
     */
    class ImageHelper(context: Context) {

        lateinit var errorImage: Drawable
        lateinit var pinImage: Drawable
        lateinit var downloadedImage: Drawable
        lateinit var downloadingImage: Drawable
        lateinit var playingImage: Drawable
        var theme: String

        fun rebuild(context: Context, force: Boolean = false) {
            val currentTheme = Settings.theme
            val themesMatch = theme == currentTheme
            if (!themesMatch) theme = currentTheme

            if (!themesMatch || force) {
                getDrawables(context)
            }
        }

        init {
            theme = Settings.theme
            getDrawables(context)
        }

        private fun getDrawables(context: Context) {
            pinImage = ContextCompat.getDrawable(context, R.drawable.ic_menu_pin)!!
            downloadedImage = ContextCompat.getDrawable(context, R.drawable.ic_menu_download)!!
            errorImage = ContextCompat.getDrawable(context, R.drawable.ic_baseline_error)!!
            downloadingImage = ContextCompat.getDrawable(context, R.drawable.stat_sys_download)!!
            playingImage = ContextCompat.getDrawable(context, R.drawable.ic_stat_play)!!
        }
    }

    interface SectionedBinder {
        fun getSectionName(item: Identifiable): String
    }
}
