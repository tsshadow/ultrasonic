package org.moire.ultrasonic.fragment;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.Artist;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.SearchCriteria;
import org.moire.ultrasonic.domain.SearchResult;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.subsonic.DownloadHandler;
import org.moire.ultrasonic.subsonic.ImageLoaderProvider;
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker;
import org.moire.ultrasonic.subsonic.ShareHandler;
import org.moire.ultrasonic.subsonic.VideoPlayer;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.MergeAdapter;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.ArtistAdapter;
import org.moire.ultrasonic.view.EntryAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlin.Lazy;
import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.inject;

public class SearchFragment extends Fragment {

    private static int DEFAULT_ARTISTS;
    private static int DEFAULT_ALBUMS;
    private static int DEFAULT_SONGS;

    private ListView list;

    private View artistsHeading;
    private View albumsHeading;
    private View songsHeading;
    private TextView searchButton;
    private View moreArtistsButton;
    private View moreAlbumsButton;
    private View moreSongsButton;
    private SearchResult searchResult;
    private MergeAdapter mergeAdapter;
    private ArtistAdapter artistAdapter;
    private ListAdapter moreArtistsAdapter;
    private EntryAdapter albumAdapter;
    private ListAdapter moreAlbumsAdapter;
    private ListAdapter moreSongsAdapter;
    private EntryAdapter songAdapter;
    private SwipeRefreshLayout searchRefresh;

