package org.moire.ultrasonic.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.Genre;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Settings;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.GenreAdapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available genres in the media library
 */
public class SelectGenreFragment extends Fragment {

    private SwipeRefreshLayout refreshGenreListView;
    private ListView genreListView;
    private Spinner yearEditBox;
    private Spinner ratingMin;
    private Spinner ratingMax;
    private View emptyView;
    private CancellationToken cancellationToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.select_genre, container, false);
    }

    public static class onYearChangedListener implements AdapterView.OnItemSelectedListener {

        private final SelectGenreFragment fragment;

        public onYearChangedListener(SelectGenreFragment fragment) {
            this.fragment = fragment;
        }

        public void onItemSelected(AdapterView<?> parent,
                                   View view, int pos, long id) {
            fragment.load(true, parent.getItemAtPosition(pos).toString());
        }

        public void onNothingSelected(AdapterView parent) {
            // Do nothing.
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshGenreListView = view.findViewById(R.id.select_genre_refresh);
        genreListView = view.findViewById(R.id.select_genre_list);

        refreshGenreListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        genreListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Genre genre = (Genre) parent.getItemAtPosition(position);

                if (genre != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_GENRE_NAME, genre.getName());
                    bundle.putString(Constants.INTENT_YEAR_NAME, yearEditBox.getSelectedItem().toString());
                    bundle.putString(Constants.INTENT_RATING_MIN, ratingMin.getSelectedItem().toString());
                    bundle.putString(Constants.INTENT_RATING_MAX, ratingMax.getSelectedItem().toString());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, Settings.getMaxSongs());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_genre_empty);
        registerForContextMenu(genreListView);

        yearEditBox = (Spinner)view.findViewById(R.id.selectYear);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this.getContext(),
                R.array.year_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        yearEditBox.setAdapter(adapter);

        yearEditBox.setOnItemSelectedListener(new SelectGenreFragment.onYearChangedListener(this));

        ratingMin = (Spinner)view.findViewById(R.id.ratingMin);
        ratingMax = (Spinner)view.findViewById(R.id.ratingMax);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> ratingAdapter = ArrayAdapter.createFromResource(this.getContext(),
                R.array.rating_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        ratingMin.setAdapter(ratingAdapter);
        ratingMax.setAdapter(ratingAdapter);
        ratingMax.setSelection(5);

        FragmentTitle.Companion.setTitle(this, R.string.main_genres_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load (final boolean refresh)
    {
        load(refresh, "");
    }
    public void load(final boolean refresh, String year)
    {
        BackgroundTask<List<Genre>> task = new FragmentBackgroundTask<List<Genre>>(getActivity(), true, refreshGenreListView, cancellationToken)
        {
            @Override
            protected List<Genre> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Genre> genres = new ArrayList<>();

                try
                {
                    genres = musicService.getGenres(refresh, year);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load genres");
                }

                return genres;
            }

            @Override
            protected void done(List<Genre> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    genreListView.setAdapter(new GenreAdapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
