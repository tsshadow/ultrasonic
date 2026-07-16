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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.moire.ultrasonic.NavigationGraphDirections
import android.widget.TextView
import java.util.Calendar
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumGridDelegate
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.fragment.tsshadow.TileInfo
import org.moire.ultrasonic.fragment.tsshadow.TileStorage
import org.moire.ultrasonic.fragment.tsshadow.navigateToGenre
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.model.AlbumListModel
import org.moire.ultrasonic.model.ArtistListModel
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import timber.log.Timber
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.fragment.tsshadow.navigateToGenreByName

class MainFragment :
    ScopeFragment(),
    KoinScopeComponent {

    private var binding: View? = null
    private val albumListModel: AlbumListModel by viewModel()
    private val artistListModel: ArtistListModel by viewModel()

    private lateinit var playlistAdapter: PlaylistHomeAdapter
    private lateinit var albumAdapter: AlbumHomeAdapter
    private lateinit var artistAdapter: ArtistHomeAdapter
    private lateinit var genreAdapter: GenreHomeAdapter
    private lateinit var trackAdapter: TrackHomeAdapter
    private lateinit var recentlyPlayedAdapter: RecentlyPlayedAdapter

    private val mediaPlayerManager: MediaPlayerManager by inject()

    private var rxBusSubscription = CompositeDisposable()
    private var currentFilter: Int = Settings.lastHomeFilter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.i("onCreate")
        binding = inflater.inflate(R.layout.primary, container, false)
        
        // Listen to sets mode changes from the toolbar switch
        rxBusSubscription.add(
            RxBus.setsModeChangedPublisher.subscribe { 
                loadData()
            }
        )
        
        return binding!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentTitle.setTitle(this, R.string.music_library_label)
        updateLayoutManagers(view)
        setupFilters(view)

        loadData()
    }

    private fun updateLayoutManagers(view: View) {
        val playlistRecyclerView: RecyclerView = view.findViewById(R.id.recent_playlists_recycler)
        val albumRecyclerView: RecyclerView = view.findViewById(R.id.recent_albums_recycler)
        val artistRecyclerView: RecyclerView = view.findViewById(R.id.recent_artists_recycler)
        val trackRecyclerView: RecyclerView = view.findViewById(R.id.recent_songs_recycler)

        playlistRecyclerView.layoutManager = GridLayoutManager(context, 2)
        albumRecyclerView.layoutManager = LinearLayoutManager(context)
        artistRecyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        trackRecyclerView.layoutManager = LinearLayoutManager(context)
        
        setupTrackRecyclerView(view)
        setupPlaylistsRecyclerView(view)
        setupAlbumsRecyclerView(view)
        setupArtistsRecyclerView(view)
        setupGenresRecyclerView(view)
        setupRecentlyPlayedRecyclerView(view)
    }

    private fun setupFilters(view: View) {
        val chipGroup: com.google.android.material.chip.ChipGroup = view.findViewById(R.id.filter_chip_group)
        
        // Set initial checked chip
        chipGroup.check(currentFilter)

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chip_recent
            if (currentFilter != checkedId) {
                currentFilter = checkedId
                Settings.lastHomeFilter = checkedId
                updateVisibilityByFilter()
                loadData()
            }
        }
    }

    private fun updateVisibilityByFilter() {
        val view = binding ?: return
        val recentSongsSection: View = view.findViewById(R.id.section_recent_songs)
        val playlistsSection: View = view.findViewById(R.id.section_playlists)
        val albumsSection: View = view.findViewById(R.id.section_albums)
        val artistsSection: View = view.findViewById(R.id.section_artists)
        val genresSection: View = view.findViewById(R.id.section_genres)

        // Reset visibilities
        recentSongsSection.visibility = View.VISIBLE
        playlistsSection.visibility = View.VISIBLE
        albumsSection.visibility = View.VISIBLE
        artistsSection.visibility = View.VISIBLE
        genresSection.visibility = View.GONE
        view.findViewById<View>(R.id.section_recently_played).visibility = View.VISIBLE

        when (currentFilter) {
            R.id.chip_recent -> {
                // Show everything (default)
            }
            R.id.chip_favorites -> {
                // In a real app, maybe only show favorites
                recentSongsSection.visibility = View.GONE
                artistsSection.visibility = View.GONE
                view.findViewById<View>(R.id.section_recently_played).visibility = View.GONE
            }
            R.id.chip_new -> {
                artistsSection.visibility = View.GONE
                view.findViewById<View>(R.id.section_recently_played).visibility = View.GONE
            }
            R.id.chip_genres -> {
                recentSongsSection.visibility = View.GONE
                playlistsSection.visibility = View.GONE
                albumsSection.visibility = View.GONE
                artistsSection.visibility = View.GONE
                genresSection.visibility = View.VISIBLE
                view.findViewById<View>(R.id.section_recently_played).visibility = View.GONE
            }
            R.id.chip_playlists -> {
                recentSongsSection.visibility = View.GONE
                albumsSection.visibility = View.GONE
                artistsSection.visibility = View.GONE
                view.findViewById<View>(R.id.section_recently_played).visibility = View.GONE
            }
        }
    }

    private fun setupTrackRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recent_songs_recycler)
        trackAdapter = TrackHomeAdapter { track ->
            mediaPlayerManager.playTracksAndToast(
                fragment = this,
                insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                tracks = listOf(track),
                isDirectory = false
            )
        }
        recyclerView.adapter = trackAdapter

        view.findViewById<View>(R.id.section_recent_songs_header).setOnClickListener {
            val filters = Filters().apply {
                add(Filter("LENGTH", if (Settings.isSetsMode) "long" else "short"))
                if (currentFilter == R.id.chip_favorites) {
                    add(Filter("RATING", "4")) // Rating 4 or 5
                }
            }
            val action = NavigationGraphDirections.toTrackCollection(
                name = if (Settings.isSetsMode) getString(R.string.main_sets) else getString(R.string.main_songs),
                filters = filters.toString(),
                sortMethod = "DateDescAndRelease",
                size = 500 // Increased size for "Open all"
            )
            findNavController().navigate(action)
        }
    }

    private fun setupPlaylistsRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recent_playlists_recycler)
        playlistAdapter = PlaylistHomeAdapter { item ->
            when (item) {
                is Playlist -> {
                    val action = NavigationGraphDirections.toTrackCollection(
                        id = item.id,
                        playlistId = item.id,
                        name = item.name,
                        playlistName = item.name
                    )
                    findNavController().navigate(action)
                }
                is TileInfo -> {
                    findNavController().navigate(navigateToGenre(item))
                }
            }
        }
        recyclerView.adapter = playlistAdapter

        view.findViewById<View>(R.id.section_playlists_header).setOnClickListener {
            findNavController().navigate(R.id.toSongList)
        }
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
        recyclerView.adapter = albumAdapter

        albumListModel.list.observe(viewLifecycleOwner) { albums ->
            albumAdapter.items = albums.take(15)
        }

        view.findViewById<View>(R.id.section_albums_header).setOnClickListener {
            val action = NavigationGraphDirections.toAlbumList(
                type = AlbumListType.NEWEST,
                title = getString(R.string.main_albums_newest)
            )
            findNavController().navigate(action)
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

    private fun setupGenresRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recent_genres_recycler)
        genreAdapter = GenreHomeAdapter { genre ->
            updateLastSelectedGenre(genre.name)
            findNavController().navigate(navigateToGenreByName(genre.name))
        }
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = genreAdapter
    }

    private fun updateLastSelectedGenre(genreName: String) {
        val gson = Gson()
        val type = object : TypeToken<MutableList<String>>() {}.type
        val genresJson = if (Settings.isSetsMode) Settings.lastSelectedLiveGenres else Settings.lastSelectedGenres
        val genres: MutableList<String> = gson.fromJson(genresJson, type) ?: mutableListOf()

        genreName.split(';', ',', '/').map { it.trim() }.filter { it.isNotEmpty() }.reversed().forEach { g ->
            genres.remove(g)
            genres.add(0, g)
        }

        if (genres.size > 50) {
            genres.removeAt(genres.size - 1)
        }

        if (Settings.isSetsMode) {
            Settings.lastSelectedLiveGenres = gson.toJson(genres)
        } else {
            Settings.lastSelectedGenres = gson.toJson(genres)
        }
    }

    private fun setupRecentlyPlayedRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recently_played_recycler)
        recentlyPlayedAdapter = RecentlyPlayedAdapter { item ->
            when (item) {
                is Track -> {
                    mediaPlayerManager.playTracksAndToast(
                        fragment = this,
                        insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                        tracks = listOf(item),
                        isDirectory = false
                    )
                }
                is Album -> {
                    val action = NavigationGraphDirections.toTrackCollection(
                        id = item.id,
                        name = item.title,
                        isAlbum = true
                    )
                    findNavController().navigate(action)
                }
                is Artist -> {
                    val action = NavigationGraphDirections.toArtistDetail(item.id)
                    findNavController().navigate(action)
                }
                is Playlist -> {
                    val action = NavigationGraphDirections.toTrackCollection(
                        id = item.id,
                        playlistId = item.id,
                        name = item.name,
                        playlistName = item.name
                    )
                    findNavController().navigate(action)
                }
                is TileInfo -> {
                    findNavController().navigate(navigateToGenre(item))
                }
            }
        }
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = recentlyPlayedAdapter
    }

    private fun loadData() {
        val isOffline = org.moire.ultrasonic.data.ActiveServerProvider.isOffline()
        val useId3Tags = org.moire.ultrasonic.data.ActiveServerProvider.shouldUseId3Tags()
        val musicService = getMusicService()

        // Load Recent Songs
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filters = Filters().apply {
                    add(Filter("LENGTH", if (Settings.isSetsMode) "long" else "short"))
                    if (currentFilter == R.id.chip_favorites) {
                        add(Filter("RATING", "4"))
                    }
                }
                val result = musicService.getSongs(
                    filters = filters,
                    ratingMin = if (currentFilter == R.id.chip_favorites) 4 else 0,
                    ratingMax = 5,
                    count = 10,
                    offset = 0,
                    sortMethod = "DateDescAndRelease"
                )

                withContext(Dispatchers.Main) {
                    trackAdapter.items = result.getTracks().take(10)
                    
                    // Update header title based on mode
                    val titleView: TextView? = binding?.findViewById(R.id.section_recent_songs_title)
                    titleView?.text = if (Settings.isSetsMode) 
                        getString(R.string.main_sets) else getString(R.string.main_songs)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load recent songs")
            }
        }

        // Load Playlists and Tiles
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val playlists = if (currentFilter == R.id.chip_favorites) {
                    emptyList() // Placeholder for favorites
                } else {
                    musicService.getPlaylists(false)
                }
                
                val type = if (Settings.isSetsMode) "liveset" else "song"
                val tiles = TileStorage.loadTiles(requireContext(), type)

                // Filter based on chips
                val combined: List<Any> = when (currentFilter) {
                    R.id.chip_playlists -> playlists
                    R.id.chip_favorites -> emptyList() // TODO: implement favorites
                    else -> (tiles.take(6) + playlists.take(4))
                }

                withContext(Dispatchers.Main) {
                    playlistAdapter.items = combined.take(8)
                    
                    // Keep section_playlists_title as Playlists
                    val titleView: TextView? = binding?.findViewById(R.id.section_playlists_title)
                    titleView?.text = getString(R.string.playlist_label)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load playlists")
            }
        }
        
        // Load Genres if filter is active
        if (currentFilter == R.id.chip_genres) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val genres = musicService.getGenres(false)

                    val gson = Gson()
                    val type = object : TypeToken<List<String>>() {}.type
                    val lastSelectedJson = if (Settings.isSetsMode) Settings.lastSelectedLiveGenres else Settings.lastSelectedGenres
                    val lastSelected: List<String> = gson.fromJson(lastSelectedJson, type) ?: emptyList()

                    val sortedGenres = genres.sortedWith(object : Comparator<Genre> {
                        override fun compare(g1: Genre, g2: Genre): Int {
                            val i1 = lastSelected.indexOf(g1.name)
                            val i2 = lastSelected.indexOf(g2.name)

                            if (i1 != -1 && i2 != -1) return i1.compareTo(i2)
                            if (i1 != -1) return -1
                            if (i2 != -1) return 1

                            return g2.songCount.compareTo(g1.songCount)
                        }
                    })

                    withContext(Dispatchers.Main) {
                        genreAdapter.items = sortedGenres.take(100)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load genres")
                }
            }
        }

        // Load Newest Albums
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val type = when (currentFilter) {
                    R.id.chip_favorites -> AlbumListType.STARRED
                    R.id.chip_new -> AlbumListType.NEWEST
                    else -> AlbumListType.NEWEST
                }
                albumListModel.getAlbums(type, size = 15, refresh = false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load albums")
            }
        }

        // Load Artists
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                artistListModel.load(
                    isOffline = isOffline,
                    useId3Tags = useId3Tags,
                    musicService = musicService,
                    refresh = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load artists")
            }
        }

        // Load Recently Played
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gson = Gson()
                val type = object : TypeToken<List<String>>() {}.type
                
                val genresJson = if (Settings.isSetsMode) Settings.lastPlayedLiveGenres else Settings.lastPlayedGenres
                val genres: List<String> = gson.fromJson(genresJson, type) ?: emptyList()
                
                val songsJson = Settings.lastPlayedSongs
                val songs: List<String> = gson.fromJson(songsJson, type) ?: emptyList()
                
                val albumsJson = Settings.lastPlayedAlbums
                val albums: List<String> = gson.fromJson(albumsJson, type) ?: emptyList()
                
                val artistsJson = Settings.lastPlayedArtists
                val artists: List<String> = gson.fromJson(artistsJson, type) ?: emptyList()

                val recentlyPlayed = mutableListOf<Any>()
                
                // Add up to 4 genres
                genres.flatMap { it.split(';', ',', '/') }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(4)
                    .forEach { recentlyPlayed.add(TileInfo(title = it)) }
                
                // Fetch up to 10 songs
                if (songs.isNotEmpty()) {
                    val songFilters = Filters().apply { add(Filter("ID", songs.take(10))) }
                    try {
                        val result = musicService.getSongs(songFilters, 0, 5, 10, 0, null)
                        recentlyPlayed.addAll(result.getTracks())
                    } catch (e: Exception) { Timber.e(e) }
                }
                
                // Fetch up to 4 albums
                albums.take(4).forEach { id ->
                    try {
                        val album = musicService.getAlbum(id, null, false)
                        if (album != null) recentlyPlayed.add(album)
                    } catch (e: Exception) { Timber.e(e) }
                }
                
                // Fetch up to 4 artists
                artists.take(4).forEach { id ->
                    try {
                        val artist = musicService.getArtistInfo(id)
                        if (artist != null) recentlyPlayed.add(artist)
                    } catch (e: Exception) { Timber.e(e) }
                }

                withContext(Dispatchers.Main) {
                    recentlyPlayedAdapter.items = recentlyPlayed.take(20)
                    
                    // Show section if not empty
                    val section: View? = binding?.findViewById(R.id.section_recently_played)
                    if (recentlyPlayed.isNotEmpty() && currentFilter == R.id.chip_recent) {
                        section?.visibility = View.VISIBLE
                    } else {
                        section?.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load recently played")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rxBusSubscription.clear()
        binding = null
    }
}

