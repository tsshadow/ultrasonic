package org.moire.ultrasonic.adapters

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Checkable
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.disposables.Disposable
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.DownloadStatus
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Used to display songs and videos in a `ListView`.
 */
class TrackViewHolder(val view: View) : RecyclerView.ViewHolder(view), Checkable, KoinComponent {

    var check: CheckedTextView = view.findViewById(R.id.song_check)
    private var rating: LinearLayout = view.findViewById(R.id.song_five_star)
    private var fiveStar1: ImageView = view.findViewById(R.id.song_five_star_1)
    private var fiveStar2: ImageView = view.findViewById(R.id.song_five_star_2)
    private var fiveStar3: ImageView = view.findViewById(R.id.song_five_star_3)
    private var fiveStar4: ImageView = view.findViewById(R.id.song_five_star_4)
    private var fiveStar5: ImageView = view.findViewById(R.id.song_five_star_5)
    var star: ImageView = view.findViewById(R.id.song_star)
    var drag: ImageView = view.findViewById(R.id.song_drag)
    var track: TextView = view.findViewById(R.id.song_track)
    var title: TextView = view.findViewById(R.id.song_title)
    var artist: TextView = view.findViewById(R.id.song_artist)
    var duration: TextView = view.findViewById(R.id.song_duration)
    var progress: TextView = view.findViewById(R.id.song_status)

    var entry: Track? = null
        private set
    var downloadFile: DownloadFile? = null
        private set

    private var isMaximized = false
    private var cachedStatus = DownloadStatus.UNKNOWN
    private var statusImage: Drawable? = null
    private var isPlayingCached = false

    private var rxSubscription: Disposable? = null

    var observableChecked = MutableLiveData(false)

    lateinit var imageHelper: Utils.ImageHelper

    fun setSong(
        file: DownloadFile,
        checkable: Boolean,
        draggable: Boolean,
        isSelected: Boolean = false
    ) {
        val useFiveStarRating = Settings.useFiveStarRating
        val song = file.track
        downloadFile = file
        entry = song

        val entryDescription = Util.readableEntryDescription(song)

        artist.text = entryDescription.artist
        title.text = entryDescription.title
        duration.text = entryDescription.duration

        if (Settings.shouldShowTrackNumber && song.track != null && song.track!! > 0) {
            track.text = entryDescription.trackNumber
        } else {
            track.isVisible = false
        }

        check.isVisible = (checkable && !song.isVideo)
        initChecked(isSelected)
        drag.isVisible = draggable

        if (ActiveServerProvider.isOffline()) {
            star.isVisible = false
            rating.isVisible = false
        } else {
            setupStarButtons(song, useFiveStarRating)
        }

        updateProgress(downloadFile!!.progress.value!!)
        updateStatus(downloadFile!!.status.value!!)

        if (useFiveStarRating) {
            setFiveStars(entry?.userRating ?: 0)
        } else {
            setSingleStar(entry!!.starred)
        }

        if (song.isVideo) {
            artist.isVisible = false
            progress.isVisible = false
        }

        rxSubscription = RxBus.playerStateObservable.subscribe {
            setPlayIcon(it.index == bindingAdapterPosition && it.track == downloadFile)
        }
    }

    fun dispose() {
        rxSubscription?.dispose()
    }

