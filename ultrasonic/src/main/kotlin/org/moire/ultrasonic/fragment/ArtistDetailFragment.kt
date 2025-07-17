package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumRowDelegate
import org.moire.ultrasonic.adapters.ArtistHeaderBinder
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.TextDividerBinder
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Detailed view for an artist showing discography grouped by release type and year.
 */
class ArtistDetailFragment : ScopeFragment() {

    private val args: ArtistDetailFragmentArgs by navArgs()

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val mediaPlayerManager: MediaPlayerManager by inject()

    private val headerAdapter = BaseAdapter<Identifiable>()
    private val albumAdapter = BaseAdapter<Identifiable>()
    private val epAdapter = BaseAdapter<Identifiable>()
    private val singleAdapter = BaseAdapter<Identifiable>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_artist_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.swipe_refresh_view)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        headerAdapter.register(ArtistHeaderBinder { playAll() })
        albumAdapter.register(TextDividerBinder())
        albumAdapter.register(AlbumRowDelegate(::onAlbumClick) { _: MenuItem, _: Album -> false })
        epAdapter.register(TextDividerBinder())
        epAdapter.register(AlbumRowDelegate(::onAlbumClick) { _: MenuItem, _: Album -> false })
        singleAdapter.register(TextDividerBinder())
        singleAdapter.register(AlbumRowDelegate(::onAlbumClick) { _: MenuItem, _: Album -> false })

        recyclerView.adapter = ConcatAdapter(headerAdapter, albumAdapter, epAdapter, singleAdapter)

        swipeRefresh.setOnRefreshListener { loadData() }
        loadData()
    }

    private fun playAll() {
        mediaPlayerManager.playTracksAndToast(
            fragment = this,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            id = args.artistId,
            isArtist = true
        )
    }

    private fun onAlbumClick(album: Album) {
        val action = NavigationGraphDirections.toTrackCollection(
            id = album.id,
            isAlbum = album.isDirectory,
            name = album.title,
            parentId = album.parent
        )
        findNavController().navigate(action)
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            swipeRefresh.isRefreshing = true
            val service = MusicServiceFactory.getMusicService()
            val albums = withContext(Dispatchers.IO) {
                service.getAlbumsOfArtist(args.artistId, null, true)
            }
            swipeRefresh.isRefreshing = false
            updateAdapters(albums)
        }
    }

    private fun updateAdapters(albums: List<Album>) {
        val name = albums.firstOrNull()?.artist ?: ""
        val genres = albums.flatMap { it.genres ?: listOfNotNull(it.genre) }
            .distinct().joinToString(", ")
        val header = ArtistHeaderBinder.ArtistHeader(name, genres.ifBlank { null }, null)
        headerAdapter.submitList(listOf(header))

        albumAdapter.submitList(buildSection(getString(R.string.search_albums)) { album ->
            val title = album.title ?: ""
            !title.contains("EP", true) && !title.contains("single", true)
        }.invoke(albums))

        epAdapter.submitList(buildSection(getString(R.string.artist_detail_eps)) { album ->
            album.title?.contains("EP", true) == true
        }.invoke(albums))

        singleAdapter.submitList(buildSection(getString(R.string.artist_detail_singles)) { album ->
            album.title?.contains("single", true) == true
        }.invoke(albums))
    }

    private fun buildSection(title: String, filter: (Album) -> Boolean): (List<Album>) -> List<Identifiable> {
        return { all ->
            val subset = all.filter(filter)
            if (subset.isEmpty()) emptyList() else {
                val items = mutableListOf<Identifiable>()
                items.add(TextDividerBinder.TextDivider(title))
                val sorted = subset.sortedWith(
                    compareByDescending<Album> { it.year ?: 0 }.thenBy { it.title }
                )
                var currentYear: Int? = null
                for (album in sorted) {
                    val year = album.year
                    if (year != null && year != currentYear) {
                        currentYear = year
                        items.add(TextDividerBinder.TextDivider(year.toString()))
                    }
                    items.add(album)
                }
                items
            }
        }
    }
}