class PlaylistHomeAdapter(
    private val onItemClick: (Any) -> Unit
) : RecyclerView.Adapter<PlaylistHomeAdapter.ViewHolder>(), KoinComponent {
    private val imageLoaderProvider: org.moire.ultrasonic.subsonic.ImageLoaderProvider by koinInject()

    var items: List<Any> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.item_title)
        val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        val image: ImageView = view.findViewById(R.id.item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.setOnClickListener { onItemClick(item) }

        if (item is Playlist) {
            holder.name.text = item.name
            holder.subtitle.text = holder.itemView.context.getString(R.string.playlist_label)
            imageLoaderProvider.executeOn {
                it.loadImage(
                    holder.image,
                    item.id,
                    item.name,
                    true,
                    0,
                    R.drawable.ic_menu_playlists
                )
            }
        } else if (item is TileInfo) {
            holder.name.text = item.title
            holder.subtitle.text = holder.itemView.context.getString(R.string.genre)
            holder.image.setImageResource(R.drawable.ic_menu_playlists)
            holder.image.setColorFilter(org.moire.ultrasonic.fragment.tsshadow.tileInfoColors[position % org.moire.ultrasonic.fragment.tsshadow.tileInfoColors.size])
        }
    }

    override fun getItemCount() = items.size
}

