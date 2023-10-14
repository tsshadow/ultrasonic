/*
 * BookmarksFragment.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.MediaPlayerManager

/**
 * Lists the Bookmarks available on the server
 *
 * Bookmarks allows to save the play position of tracks, especially useful for longer tracks like
 * audio books etc.
 *
 * Therefore this fragment allows only for singular selection and playback.
 */
class BookmarksFragment : TrackCollectionFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setTitle(this, R.string.button_bar_bookmarks)

        viewAdapter.selectionType = BaseAdapter.SelectionType.SINGLE
    }

    override fun getLiveData(
        refresh: Boolean,
        append: Boolean
    ): LiveData<List<MusicDirectory.Child>> {
        listModel.viewModelScope.launch(handler) {
            refreshListView?.isRefreshing = true
            listModel.getBookmarks()
            refreshListView?.isRefreshing = false
        }
        return listModel.currentList
    }

    /**
     * Set a custom listener to perform the playing, in order to be able to restore
     * the playback position
     */
    override fun setupButtons(view: View) {
        super.setupButtons(view)

        playNowButton!!.setOnClickListener {
            playNow(getSelectedTracks())
        }
    }

    /**
     * Play the selected tracks at the bookmarked position
     */
    private fun playNow(songs: List<Track>) {
        if (songs.isNotEmpty()) {

            mediaPlayerManager.addToPlaylist(
                songs = songs,
                autoPlay = false,
                shuffle = false,
                insertionMode = MediaPlayerManager.InsertionMode.CLEAR
            )

            mediaPlayerManager.seekTo(0, songs[0].bookmarkPosition)
            mediaPlayerManager.prepare()
            mediaPlayerManager.play()
        }
    }
}
