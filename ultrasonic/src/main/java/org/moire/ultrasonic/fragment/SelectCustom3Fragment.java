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
import org.moire.ultrasonic.domain.Custom3;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Settings;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.Custom3Adapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available custom3 in the media library
 */
public class SelectCustom3Fragment extends Fragment {

    private SwipeRefreshLayout refreshcustom3ListView;
    private ListView custom3ListView;
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
        return inflater.inflate(R.layout.select_custom3, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshcustom3ListView = view.findViewById(R.id.select_custom3_refresh);
        custom3ListView = view.findViewById(R.id.select_custom3_list);

        refreshcustom3ListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        custom3ListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Custom3 custom3 = (Custom3) parent.getItemAtPosition(position);

                if (custom3 != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_NAME_CUSTOM3_NAME, custom3.getName());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, Settings.getMaxSongs());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_custom3_empty);
        registerForContextMenu(custom3ListView);

        FragmentTitle.Companion.setTitle(this, R.string.main_custom3_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Custom3>> task = new FragmentBackgroundTask<List<Custom3>>(getActivity(), true, refreshcustom3ListView, cancellationToken)
        {
            @Override
            protected List<Custom3> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Custom3> custom3 = new ArrayList<>();

                try
                {
                    custom3 = musicService.getCustom3(refresh);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load custom3");
                }

                return custom3;
            }

            @Override
            protected void done(List<Custom3> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    custom3ListView.setAdapter(new Custom3Adapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