class GenreHomeAdapter(
    private val onItemClick: (Genre) -> Unit
) : RecyclerView.Adapter<GenreHomeAdapter.ViewHolder>(), KoinComponent {

    var items: List<Genre> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.item_title)
        val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        val image: ImageView = view.findViewById(R.id.item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.subtitle.text = "${item.songCount} songs"
        holder.itemView.setOnClickListener { onItemClick(item) }

        holder.image.setImageResource(R.drawable.ic_menu_playlists)
        holder.image.setColorFilter(org.moire.ultrasonic.fragment.tsshadow.tileInfoColors[position % org.moire.ultrasonic.fragment.tsshadow.tileInfoColors.size])
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
        val title: TextView = view.findViewById(R.id.item_title)
        val artist: TextView = view.findViewById(R.id.item_subtitle)
        val image: ImageView = view.findViewById(R.id.item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_list_item, parent, false)
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
                true,
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
                key = org.moire.ultrasonic.util.FileUtil.getArtistArtKey(item.name, true),
                large = true,
                size = 0,
                defaultResourceId = R.drawable.ic_contact_picture
            )
        }
    }

    override fun getItemCount() = items.size
}

class TrackHomeAdapter(
    private val onItemClick: (Track) -> Unit
) : RecyclerView.Adapter<TrackHomeAdapter.ViewHolder>(), KoinComponent {
    private val imageLoaderProvider: org.moire.ultrasonic.subsonic.ImageLoaderProvider by koinInject()

    var items: List<Track> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.item_title)
        val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        val image: ImageView = view.findViewById(R.id.item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        val yearPart = if (item.year != null && item.year!! > 0) " (${item.year})" else ""
        holder.subtitle.text = "${item.artist ?: ""}$yearPart"
        holder.itemView.setOnClickListener { onItemClick(item) }

        imageLoaderProvider.executeOn {
            it.loadImage(
                holder.image,
                item.id,
                item.title,
                false,
                0,
                R.drawable.baseline_music_note_24
            )
        }
    }

    override fun getItemCount() = items.size
}

