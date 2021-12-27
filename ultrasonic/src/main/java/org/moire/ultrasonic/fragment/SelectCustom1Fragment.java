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
import org.moire.ultrasonic.domain.Custom1;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.Custom1Adapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Displays the available custom1 in the media library
 */
public class SelectCustom1Fragment extends Fragment {

    private SwipeRefreshLayout refreshcustom1ListView;
    private ListView custom1ListView;
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
        return inflater.inflate(R.layout.select_custom1, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        refreshcustom1ListView = view.findViewById(R.id.select_custom1_refresh);
        custom1ListView = view.findViewById(R.id.select_custom1_list);

        refreshcustom1ListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        custom1ListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Custom1 custom1 = (Custom1) parent.getItemAtPosition(position);

                if (custom1 != null)
                {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.INTENT_NAME_CUSTOM1_NAME, custom1.getName());
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, 100000);
                    bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0);
                    Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
                }
            }
        });

        emptyView = view.findViewById(R.id.select_custom1_empty);
        registerForContextMenu(custom1ListView);

        FragmentTitle.Companion.setTitle(this, R.string.main_custom1_title);
        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Custom1>> task = new FragmentBackgroundTask<List<Custom1>>(getActivity(), true, refreshcustom1ListView, cancellationToken)
        {
            @Override
            protected List<Custom1> doInBackground()
            {
                MusicService musicService = MusicServiceFactory.getMusicService();

                List<Custom1> custom1 = new ArrayList<>();

                try
                {
                    custom1 = musicService.getCustom1(refresh);
                }
                catch (Exception x)
                {
                    Timber.e(x, "Failed to load custom1");
                }

                return custom1;
            }

            @Override
            protected void done(List<Custom1> result)
            {
                emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);

                if (result != null)
                {
                    custom1ListView.setAdapter(new Custom1Adapter(getContext(), result));
                }
            }
        };
        task.execute();
    }
}