    private final Lazy<VideoPlayer> videoPlayer = inject(VideoPlayer.class);
    private final Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);
    private final Lazy<ImageLoaderProvider> imageLoaderProvider = inject(ImageLoaderProvider.class);
    private final Lazy<DownloadHandler> downloadHandler = inject(DownloadHandler.class);
    private final Lazy<ShareHandler> shareHandler = inject(ShareHandler.class);
    private final Lazy<NetworkAndStorageChecker> networkAndStorageChecker = inject(NetworkAndStorageChecker.class);
    private CancellationToken cancellationToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cancellationToken = new CancellationToken();

        FragmentTitle.Companion.setTitle(this, R.string.search_title);
        setHasOptionsMenu(true);

        DEFAULT_ARTISTS = Util.getDefaultArtists(getContext());
        DEFAULT_ALBUMS = Util.getDefaultAlbums(getContext());
        DEFAULT_SONGS = Util.getDefaultSongs(getContext());

        View buttons = LayoutInflater.from(getContext()).inflate(R.layout.search_buttons, null);

        if (buttons != null)
        {
            artistsHeading = buttons.findViewById(R.id.search_artists);
            albumsHeading = buttons.findViewById(R.id.search_albums);
            songsHeading = buttons.findViewById(R.id.search_songs);
            searchButton = buttons.findViewById(R.id.search_search);
            moreArtistsButton = buttons.findViewById(R.id.search_more_artists);
            moreAlbumsButton = buttons.findViewById(R.id.search_more_albums);
            moreSongsButton = buttons.findViewById(R.id.search_more_songs);
        }

        list = view.findViewById(R.id.search_list);
        searchRefresh = view.findViewById(R.id.search_entries_refresh);
        searchRefresh.setEnabled(false); // TODO: Should this be enabled?

        list.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (view == moreArtistsButton)
                {
                    expandArtists();
                }
                else if (view == moreAlbumsButton)
                {
                    expandAlbums();
                }
                else if (view == moreSongsButton)
                {
                    expandSongs();
                }
                else
                {
                    Object item = parent.getItemAtPosition(position);
                    if (item instanceof Artist)
                    {
                        onArtistSelected((Artist) item);
                    }
                    else if (item instanceof MusicDirectory.Entry)
                    {
                        MusicDirectory.Entry entry = (MusicDirectory.Entry) item;
                        if (entry.isDirectory())
                        {
                            onAlbumSelected(entry, false);
                        }
                        else if (entry.isVideo())
                        {
                            onVideoSelected(entry);
                        }
                        else
                        {
                            onSongSelected(entry, false, true, true, false);
                        }

                    }
                }
            }
        });

        registerForContextMenu(list);

        // Fragment was started with a query, try to execute search right away
        Bundle arguments = getArguments();
        if (arguments != null) {
            String query = arguments.getString(Constants.INTENT_EXTRA_NAME_QUERY);
            boolean autoPlay = arguments.getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);

            if (query != null)
            {
                mergeAdapter = new MergeAdapter();
                list.setAdapter(mergeAdapter);
                search(query, autoPlay);
                return;
            }
        }

        // Fragment was started from the Menu, create empty list
        populateList();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        Activity activity = getActivity();
        if (activity == null) return;
        SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        Bundle arguments = getArguments();
        final boolean autoPlay = arguments != null && arguments.getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);

        inflater.inflate(R.menu.search, menu);
        MenuItem searchItem = menu.findItem(R.id.search_item);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));

        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) { return true; }

            @Override
            public boolean onSuggestionClick(int position) {
                Timber.d("onSuggestionClick: %d", position);
                Cursor cursor= searchView.getSuggestionsAdapter().getCursor();
                cursor.moveToPosition(position);
                String suggestion = cursor.getString(2); // TODO: Try to do something with this magic const -- 2 is the index of col containing suggestion name.
                searchView.setQuery(suggestion,true);
                return true;
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Timber.d("onQueryTextSubmit: %s", query);
                mergeAdapter = new MergeAdapter();
                list.setAdapter(mergeAdapter);
                search(query, autoPlay);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) { return true;  }
        });

        searchView.setIconifiedByDefault(false);
        searchItem.expandActionView();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Object selectedItem = list.getItemAtPosition(info.position);

        boolean isArtist = selectedItem instanceof Artist;
        boolean isAlbum = selectedItem instanceof MusicDirectory.Entry && ((MusicDirectory.Entry) selectedItem).isDirectory();

        if (!isArtist && !isAlbum)
        {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.select_song_context, menu);
        }
        else
        {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.select_album_context, menu);
        }

        MenuItem shareButton = menu.findItem(R.id.menu_item_share);
        MenuItem downloadMenuItem = menu.findItem(R.id.album_menu_download);

        if (downloadMenuItem != null)
        {
            downloadMenuItem.setVisible(!ActiveServerProvider.Companion.isOffline(getContext()));
        }

        if (ActiveServerProvider.Companion.isOffline(getContext()) || isArtist)
        {
            if (shareButton != null)
            {
                shareButton.setVisible(false);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();

        if (info == null)
        {
            return true;
        }

        Object selectedItem = list.getItemAtPosition(info.position);

        Artist artist = selectedItem instanceof Artist ? (Artist) selectedItem : null;
        MusicDirectory.Entry entry = selectedItem instanceof MusicDirectory.Entry ? (MusicDirectory.Entry) selectedItem : null;

        String entryId = null;

        if (entry != null)
        {
            entryId = entry.getId();
        }

        String id = artist != null ? artist.getId() : entryId;

        if (id == null)
        {
            return true;
        }

        List<MusicDirectory.Entry> songs = new ArrayList<>(1);

        switch (menuItem.getItemId())
        {
            case R.id.album_menu_play_now:
                downloadHandler.getValue().downloadRecursively(this, id, false, false, true, false, false, false, false, false);
                break;
            case R.id.album_menu_play_next:
                downloadHandler.getValue().downloadRecursively(this, id, false, true, false, true, false, true, false, false);
                break;
            case R.id.album_menu_play_last:
                downloadHandler.getValue().downloadRecursively(this, id, false, true, false, false, false, false, false, false);
                break;
            case R.id.album_menu_pin:
                downloadHandler.getValue().downloadRecursively(this, id, true, true, false, false, false, false, false, false);
                break;
            case R.id.album_menu_unpin:
                downloadHandler.getValue().downloadRecursively(this, id, false, false, false, false, false, false, true, false);
                break;
            case R.id.album_menu_download:
                downloadHandler.getValue().downloadRecursively(this, id, false, false, false, false, true, false, false, false);
                break;
            case R.id.song_menu_play_now:
                if (entry != null)
                {
                    songs = new ArrayList<>(1);
                    songs.add(entry);
                    downloadHandler.getValue().download(this, false, false, true, false, false, songs);
                }
                break;
            case R.id.song_menu_play_next:
                if (entry != null)
                {
                    songs = new ArrayList<>(1);
                    songs.add(entry);
                    downloadHandler.getValue().download(this, true, false, false, true, false, songs);
                }
                break;
            case R.id.song_menu_play_last:
                if (entry != null)
                {
                    songs = new ArrayList<>(1);
                    songs.add(entry);
                    downloadHandler.getValue().download(this, true, false, false, false, false, songs);
                }
                break;
            case R.id.song_menu_pin:
                if (entry != null)
                {
                    songs.add(entry);
                    Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_pinned, songs.size(), songs.size()));
                    downloadBackground(true, songs);
                }
                break;
            case R.id.song_menu_download:
                if (entry != null)
                {
                    songs.add(entry);
                    Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_downloaded, songs.size(), songs.size()));
                    downloadBackground(false, songs);
                }
                break;
            case R.id.song_menu_unpin:
                if (entry != null)
                {
                    songs.add(entry);
                    Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_unpinned, songs.size(), songs.size()));
                    mediaPlayerControllerLazy.getValue().unpin(songs);
                }
                break;
            case R.id.menu_item_share:
                if (entry != null)
                {
                    songs = new ArrayList<>(1);
                    songs.add(entry);
                    // TODO: Add SwipeRefresh spinner
                    shareHandler.getValue().createShare(this, songs, null, cancellationToken);
                }
            default:
                return super.onContextItemSelected(menuItem);
        }

        return true;
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void downloadBackground(final boolean save, final List<MusicDirectory.Entry> songs)
    {
        Runnable onValid = new Runnable()
        {
            @Override
            public void run()
            {
                networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();
                mediaPlayerControllerLazy.getValue().downloadBackground(songs, save);
            }
        };

        onValid.run();
    }

    private void search(final String query, final boolean autoplay)
    {
        final int maxArtists = Util.getMaxArtists(getContext());
        final int maxAlbums = Util.getMaxAlbums(getContext());
        final int maxSongs = Util.getMaxSongs(getContext());

        BackgroundTask<SearchResult> task = new TabActivityBackgroundTask<SearchResult>(getActivity(), true, searchRefresh, cancellationToken)
        {
            @Override
            protected SearchResult doInBackground() throws Throwable
            {
                SearchCriteria criteria = new SearchCriteria(query, maxArtists, maxAlbums, maxSongs);
                MusicService service = MusicServiceFactory.getMusicService(getContext());
                return service.search(criteria, getContext(), this);
            }

            @Override
            protected void done(SearchResult result)
            {
                searchResult = result;

                populateList();

                if (autoplay)
                {
                    autoplay();
                }

            }
        };
        task.execute();
    }

    private void populateList()
    {
        mergeAdapter = new MergeAdapter();

        // TODO: Remove this if the search widget can do the same
        //mergeAdapter.addView(searchButton, true);

        if (searchResult != null)
        {
            List<Artist> artists = searchResult.getArtists();
            if (!artists.isEmpty())
            {
                mergeAdapter.addView(artistsHeading);
                List<Artist> displayedArtists = new ArrayList<Artist>(artists.subList(0, Math.min(DEFAULT_ARTISTS, artists.size())));
                artistAdapter = new ArtistAdapter(getContext(), displayedArtists);
                mergeAdapter.addAdapter(artistAdapter);
                if (artists.size() > DEFAULT_ARTISTS)
                {
                    moreArtistsAdapter = mergeAdapter.addView(moreArtistsButton, true);
                }
            }

            List<MusicDirectory.Entry> albums = searchResult.getAlbums();
            if (!albums.isEmpty())
            {
                mergeAdapter.addView(albumsHeading);
                List<MusicDirectory.Entry> displayedAlbums = new ArrayList<MusicDirectory.Entry>(albums.subList(0, Math.min(DEFAULT_ALBUMS, albums.size())));
                albumAdapter = new EntryAdapter(getContext(), imageLoaderProvider.getValue().getImageLoader(), displayedAlbums, false);
                mergeAdapter.addAdapter(albumAdapter);
                if (albums.size() > DEFAULT_ALBUMS)
                {
                    moreAlbumsAdapter = mergeAdapter.addView(moreAlbumsButton, true);
                }
            }

            List<MusicDirectory.Entry> songs = searchResult.getSongs();
            if (!songs.isEmpty())
            {
                mergeAdapter.addView(songsHeading);
                List<MusicDirectory.Entry> displayedSongs = new ArrayList<MusicDirectory.Entry>(songs.subList(0, Math.min(DEFAULT_SONGS, songs.size())));
                songAdapter = new EntryAdapter(getContext(), imageLoaderProvider.getValue().getImageLoader(), displayedSongs, false);
                mergeAdapter.addAdapter(songAdapter);
                if (songs.size() > DEFAULT_SONGS)
                {
                    moreSongsAdapter = mergeAdapter.addView(moreSongsButton, true);
                }
            }

            boolean empty = searchResult.getArtists().isEmpty() && searchResult.getAlbums().isEmpty() && searchResult.getSongs().isEmpty();
            searchButton.setText(empty ? R.string.search_no_match : R.string.search_search);
        }

        list.setAdapter(mergeAdapter);
    }

    private void expandArtists()
    {
        artistAdapter.clear();

        for (Artist artist : searchResult.getArtists())
        {
            artistAdapter.add(artist);
        }

        artistAdapter.notifyDataSetChanged();
        mergeAdapter.removeAdapter(moreArtistsAdapter);
        mergeAdapter.notifyDataSetChanged();
    }

    private void expandAlbums()
    {
        albumAdapter.clear();

        for (MusicDirectory.Entry album : searchResult.getAlbums())
        {
            albumAdapter.add(album);
        }

        albumAdapter.notifyDataSetChanged();
        mergeAdapter.removeAdapter(moreAlbumsAdapter);
        mergeAdapter.notifyDataSetChanged();
    }

    private void expandSongs()
    {
        songAdapter.clear();

        for (MusicDirectory.Entry song : searchResult.getSongs())
        {
            songAdapter.add(song);
        }

        songAdapter.notifyDataSetChanged();
        mergeAdapter.removeAdapter(moreSongsAdapter);
        mergeAdapter.notifyDataSetChanged();
    }

    private void onArtistSelected(Artist artist)
    {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, artist.getId());
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, artist.getId());
        Navigation.findNavController(getView()).navigate(R.id.searchToSelectAlbum, bundle);
    }

    private void onAlbumSelected(MusicDirectory.Entry album, boolean autoplay)
    {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, album.getId());
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, album.getTitle());
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, album.isDirectory());
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, autoplay);
        Navigation.findNavController(getView()).navigate(R.id.searchToSelectAlbum, bundle);
    }

    private void onSongSelected(MusicDirectory.Entry song, boolean save, boolean append, boolean autoplay, boolean playNext)
    {
        MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
        if (mediaPlayerController != null)
        {
            if (!append && !playNext)
            {
                mediaPlayerController.clear();
            }

            mediaPlayerController.download(Collections.singletonList(song), save, false, playNext, false, false);

            if (autoplay)
            {
                mediaPlayerController.play(mediaPlayerController.getPlaylistSize() - 1);
            }

            Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_added, 1, 1));
        }
    }

    private void onVideoSelected(MusicDirectory.Entry entry)
    {
        videoPlayer.getValue().playVideo(entry);
    }

    private void autoplay()
    {
        if (!searchResult.getSongs().isEmpty())
        {
            onSongSelected(searchResult.getSongs().get(0), false, false, true, false);
        }
        else if (!searchResult.getAlbums().isEmpty())
        {
            onAlbumSelected(searchResult.getAlbums().get(0), true);
        }
    }
}