class RecentlyPlayedAdapter(
    private val onItemClick: (Any) -> Unit
) : RecyclerView.Adapter<RecentlyPlayedAdapter.ViewHolder>(), KoinComponent {
    private val imageLoaderProvider: org.moire.ultrasonic.subsonic.ImageLoaderProvider by koinInject()

    var items: List<Any> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.item_title)
        val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        val image: ImageView = view.findViewById(R.id.item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.setOnClickListener { onItemClick(item) }

        when (item) {
            is Track -> {
                holder.name.text = item.title
                holder.subtitle.text = holder.itemView.context.getString(R.string.main_songs)
                imageLoaderProvider.executeOn {
                    it.loadImage(holder.image, item.id, item.title, false, 0, R.drawable.baseline_music_note_24)
                }
            }
            is Album -> {
                holder.name.text = item.title
                holder.subtitle.text = holder.itemView.context.getString(R.string.common_album)
                imageLoaderProvider.executeOn {
                    it.loadImage(holder.image, item.id, item.title, true, 0, R.drawable.ic_menu_playlists)
                }
            }
            is Artist -> {
                holder.name.text = item.name
                holder.subtitle.text = holder.itemView.context.getString(R.string.common_artist)
                imageLoaderProvider.executeOn {
                    it.loadImage(holder.image, item.id, item.name, false, 0, R.drawable.ic_contact_picture)
                }
            }
            is Playlist -> {
                holder.name.text = item.name
                holder.subtitle.text = holder.itemView.context.getString(R.string.playlist_label)
                imageLoaderProvider.executeOn {
                    it.loadImage(holder.image, item.id, item.name, true, 0, R.drawable.ic_menu_playlists)
                }
            }
            is TileInfo -> {
                holder.name.text = item.title
                holder.subtitle.text = holder.itemView.context.getString(R.string.genre)
                holder.image.setImageResource(R.drawable.ic_menu_playlists)
                holder.image.setColorFilter(org.moire.ultrasonic.fragment.tsshadow.tileInfoColors[position % org.moire.ultrasonic.fragment.tsshadow.tileInfoColors.size])
            }
        }
    }

    override fun getItemCount() = items.size
}

