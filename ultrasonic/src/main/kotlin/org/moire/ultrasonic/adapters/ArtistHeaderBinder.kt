package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable

/** Binder for displaying artist information in the header section */
class ArtistHeaderBinder(private val onPlayAll: () -> Unit) : ItemViewBinder<ArtistHeaderBinder.ArtistHeader, ArtistHeaderBinder.ViewHolder>() {

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder = ViewHolder(inflater.inflate(R.layout.list_header_artist_detail, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, item: ArtistHeader) {
        holder.title.text = item.name
        holder.genres.text = item.genres
        holder.description.text = item.description
        holder.playAll.setOnClickListener { onPlayAll() }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.artist_title)
        val genres: TextView = view.findViewById(R.id.artist_genres)
        val description: TextView = view.findViewById(R.id.artist_description)
        val playAll: View = view.findViewById(R.id.artist_play_all)
    }

    data class ArtistHeader(val name: String, val genres: String?, val description: String?) : Identifiable {
        override val id: String = "artist_header"
    }
}
