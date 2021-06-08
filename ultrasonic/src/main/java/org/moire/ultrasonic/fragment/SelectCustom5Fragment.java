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
import org.moire.ultrasonic.domain.Custom5;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.Custom5Adapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available custom5 in the media library
 */
public class SelectCustom5Fragment extends Fragment {

    private SwipeRefreshLayout refreshcustom5ListView;
    private ListView custom5ListView;
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
        return inflater.inflate(R.layout.select_custom5, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshcustom5ListView = view.findViewById(R.id.select_custom5_refresh);
        custom5ListView = view.findViewById(R.id.select_custom5_list);

        refreshcustom5ListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        custom5ListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Custom5 custom5 = (Custom5) parent.getItemAtPosition(position);

                if (custom5 != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_EXTRA_NAME_CUSTOM5_NAME, custom5.getName());
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 500000);
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_custom5_empty);
        registerForContextMenu(custom5ListView);

        FragmentTitle.Companion.setTitle(this, R.string.main_custom5_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Custom5>> task = new FragmentBackgroundTask<List<Custom5>>(getActivity(), true, refreshcustom5ListView, cancellationToken)
        {
            @Override
            protected List<Custom5> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Custom5> custom5 = new ArrayList<>();

                try
                {
                    custom5 = musicService.getCustom5(refresh);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load custom5");
                }

                return custom5;
            }

            @Override
            protected void done(List<Custom5> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    custom5ListView.setAdapter(new Custom5Adapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