    private fun setPlayIcon(isPlaying: Boolean) {
        if (isPlaying && !isPlayingCached) {
            isPlayingCached = true
            title.setCompoundDrawablesWithIntrinsicBounds(
                imageHelper.playingImage, null, null, null
            )
        } else if (!isPlaying && isPlayingCached) {
            isPlayingCached = false
            title.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, 0, 0
            )
        }
    }

    private fun setupStarButtons(song: Track, useFiveStarRating: Boolean) {
        if (useFiveStarRating) {
            // Hide single star
            star.visibility = View.INVISIBLE
            val rating = if (song.userRating == null) 0 else song.userRating!!
            setFiveStars(rating)
        } else {
            // Hide five stars
            rating.isVisible = false

            setSingleStar(song.starred)
            star.setOnClickListener {
                val isStarred = song.starred
                val id = song.id

                if (!isStarred) {
                    star.setImageResource(R.drawable.ic_star_full)
                    song.starred = true
                } else {
                    star.setImageResource(R.drawable.ic_star_hollow)
                    song.starred = false
                }

                // Should this be done here ?
                Thread {
                    val musicService = MusicServiceFactory.getMusicService()
                    try {
                        if (!isStarred) {
                            musicService.star(id, null, null)
                        } else {
                            musicService.unstar(id, null, null)
                        }
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }.start()
            }
        }
    }

    @Suppress("MagicNumber")
    private fun setFiveStars(rating: Int) {
        fiveStar1.setImageResource(
            if (rating > 0) R.drawable.ic_star_full else R.drawable.ic_star_hollow
        )
        fiveStar2.setImageResource(
            if (rating > 1) R.drawable.ic_star_full else R.drawable.ic_star_hollow
        )
        fiveStar3.setImageResource(
            if (rating > 2) R.drawable.ic_star_full else R.drawable.ic_star_hollow
        )
        fiveStar4.setImageResource(
            if (rating > 3) R.drawable.ic_star_full else R.drawable.ic_star_hollow
        )
        fiveStar5.setImageResource(
            if (rating > 4) R.drawable.ic_star_full else R.drawable.ic_star_hollow
        )
    }

    private fun setSingleStar(starred: Boolean) {
        if (starred) {
            star.setImageResource(R.drawable.ic_star_full)
        } else {
            star.setImageResource(R.drawable.ic_star_hollow)
        }
    }

    fun updateStatus(status: DownloadStatus) {
        if (status == cachedStatus) return
        cachedStatus = status

        when (status) {
            DownloadStatus.DONE -> {
                statusImage = imageHelper.downloadedImage
                progress.text = null
            }
            DownloadStatus.PINNED -> {
                statusImage = imageHelper.pinImage
                progress.text = null
            }
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED -> {
                statusImage = imageHelper.errorImage
                progress.text = null
            }
            DownloadStatus.DOWNLOADING -> {
                statusImage = imageHelper.downloadingImage
            }
            else -> {
                statusImage = null
            }
        }

        updateImages()
    }

    fun updateProgress(p: Int) {
        if (cachedStatus == DownloadStatus.DOWNLOADING) {
            progress.text = Util.formatPercentage(p)
        } else {
            progress.text = null
        }
    }

    private fun updateImages() {
        progress.setCompoundDrawablesWithIntrinsicBounds(
            null, null, statusImage, null
        )

        if (statusImage === imageHelper.downloadingImage) {
            val frameAnimation = statusImage as AnimationDrawable?
            frameAnimation?.setVisible(true, true)
            frameAnimation?.start()
        }
    }

    /*
     * Set the checked value and re-init the MutableLiveData.
     * If we would post a new value, there might be a short glitch where the track is shown with its
     * old selection status before the posted value has been processed.
     */
    private fun initChecked(newStatus: Boolean) {
        observableChecked = MutableLiveData(newStatus)
        check.isChecked = newStatus
    }

    /*
     * To be correct, this method doesn't directly set the checked status.
     * It only notifies the observable. If the selection tracker accepts the selection
     *  (might be false for Singular SelectionTrackers) then it will cause the actual modification.
     */
    override fun setChecked(newStatus: Boolean) {
        observableChecked.postValue(newStatus)
    }

    override fun isChecked(): Boolean {
        return check.isChecked
    }

    override fun toggle() {
        isChecked = isChecked
    }

    fun maximizeOrMinimize() {
        isMaximized = !isMaximized

        title.isSingleLine = !isMaximized
        artist.isSingleLine = !isMaximized
    }
}
