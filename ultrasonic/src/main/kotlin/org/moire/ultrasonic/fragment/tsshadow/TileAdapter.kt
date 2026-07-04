package org.moire.ultrasonic.fragment.tsshadow

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.R

interface TileAdapterCallback {
    fun onEditTile(tile: TileInfo, position: Int)
}

class TileAdapter(
    val tiles: MutableList<TileInfo>,
    private val context: Context,
    private val navController: NavController,
    private val pageKey: String,
    private val callback: TileAdapterCallback
) : RecyclerView.Adapter<TileAdapter.TileViewHolder>() {

    inner class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconContainer: View = view.findViewById(R.id.tile_icon_container)
        private val tileIcon: ImageView = view.findViewById(R.id.tile_icon)
        private val tileText: TextView = view.findViewById(R.id.tile_text)
        private val subtext: TextView = view.findViewById(R.id.tile_subtext)

        fun bind(tile: TileInfo, index: Int) {
            setupBackground(index)
            setupIconAndText(tile)

            itemView.setOnClickListener {
                navController.navigate(navigateToGenre(tile))
            }

            itemView.setOnLongClickListener {
                callback.onEditTile(tile, bindingAdapterPosition)
                true
            }
        }

        private fun setupBackground(index: Int) {
            val color = tileInfoColors[index % tileInfoColors.size]
            iconContainer.setBackgroundColor(color)
        }

        private fun setupIconAndText(tile: TileInfo) {
            val firstGenre = tile.genre?.firstOrNull { it.isNotBlank() && it != "All" }
            val iconRes = genreIconMap[firstGenre] ?: R.drawable.baseline_music_note_24
            
            tileIcon.setImageResource(iconRes)
            tileIcon.contentDescription = firstGenre ?: tile.title
            tileText.text = tile.title
            
            subtext.text = when {
                !tile.artists.isNullOrEmpty() -> {
                    val list = tile.artists
                    if (list.size > 2) "${list[0]}, ${list[1]}..." else list.joinToString(", ")
                }
                !tile.genre.isNullOrEmpty() -> {
                    val list = tile.genre.filter { it != "All" }
                    if (list.size > 2) "${list[0]}, ${list[1]}..." else list.joinToString(", ")
                }
                else -> "Dynamic Playlist"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.tsshadow_tile_layout, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        holder.bind(tiles[position], position)
    }

    override fun getItemCount(): Int = tiles.size

    fun addTile(tile: TileInfo) {
        tiles.add(tile)
        TileStorage.saveTiles(context, tiles, pageKey)
        notifyItemInserted(tiles.size - 1)
    }
}
