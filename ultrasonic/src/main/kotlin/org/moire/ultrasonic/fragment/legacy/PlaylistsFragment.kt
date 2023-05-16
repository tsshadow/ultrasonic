/*
 * PlaylistsFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Locale
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.OfflineException
import org.moire.ultrasonic.subsonic.DownloadAction
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.util.BackgroundTask
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.FragmentBackgroundTask
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.LoadingTask
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.toast

/**
 * Displays the playlists stored on the server
 *
 * TODO: This file has been converted from Java, but not modernized yet.
 */
class PlaylistsFragment : Fragment(), KoinComponent {
    private var refreshPlaylistsListView: SwipeRefreshLayout? = null
    private var playlistsListView: ListView? = null
    private var emptyTextView: View? = null
    private var playlistAdapter: ArrayAdapter<Playlist>? = null

    private val downloadHandler by inject<DownloadHandler>()

    private var cancellationToken: CancellationToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.select_playlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cancellationToken = CancellationToken()
        refreshPlaylistsListView = view.findViewById(R.id.select_playlist_refresh)
        playlistsListView = view.findViewById(R.id.select_playlist_list)
        refreshPlaylistsListView!!.setOnRefreshListener { load(true) }
        emptyTextView = view.findViewById(R.id.select_playlist_empty)
        playlistsListView!!.setOnItemClickListener { parent, _, position, _ ->
            val (id1, name) = parent.getItemAtPosition(position) as Playlist

            val action = NavigationGraphDirections.toTrackCollection(
                id = id1,
                playlistId = id1,
                name = name,
                playlistName = name,
            )
            findNavController().navigate(action)
        }
        registerForContextMenu(playlistsListView!!)
        setTitle(this, R.string.playlist_label)
        load(false)
    }

    override fun onDestroyView() {
        cancellationToken!!.cancel()
        super.onDestroyView()
    }

    private fun load(refresh: Boolean) {
        val task: BackgroundTask<List<Playlist>> =
            object : FragmentBackgroundTask<List<Playlist>>(
                activity, true, refreshPlaylistsListView, cancellationToken
            ) {
                @Throws(Throwable::class)
                override fun doInBackground(): List<Playlist> {
                    val musicService = getMusicService()
                    val playlists = musicService.getPlaylists(refresh)
                    if (!isOffline()) CacheCleaner().cleanPlaylists(playlists)
                    return playlists
                }

                override fun done(result: List<Playlist>) {
                    playlistAdapter =
                        ArrayAdapter(requireContext(), R.layout.list_item_generic, result)
                    playlistsListView!!.adapter = playlistAdapter
                    emptyTextView!!.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        task.execute()
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)
        val inflater = requireActivity().menuInflater
        if (isOffline()) inflater.inflate(
            R.menu.select_playlist_context_offline,
            menu
        ) else inflater.inflate(R.menu.select_playlist_context, menu)
        val downloadMenuItem = menu.findItem(R.id.playlist_menu_download)
        if (downloadMenuItem != null) {
            downloadMenuItem.isVisible = !isOffline()
        }
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        val info = menuItem.menuInfo as AdapterContextMenuInfo
        val playlist = playlistsListView!!.getItemAtPosition(info.position) as Playlist
        when (menuItem.itemId) {
            R.id.playlist_menu_pin -> {
                downloadHandler.justDownload(
                    DownloadAction.PIN,
                    fragment = this,
                    id = playlist.id,
                    name = playlist.name,
                    isShare = false,
                    isDirectory = false
                )
            }
            R.id.playlist_menu_unpin -> {
                downloadHandler.justDownload(
                    DownloadAction.UNPIN,
                    fragment = this,
                    id = playlist.id,
                    name = playlist.name,
                    isShare = false,
                    isDirectory = false
                )
            }
            R.id.playlist_menu_download -> {
                downloadHandler.justDownload(
                    DownloadAction.DOWNLOAD,
                    fragment = this,
                    id = playlist.id,
                    name = playlist.name,
                    isShare = false,
                    isDirectory = false
                )
            }
            R.id.playlist_menu_play_now -> {
                val action = NavigationGraphDirections.toTrackCollection(
                    playlistId = playlist.id,
                    playlistName = playlist.name,
                    autoPlay = true
                )
                findNavController().navigate(action)
            }
            R.id.playlist_menu_play_shuffled -> {
                val action = NavigationGraphDirections.toTrackCollection(
                    playlistId = playlist.id,
                    playlistName = playlist.name,
                    autoPlay = true,
                    shuffle = true
                )

                findNavController().navigate(action)
            }
            R.id.playlist_menu_delete -> {
                deletePlaylist(playlist)
            }
            R.id.playlist_info -> {
                displayPlaylistInfo(playlist)
            }
            R.id.playlist_update_info -> {
                updatePlaylistInfo(playlist)
            }
            else -> {
                return super.onContextItemSelected(menuItem)
            }
        }
        return true
    }

    private fun deletePlaylist(playlist: Playlist) {
        ConfirmationDialog.Builder(requireContext()).setIcon(R.drawable.ic_baseline_warning)
            .setTitle(R.string.common_confirm).setMessage(
                resources.getString(R.string.delete_playlist, playlist.name)
            ).setPositiveButton(R.string.common_ok) { _, _ ->
                object : LoadingTask<Any?>(activity, refreshPlaylistsListView, cancellationToken) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): Any? {
                        val musicService = getMusicService()
                        musicService.deletePlaylist(playlist.id)
                        return null
                    }

                    override fun done(result: Any?) {
                        playlistAdapter!!.remove(playlist)
                        playlistAdapter!!.notifyDataSetChanged()
                        toast(
                            context,
                            resources.getString(R.string.menu_deleted_playlist, playlist.name)
                        )
                    }

                    override fun error(error: Throwable) {
                        val msg: String =
                            if (error is OfflineException || error is ApiNotSupportedException)
                                getErrorMessage(
                                    error
                                ) else String.format(
                                Locale.ROOT,
                                "%s %s",
                                resources.getString(
                                    R.string.menu_deleted_playlist_error,
                                    playlist.name
                                ),
                                getErrorMessage(error)
                            )
                        toast(context, msg, false)
                    }
                }.execute()
            }.setNegativeButton(R.string.common_cancel, null).show()
    }

    private fun displayPlaylistInfo(playlist: Playlist) {
        val textView = TextView(context)
        textView.setPadding(5, 5, 5, 5)
        val message: Spannable = SpannableString(
            """
                  Owner: ${playlist.owner}
                  Comments: ${playlist.comment}
                  Song Count: ${playlist.songCount}
            """.trimIndent() +
                if (playlist.public == null) "" else """
     
     Public: ${playlist.public}
                """.trimIndent() + """
          
          Creation Date: ${playlist.created.replace('T', ' ')}
                """.trimIndent()
        )
        Linkify.addLinks(message, Linkify.WEB_URLS)
        textView.text = message
        textView.movementMethod = LinkMovementMethod.getInstance()
        InfoDialog.Builder(requireContext()).setTitle(playlist.name).setCancelable(true)
            .setView(textView).show()
    }

    @SuppressLint("InflateParams")
    private fun updatePlaylistInfo(playlist: Playlist) {
        val dialogView = layoutInflater.inflate(R.layout.update_playlist, null) ?: return
        val nameBox = dialogView.findViewById<EditText>(R.id.get_playlist_name)
        val commentBox = dialogView.findViewById<EditText>(R.id.get_playlist_comment)
        val publicBox = dialogView.findViewById<CheckBox>(R.id.get_playlist_public)
        nameBox.setText(playlist.name)
        commentBox.setText(playlist.comment)
        val pub = playlist.public
        if (pub == null) {
            publicBox.isEnabled = false
        } else {
            publicBox.isChecked = pub
        }
        val alertDialog = ConfirmationDialog.Builder(requireContext())
        alertDialog.setIcon(R.drawable.ic_baseline_warning)
        alertDialog.setTitle(R.string.playlist_update_info)
        alertDialog.setView(dialogView)
        alertDialog.setPositiveButton(R.string.common_ok) { _, _ ->
            object : LoadingTask<Any?>(activity, refreshPlaylistsListView, cancellationToken) {
                @Throws(Throwable::class)
                override fun doInBackground(): Any? {
                    val nameBoxText = nameBox.text
                    val commentBoxText = commentBox.text
                    val name = nameBoxText?.toString()
                    val comment = commentBoxText?.toString()
                    val musicService = getMusicService()
                    musicService.updatePlaylist(playlist.id, name, comment, publicBox.isChecked)
                    return null
                }

                override fun done(result: Any?) {
                    load(true)
                    toast(
                        context,
                        resources.getString(R.string.playlist_updated_info, playlist.name)
                    )
                }

                override fun error(error: Throwable) {
                    val msg: String =
                        if (error is OfflineException || error is ApiNotSupportedException)
                            getErrorMessage(
                                error
                            ) else String.format(
                            Locale.ROOT,
                            "%s %s",
                            resources.getString(
                                R.string.playlist_updated_info_error,
                                playlist.name
                            ),
                            getErrorMessage(error)
                        )
                    toast(context, msg, false)
                }
            }.execute()
        }
        alertDialog.setNegativeButton(R.string.common_cancel, null)
        alertDialog.show()
    }
}
