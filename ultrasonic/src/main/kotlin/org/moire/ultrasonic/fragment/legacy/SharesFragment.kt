/*
 * SharesFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Locale
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.domain.Share
import org.moire.ultrasonic.fragment.FragmentTitle
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.OfflineException
import org.moire.ultrasonic.util.BackgroundTask
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.FragmentBackgroundTask
import org.moire.ultrasonic.util.LoadingTask
import org.moire.ultrasonic.util.TimeSpanPicker
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.view.ShareAdapter

/**
 * Displays the shares in the media library
 *
 * TODO: This file has been converted from Java, but not modernized yet.
 */
class SharesFragment : ScopeFragment(), KoinScopeComponent {
    private var refreshSharesListView: SwipeRefreshLayout? = null
    private var sharesListView: ListView? = null
    private var emptyTextView: View? = null
    private var shareAdapter: ShareAdapter? = null
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private var cancellationToken: CancellationToken? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.select_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cancellationToken = CancellationToken()
        refreshSharesListView = view.findViewById(R.id.select_share_refresh)
        sharesListView = view.findViewById(R.id.select_share_list)
        refreshSharesListView!!.setOnRefreshListener { load(true) }
        emptyTextView = view.findViewById(R.id.select_share_empty)
        sharesListView!!.onItemClickListener =
            AdapterView.OnItemClickListener { parent, _, position, _ ->
                val share = parent.getItemAtPosition(position) as Share

                val action = NavigationGraphDirections.toTrackCollection(
                    shareId = share.id,
                    shareName = share.name
                )
                findNavController().navigate(action)
            }
        registerForContextMenu(sharesListView!!)
        FragmentTitle.setTitle(this, R.string.button_bar_shares)
        load(false)
    }

    override fun onDestroyView() {
        cancellationToken!!.cancel()
        super.onDestroyView()
    }

    private fun load(refresh: Boolean) {
        val task: BackgroundTask<List<Share>> = object : FragmentBackgroundTask<List<Share>>(
            activity, true, refreshSharesListView, cancellationToken
        ) {
            @Throws(Throwable::class)
            override fun doInBackground(): List<Share> {
                val musicService = MusicServiceFactory.getMusicService()
                return musicService.getShares(refresh)
            }

            override fun done(result: List<Share>) {
                shareAdapter = ShareAdapter(requireContext(), result)
                sharesListView?.adapter = shareAdapter
                emptyTextView?.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        task.execute()
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        view: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, view, menuInfo)
        val inflater = requireActivity().menuInflater
        inflater.inflate(R.menu.select_share_context, menu)
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        val info = menuItem.menuInfo as AdapterView.AdapterContextMenuInfo
        val share = sharesListView!!.getItemAtPosition(info.position) as Share
        when (menuItem.itemId) {
            R.id.share_menu_pin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.PIN,
                    fragment = this,
                    id = share.id,
                    name = share.name,
                    isShare = true,
                    isDirectory = false
                )
            }
            R.id.share_menu_unpin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.UNPIN,
                    fragment = this,
                    id = share.id,
                    name = share.name,
                    isShare = true,
                    isDirectory = false
                )
            }
            R.id.share_menu_download -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.DOWNLOAD,
                    fragment = this,
                    id = share.id,
                    name = share.name,
                    isShare = true,
                    isDirectory = false
                )
            }
            R.id.share_menu_play_now -> {
                mediaPlayerManager.playTracksAndToast(
                    this,
                    insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                    id = share.id,
                    name = share.name,
                    shuffle = false
                )
            }
            R.id.share_menu_play_shuffled -> {
                mediaPlayerManager.playTracksAndToast(
                    this,
                    insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                    id = share.id,
                    name = share.name,
                    shuffle = true,
                )
            }
            R.id.share_menu_delete -> {
                deleteShare(share)
            }
            R.id.share_info -> {
                displayShareInfo(share)
            }
            R.id.share_update_info -> {
                updateShareInfo(share)
            }
            else -> {
                return super.onContextItemSelected(menuItem)
            }
        }
        return true
    }

    private fun deleteShare(share: Share) {
        AlertDialog.Builder(context).setIcon(R.drawable.ic_baseline_warning)
            .setTitle(R.string.common_confirm).setMessage(
                resources.getString(R.string.delete_playlist, share.name)
            ).setPositiveButton(R.string.common_ok) { _, _ ->
                object : LoadingTask<Any?>(activity, refreshSharesListView, cancellationToken) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): Any? {
                        val musicService = MusicServiceFactory.getMusicService()
                        musicService.deleteShare(share.id)
                        return null
                    }

                    override fun done(result: Any?) {
                        shareAdapter!!.remove(share)
                        shareAdapter!!.notifyDataSetChanged()
                        toast(
                            resources.getString(R.string.menu_deleted_share, share.name)
                        )
                    }

                    override fun error(error: Throwable) {
                        val msg: String =
                            if (error is OfflineException || error is ApiNotSupportedException) {
                                getErrorMessage(
                                    error
                                )
                            } else {
                                String.format(
                                    Locale.ROOT,
                                    "%s %s",
                                    resources.getString(
                                        R.string.menu_deleted_share_error,
                                        share.name
                                    ),
                                    getErrorMessage(error)
                                )
                            }
                        toast(msg, false)
                    }
                }.execute()
            }.setNegativeButton(R.string.common_cancel, null).show()
    }

    private fun displayShareInfo(share: Share) {
        val textView = TextView(context)
        textView.setPadding(5, 5, 5, 5)
        val message: Spannable = SpannableString(
            """
                  Owner: ${share.username}
                  Comments: ${if (share.description == null) "" else share.description}
                  URL: ${share.url}
                  Entry Count: ${share.getEntries().size}
                  Visit Count: ${share.visitCount}
            """.trimIndent() +
                (
                    if (share.created == null) "" else """
     
     Creation Date: ${share.created!!.replace('T', ' ')}
                    """.trimIndent()
                    ) +
                (
                    if (share.lastVisited == null) "" else """
     
     Last Visited Date: ${share.lastVisited!!.replace('T', ' ')}
                    """.trimIndent()
                    ) +
                if (share.expires == null) "" else """
     
     Expiration Date: ${share.expires!!.replace('T', ' ')}
                """.trimIndent()
        )
        Linkify.addLinks(message, Linkify.WEB_URLS)
        textView.text = message
        textView.movementMethod = LinkMovementMethod.getInstance()
        AlertDialog.Builder(context).setTitle("Share Details").setCancelable(true)
            .setIcon(R.drawable.ic_baseline_info).setView(textView).show()
    }

    @SuppressLint("InflateParams")
    private fun updateShareInfo(share: Share) {
        val dialogView = layoutInflater.inflate(R.layout.share_details, null) ?: return
        val shareDescription = dialogView.findViewById<EditText>(R.id.share_description)
        val timeSpanPicker = dialogView.findViewById<TimeSpanPicker>(R.id.date_picker)
        shareDescription.setText(share.description)
        val hideDialogCheckBox = dialogView.findViewById<CheckBox>(R.id.hide_dialog)
        val saveAsDefaultsCheckBox = dialogView.findViewById<CheckBox>(R.id.save_as_defaults)
        val noExpirationCheckBox = dialogView.findViewById<CheckBox>(R.id.timeSpanDisableCheckBox)
        noExpirationCheckBox.setOnCheckedChangeListener { _, b ->
            timeSpanPicker.isEnabled = !b
        }
        noExpirationCheckBox.isChecked = true
        timeSpanPicker.setTimeSpanDisableText(resources.getText(R.string.no_expiration))
        hideDialogCheckBox.visibility = View.GONE
        saveAsDefaultsCheckBox.visibility = View.GONE
        val alertDialog = AlertDialog.Builder(context)
        alertDialog.setIcon(R.drawable.ic_baseline_warning)
        alertDialog.setTitle(R.string.playlist_update_info)
        alertDialog.setView(dialogView)
        alertDialog.setPositiveButton(R.string.common_ok) { _, _ ->
            object : LoadingTask<Any?>(activity, refreshSharesListView, cancellationToken) {
                @Throws(Throwable::class)
                override fun doInBackground(): Any? {
                    var millis = timeSpanPicker.getTimeSpan()
                    if (millis > 0) {
                        millis += System.currentTimeMillis()
                    }
                    val shareDescriptionText = shareDescription.text
                    val description = shareDescriptionText?.toString()
                    val musicService = MusicServiceFactory.getMusicService()
                    musicService.updateShare(share.id, description, millis)
                    return null
                }

                override fun done(result: Any?) {
                    load(true)
                    toast(
                        resources.getString(R.string.playlist_updated_info, share.name)
                    )
                }

                override fun error(error: Throwable) {
                    val msg: String =
                        if (error is OfflineException || error is ApiNotSupportedException) {
                            getErrorMessage(
                                error
                            )
                        } else {
                            String.format(
                                Locale.ROOT,
                                "%s %s",
                                resources.getString(
                                    R.string.playlist_updated_info_error,
                                    share.name
                                ),
                                getErrorMessage(error)
                            )
                        }
                    toast(msg, false)
                }
            }.execute()
        }
        alertDialog.setNegativeButton(R.string.common_cancel, null)
        alertDialog.show()
    }
}
