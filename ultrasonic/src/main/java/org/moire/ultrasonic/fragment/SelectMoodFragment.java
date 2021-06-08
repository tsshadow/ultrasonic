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
import org.moire.ultrasonic.domain.Mood;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.MoodAdapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available mood in the media library
 */
public class SelectMoodFragment extends Fragment {

    private SwipeRefreshLayout refreshMoodListView;
    private ListView moodListView;
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
        return inflater.inflate(R.layout.select_mood, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshMoodListView = view.findViewById(R.id.select_mood_refresh);
        moodListView = view.findViewById(R.id.select_mood_list);

        refreshMoodListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        moodListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Mood mood = (Mood) parent.getItemAtPosition(position);

                if (mood != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_EXTRA_NAME_MOOD_NAME, mood.getName());
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 100000);
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_mood_empty);
        registerForContextMenu(moodListView);

        FragmentTitle.Companion.setTitle(this, R.string.main_mood_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Mood>> task = new FragmentBackgroundTask<List<Mood>>(getActivity(), true, refreshMoodListView, cancellationToken)
        {
            @Override
            protected List<Mood> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Mood> mood = new ArrayList<>();

                try
                {
                    mood = musicService.getMoods(refresh);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load mood");
                }

                return mood;
            }

            @Override
            protected void done(List<Mood> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    moodListView.setAdapter(new MoodAdapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
