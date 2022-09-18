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
        lateinit var downloadingImage: List<Drawable>
        lateinit var playingImage: Drawable
        var theme: String

        init {
            theme = Settings.theme
            getDrawables(context)
        }

        private fun getDrawables(context: Context) {
            pinImage = ContextCompat.getDrawable(context, R.drawable.ic_menu_pin)!!
            downloadedImage =
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_0)!!
            errorImage = ContextCompat.getDrawable(context, R.drawable.ic_baseline_error)!!
            downloadingImage = listOf(
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_1)!!,
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_2)!!,
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_3)!!,
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_4)!!,
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_5)!!,
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_6)!!,
                ContextCompat.getDrawable(context, R.drawable.stat_sys_download_anim_7)!!,
            )
            playingImage = ContextCompat.getDrawable(context, R.drawable.ic_stat_play)!!
        }
    }

    interface SectionedBinder {
        fun getSectionName(item: Identifiable): String
    }
}
