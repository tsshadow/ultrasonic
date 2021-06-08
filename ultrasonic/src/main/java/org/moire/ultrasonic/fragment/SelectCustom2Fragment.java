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
import org.moire.ultrasonic.domain.Custom2;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.Custom2Adapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available custom2 in the media library
 */
public class SelectCustom2Fragment extends Fragment {

    private SwipeRefreshLayout refreshcustom2ListView;
    private ListView custom2ListView;
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
        return inflater.inflate(R.layout.select_custom2, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshcustom2ListView = view.findViewById(R.id.select_custom2_refresh);
        custom2ListView = view.findViewById(R.id.select_custom2_list);

        refreshcustom2ListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        custom2ListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Custom2 custom2 = (Custom2) parent.getItemAtPosition(position);

                if (custom2 != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_EXTRA_NAME_CUSTOM2_NAME, custom2.getName());
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 200000);
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_custom2_empty);
        registerForContextMenu(custom2ListView);

        FragmentTitle.Companion.setTitle(this, R.string.main_custom2_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Custom2>> task = new FragmentBackgroundTask<List<Custom2>>(getActivity(), true, refreshcustom2ListView, cancellationToken)
        {
            @Override
            protected List<Custom2> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Custom2> custom2 = new ArrayList<>();

                try
                {
                    custom2 = musicService.getCustom2(refresh);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load custom2");
                }

                return custom2;
            }

            @Override
            protected void done(List<Custom2> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    custom2ListView.setAdapter(new Custom2Adapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
