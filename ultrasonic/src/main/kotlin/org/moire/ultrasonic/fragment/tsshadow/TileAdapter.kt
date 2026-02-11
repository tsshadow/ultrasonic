package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import TileStorage.saveTiles
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
import genreIconMap
import navigateToGenre
import org.moire.ultrasonic.R
import tileInfoColors

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
        private val tileCard: View = view.findViewById(R.id.tile_card)
        private val tileIcon: ImageView = view.findViewById(R.id.tile_icon)
        private val tileText: TextView = view.findViewById(R.id.tile_text)

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
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(tileInfoColors[index % tileInfoColors.size], Color.BLACK)
            ).apply { cornerRadius = TILE_CORNER_RADIUS }
            tileCard.background = gradient
        }

        private fun setupIconAndText(tile: TileInfo) {
            val iconRes =
                genreIconMap[tile.genre?.firstOrNull()] ?: R.drawable.baseline_music_note_24
            tileIcon.setImageResource(iconRes)
            tileIcon.contentDescription = tile.genre?.firstOrNull() ?: tile.title
            tileText.text = tile.title
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
        saveTiles(context, tiles, pageKey)
        notifyItemInserted(tiles.size - 1)
    }

    companion object {
        private const val TILE_CORNER_RADIUS = 24f
    }
}
