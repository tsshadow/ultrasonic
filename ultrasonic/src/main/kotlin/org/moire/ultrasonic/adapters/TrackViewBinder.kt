package org.moire.ultrasonic.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track

class TrackViewBinder(
    val onItemClick: (Track, Int) -> Unit,
    val onContextMenuClick: ((MenuItem, Track) -> Boolean)? = null,
    val checkable: Boolean,
    val draggable: Boolean,
    context: Context,
    val lifecycleOwner: LifecycleOwner,
) : ItemViewBinder<Identifiable, TrackViewHolder>(), KoinComponent {

    var startDrag: ((TrackViewHolder) -> Unit)? = null

    // Set our layout files
    val layout = R.layout.list_item_track
    private val contextMenuLayout = R.menu.context_menu_track

    private val imageHelper: Utils.ImageHelper = Utils.ImageHelper(context)

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): TrackViewHolder {
        return TrackViewHolder(inflater.inflate(layout, parent, false))
    }

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("LongMethod")
    override fun onBindViewHolder(holder: TrackViewHolder, item: Identifiable) {
        val diffAdapter = adapter as BaseAdapter<*>

        val track: Track = when (item) {
            is Track -> {
                item
            }
            else -> {
                return
            }
        }

        holder.imageHelper = imageHelper

        // Remove observer before binding
        holder.observableChecked.removeObservers(lifecycleOwner)

        holder.setSong(
            song = track,
            checkable = checkable,
            draggable = draggable,
            diffAdapter.isSelected(item.longId)
        )

        holder.itemView.setOnLongClickListener {
            if (onContextMenuClick != null) {
                val popup = Utils.createPopupMenu(holder.itemView, contextMenuLayout)

                popup.setOnMenuItemClickListener { menuItem ->
                    onContextMenuClick.invoke(menuItem, track)
                }
            } else {
                // Minimize or maximize the Text view (if song title is very long)
                if (!track.isDirectory) {
                    holder.maximizeOrMinimize()
                }
            }

            true
        }

        holder.itemView.setOnClickListener {
            if (checkable && !track.isVideo) {
                val nowChecked = !holder.check.isChecked
                holder.isChecked = nowChecked
            } else {
                onItemClick(track, holder.bindingAdapterPosition)
            }
        }

        holder.drag.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startDrag?.invoke(holder)
            }
            false
        }

        // Notify the adapter of selection changes
        holder.observableChecked.observe(
            lifecycleOwner
        ) { isCheckedNow ->
            if (isCheckedNow) {
                diffAdapter.notifySelected(holder.entry!!.longId)
            } else {
                diffAdapter.notifyUnselected(holder.entry!!.longId)
            }
        }

        // Listen to changes in selection status and update ourselves
        diffAdapter.selectionRevision.observe(
            lifecycleOwner
        ) {
            val newStatus = diffAdapter.isSelected(item.longId)

            if (newStatus != holder.check.isChecked) holder.check.isChecked = newStatus
        }
    }

    override fun onViewRecycled(holder: TrackViewHolder) {
        holder.dispose()
        super.onViewRecycled(holder)
    }
}
