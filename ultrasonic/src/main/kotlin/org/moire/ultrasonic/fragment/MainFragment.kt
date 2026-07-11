/*
 * MainFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.inject as koinInject
import org.moire.ultrasonic.NavigationGraphDirections
import android.widget.TextView
import java.util.Calendar
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumGridDelegate
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.model.AlbumListModel
import org.moire.ultrasonic.model.ArtistListModel
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

class MainFragment :
    ScopeFragment(),
    KoinScopeComponent {

    private var binding: View? = null
    private val albumListModel: AlbumListModel by viewModel()
    private val artistListModel: ArtistListModel by viewModel()

    private lateinit var playlistAdapter: PlaylistHomeAdapter
    private lateinit var albumAdapter: AlbumHomeAdapter
    private lateinit var artistAdapter: ArtistHomeAdapter

    private var rxBusSubscription = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.i("onCreate")
        binding = inflater.inflate(R.layout.primary, container, false)
        return binding!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentTitle.setTitle(this, R.string.music_library_label)

        val welcomeTextView: TextView = binding!!.findViewById(R.id.welcome_message)
        welcomeTextView.text = getWelcomeMessage()

        setupPlaylistsRecyclerView(view)
        setupAlbumsRecyclerView(view)
        setupArtistsRecyclerView(view)

        loadData()
    }

    private fun setupPlaylistsRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recent_playlists_recycler)
        playlistAdapter = PlaylistHomeAdapter { playlist ->
            val action = NavigationGraphDirections.toTrackCollection(
                id = playlist.id,
                playlistId = playlist.id,
                name = playlist.name,
                playlistName = playlist.name
            )
            findNavController().navigate(action)
        }
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = playlistAdapter
    }

    private fun setupAlbumsRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recent_albums_recycler)
        albumAdapter = AlbumHomeAdapter { album ->
            val action = NavigationGraphDirections.toTrackCollection(
                id = album.id,
                name = album.title,
                isAlbum = true
            )
            findNavController().navigate(action)
        }
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = albumAdapter

        albumListModel.list.observe(viewLifecycleOwner) { albums ->
            albumAdapter.items = albums.take(6)
        }
    }

    private fun setupArtistsRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recent_artists_recycler)
        artistAdapter = ArtistHomeAdapter { artist ->
            val action = NavigationGraphDirections.toArtistDetail(artist.id)
            findNavController().navigate(action)
        }
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recyclerView.adapter = artistAdapter

        artistListModel.list.observe(viewLifecycleOwner) { artists ->
            // Filter out indexes, only show artists
            artistAdapter.items = artists.filterIsInstance<Artist>().take(10)
        }
    }

    private fun loadData() {
        // Load Playlists
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val playlists = getMusicService().getPlaylists(false)
                withContext(Dispatchers.Main) {
                    playlistAdapter.items = playlists.take(6)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load playlists")
            }
        }
        // Load Newest Albums
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            albumListModel.getAlbums(AlbumListType.NEWEST, size = 10, refresh = false)
        }

        // Load Artists
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            artistListModel.load(
                isOffline = org.moire.ultrasonic.data.ActiveServerProvider.isOffline(),
                useId3Tags = org.moire.ultrasonic.data.ActiveServerProvider.shouldUseId3Tags(),
                musicService = getMusicService(),
                refresh = false
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rxBusSubscription.clear()
        binding = null
    }

    private fun getWelcomeMessage(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val resId = when (hour) {
            in 0..11 -> R.string.welcome_morning
            in 12..16 -> R.string.welcome_afternoon
            in 17..20 -> R.string.welcome_evening
            else -> R.string.welcome_night
        }
        return getString(resId)
    }
}

class PlaylistHomeAdapter(
    private val onItemClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistHomeAdapter.ViewHolder>(), KoinComponent {
    private val imageLoaderProvider: org.moire.ultrasonic.subsonic.ImageLoaderProvider by koinInject()

    var items: List<Playlist> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.playlist_name)
        val image: ImageView = view.findViewById(R.id.playlist_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.itemView.setOnClickListener { onItemClick(item) }

        imageLoaderProvider.executeOn {
            it.loadImage(
                holder.image,
                item.id,
                item.name,
                false,
                0,
                R.drawable.ic_menu_playlists
            )
        }
    }

    override fun getItemCount() = items.size
}

class AlbumHomeAdapter(
    private val onItemClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumHomeAdapter.ViewHolder>(), KoinComponent {
    private val imageLoaderProvider: org.moire.ultrasonic.subsonic.ImageLoaderProvider by koinInject()

    var items: List<Album> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.album_title)
        val artist: TextView = view.findViewById(R.id.album_artist)
        val image: ImageView = view.findViewById(R.id.cover_art)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item_album, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.artist.text = item.artist
        holder.itemView.setOnClickListener { onItemClick(item) }

        imageLoaderProvider.executeOn {
            it.loadImage(
                holder.image,
                item,
                false,
                0,
                R.drawable.unknown_album
            )
        }
    }

    override fun getItemCount() = items.size
}

class ArtistHomeAdapter(
    private val onItemClick: (Artist) -> Unit
) : RecyclerView.Adapter<ArtistHomeAdapter.ViewHolder>(), KoinComponent {
    private val imageLoaderProvider: org.moire.ultrasonic.subsonic.ImageLoaderProvider by koinInject()

    var items: List<Artist> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.row_artist_name)
        val image: ImageView = view.findViewById(R.id.cover_art)
        val section: TextView = view.findViewById(R.id.row_section)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_artist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.section.visibility = View.GONE
        holder.itemView.setOnClickListener { onItemClick(item) }

        imageLoaderProvider.executeOn {
            it.loadImage(
                view = holder.image,
                id = item.coverArt,
                key = org.moire.ultrasonic.util.FileUtil.getArtistArtKey(item.name, false),
                large = false,
                size = 0,
                defaultResourceId = R.drawable.ic_contact_picture
            )
        }
    }

    override fun getItemCount() = items.size
}

