package org.moire.ultrasonic.fragment.tsshadow

import TileInfo
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import org.moire.ultrasonic.R
import org.moire.ultrasonic.NavigationGraphDirections
import TileStorage.saveTiles
import genreIconMap
import tileInfoColors
import navigateToGenre
import timber.log.Timber

class TileAdapter(
    private val tiles: MutableList<TileInfo>,
    private val context: Context,
    private val navController: NavController,
    private val pageKey: String
) : RecyclerView.Adapter<TileAdapter.TileViewHolder>() {

    inner class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tileCard: View = view.findViewById(R.id.tile_card)
        private val tileIcon: ImageView = view.findViewById(R.id.tile_icon)
        private val tileText: TextView = view.findViewById(R.id.tile_text)

        fun bind(tile: TileInfo, index: Int) {
            // Background
            val startColor = tileInfoColors[index % tileInfoColors.size]
            val endColor = Color.BLACK
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(startColor, endColor)
            ).apply { cornerRadius = 24f }
            tileCard.background = gradient

            @DrawableRes val iconRes = genreIconMap[tile.genre?.firstOrNull()] ?: R.drawable.baseline_music_note_24
            tileIcon.setImageResource(iconRes)
            tileIcon.contentDescription = tile.genre?.firstOrNull() ?: tile.title
            tileText.text = tile.title

            itemView.setOnClickListener {
                navController.navigate(navigateToGenre(tile))
            }

            itemView.setOnLongClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Tile verwijderen?")
                    .setMessage("Weet je zeker dat je '${tile.title}' wilt verwijderen?")
                    .setPositiveButton("Ja") { _, _ ->
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            tiles.removeAt(pos)
                            notifyItemRemoved(pos)
                            saveTiles(context, tiles, pageKey)
                        }
                    }
                    .setNegativeButton("Annuleren", null)
                    .show()
                true
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
        saveTiles(context, tiles, pageKey)
        notifyItemInserted(tiles.size - 1)
    }
}
