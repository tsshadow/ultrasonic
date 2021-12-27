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
import org.moire.ultrasonic.domain.Custom4;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Settings;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.Custom4Adapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available custom4 in the media library
 */
public class SelectCustom4Fragment extends Fragment {

    private SwipeRefreshLayout refreshcustom4ListView;
    private ListView custom4ListView;
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
        return inflater.inflate(R.layout.select_custom4, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshcustom4ListView = view.findViewById(R.id.select_custom4_refresh);
        custom4ListView = view.findViewById(R.id.select_custom4_list);

        refreshcustom4ListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        custom4ListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Custom4 custom4 = (Custom4) parent.getItemAtPosition(position);

                if (custom4 != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_CUSTOM4_NAME, custom4.getName());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, Settings.getMaxSongs());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_custom4_empty);
        registerForContextMenu(custom4ListView);

        FragmentTitle.Companion.setTitle(this, R.string.main_custom4_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Custom4>> task = new FragmentBackgroundTask<List<Custom4>>(getActivity(), true, refreshcustom4ListView, cancellationToken)
        {
            @Override
            protected List<Custom4> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Custom4> custom4 = new ArrayList<>();

                try
                {
                    custom4 = musicService.getCustom4(refresh);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load custom4");
                }

                return custom4;
            }

            @Override
            protected void done(List<Custom4> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    custom4ListView.setAdapter(new Custom4Adapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
