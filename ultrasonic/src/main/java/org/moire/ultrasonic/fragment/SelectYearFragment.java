package org.moire.ultrasonic.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.Year;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Settings;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.YearAdapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available year in the media library
 */
public class SelectYearFragment extends Fragment {

    private SwipeRefreshLayout refreshYearListView;
    private ListView yearListView;
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
        return inflater.inflate(R.layout.select_year, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshYearListView = view.findViewById(R.id.select_year_refresh);
        yearListView = view.findViewById(R.id.select_year_list);

        refreshYearListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        yearListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Year year = (Year) parent.getItemAtPosition(position);

                if (year != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_YEAR_NAME, year.getName());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, Settings.getMaxSongs());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_year_empty);
        registerForContextMenu(yearListView);

        FragmentTitle.Companion.setTitle(this, R.string.main_year_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Year>> task = new FragmentBackgroundTask<List<Year>>(getActivity(), true, refreshYearListView, cancellationToken)
        {
            @Override
            protected List<Year> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Year> year = new ArrayList<>();

                try
                {
                    year = musicService.getYears(refresh);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load year");
                }

                return year;
            }

            @Override
            protected void done(List<Year> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    yearListView.setAdapter(new YearAdapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
