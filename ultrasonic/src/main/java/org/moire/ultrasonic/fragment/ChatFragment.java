package org.moire.ultrasonic.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.ChatMessage;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Settings;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.ChatAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

import com.google.android.material.button.MaterialButton;

/**
 * Provides online chat functionality
 */
public class ChatFragment extends Fragment {

    private ListView chatListView;
    private EditText messageEditText;
    private MaterialButton sendButton;
    private Timer timer;
    private volatile static Long lastChatMessageTime = (long) 0;
    private static final ArrayList<ChatMessage> messageList = new ArrayList<>();
    private CancellationToken cancellationToken;
    private SwipeRefreshLayout swipeRefresh;

    private final Lazy<ActiveServerProvider> activeServerProvider = inject(ActiveServerProvider.class);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeRefresh = view.findViewById(R.id.chat_refresh);
        swipeRefresh.setEnabled(false);

        cancellationToken = new CancellationToken();
        messageEditText = view.findViewById(R.id.chat_edittext);
        sendButton = view.findViewById(R.id.chat_send);

        sendButton.setOnClickListener(view1 -> sendMessage());

        chatListView = view.findViewById(R.id.chat_entries_list);
        chatListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        chatListView.setStackFromBottom(true);

        String serverName = activeServerProvider.getValue().getActiveServer().getName();
        String userName = activeServerProvider.getValue().getActiveServer().getUserName();
        String title = String.format("%s [%s@%s]", getResources().getString(R.string.button_bar_chat), userName, serverName);

        FragmentTitle.Companion.setTitle(this, title);
        setHasOptionsMenu(true);

        messageEditText.setImeActionLabel("Send", KeyEvent.KEYCODE_ENTER);

        messageEditText.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2)
            {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2)
            {
            }

            @Override
            public void afterTextChanged(Editable editable)
            {
                sendButton.setEnabled(!Util.isNullOrWhiteSpace(editable.toString()));
            }
        });

        messageEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN))
            {
                sendMessage();
                return true;
            }

            return false;
        });

        load();
        timerMethod();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.chat, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /*
     * Listen for option item selections so that we receive a notification
     * when the user requests a refresh by selecting the refresh action bar item.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Check if user triggered a refresh:
        if (item.getItemId() == R.id.menu_refresh) {
            // Start the refresh background task.
            load();
            return true;
        }
        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!messageList.isEmpty())
        {
            ListAdapter chatAdapter = new ChatAdapter(getContext(), messageList);
            chatListView.setAdapter(chatAdapter);
        }

        if (timer == null)
        {
            timerMethod();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void timerMethod()
    {
        int refreshInterval = Settings.getChatRefreshInterval();

        if (refreshInterval > 0)
        {
            timer = new Timer();

            timer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    getActivity().runOnUiThread(() -> load());
                }
            }, refreshInterval, refreshInterval);
        }
    }

    private void sendMessage()
    {
        if (messageEditText != null)
        {
            final String message;
            Editable text = messageEditText.getText();

            if (text == null)
            {
                return;
            }

            message = text.toString();

            if (!Util.isNullOrWhiteSpace(message))
            {
                messageEditText.setText("");

                BackgroundTask<Void> task = new FragmentBackgroundTask<Void>(getActivity(), false, swipeRefresh, cancellationToken)
                {
                    @Override
                    protected Void doInBackground() throws Throwable
                    {
                        MusicService musicService = MusicServiceFactory.getMusicService();
                        musicService.addChatMessage(message);
                        return null;
                    }

                    @Override
                    protected void done(Void result)
                    {
                        load();
                    }
                };

                task.execute();
            }
        }
    }

    private synchronized void load()
    {
        BackgroundTask<List<ChatMessage>> task = new FragmentBackgroundTask<List<ChatMessage>>(getActivity(), false, swipeRefresh, cancellationToken)
        {
            @Override
            protected List<ChatMessage> doInBackground() throws Throwable
            {
                MusicService musicService = MusicServiceFactory.getMusicService();
                return musicService.getChatMessages(lastChatMessageTime);
            }

            @Override
            protected void done(List<ChatMessage> result)
            {
                if (result != null && !result.isEmpty())
                {
                    // Reset lastChatMessageTime if we have a newer message
                    for (ChatMessage message : result)
                    {
                        if (message.getTime() > lastChatMessageTime)
                        {
                            lastChatMessageTime = message.getTime();
                        }
                    }

                    // Reverse results to show them on the bottom
                    Collections.reverse(result);
                    messageList.addAll(result);

                    ListAdapter chatAdapter = new ChatAdapter(getContext(), messageList);
                    chatListView.setAdapter(chatAdapter);
                }
            }

            @Override
            protected void error(Throwable error) {
                // Stop the timer in case of an error, otherwise it may repeat the error message forever
                if (timer != null)
                {
                    timer.cancel();
                    timer = null;
                }
                super.error(error);
            }
        };

        task.execute();
    }
}
