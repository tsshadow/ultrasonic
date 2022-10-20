package org.moire.ultrasonic.adapters

import android.view.View
import android.widget.Checkable
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

const val INDICATOR_THICKNESS_INDEFINITE = 5
const val INDICATOR_THICKNESS_DEFINITE = 10

/**
 * Used to display songs and videos in a `ListView`.
 */
class TrackViewHolder(val view: View) :
    RecyclerView.ViewHolder(view),
    Checkable,
    KoinComponent,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    var entry: Track? = null
        private set
    var check: CheckedTextView = view.findViewById(R.id.song_check)
    var drag: ImageView = view.findViewById(R.id.song_drag)
    var observableChecked = MutableLiveData(false)

    private var rating: LinearLayout = view.findViewById(R.id.song_rating)
    private var fiveStar1: ImageView = view.findViewById(R.id.song_five_star_1)
    private var fiveStar2: ImageView = view.findViewById(R.id.song_five_star_2)
    private var fiveStar3: ImageView = view.findViewById(R.id.song_five_star_3)
    private var fiveStar4: ImageView = view.findViewById(R.id.song_five_star_4)
    private var fiveStar5: ImageView = view.findViewById(R.id.song_five_star_5)
    private var star: ImageView = view.findViewById(R.id.song_star)
    private var track: TextView = view.findViewById(R.id.song_track)
    private var title: TextView = view.findViewById(R.id.song_title)
    private var artist: TextView = view.findViewById(R.id.song_artist)
    private var duration: TextView = view.findViewById(R.id.song_duration)
    private var statusImage: ImageView = view.findViewById(R.id.song_status_image)
    private var progressIndicator: CircularProgressIndicator =
        view.findViewById<CircularProgressIndicator?>(R.id.song_status_progress).apply {
            this.max = 100
        }

    private var isMaximized = false
    private var cachedStatus = DownloadState.UNKNOWN
    private var isPlayingCached = false

    private var rxBusSubscription: CompositeDisposable? = null

    init {
        Timber.v("New ViewHolder created")
    }

    @Suppress("ComplexMethod")
    fun setSong(
        song: Track,
        checkable: Boolean,
        draggable: Boolean,
        isSelected: Boolean = false
    ) {
        Timber.v("Setting song")
        val useFiveStarRating = Settings.useFiveStarRating
        entry = song

        val entryDescription = Util.readableEntryDescription(song)

        artist.text = entryDescription.artist
        title.text = entryDescription.title
        duration.text = entryDescription.duration

        if (Settings.shouldShowTrackNumber && song.track != null && song.track!! > 0) {
            track.text = entryDescription.trackNumber
        } else {
            if (!track.isGone) track.isGone = true
        }

        val checkValue = (checkable && !song.isVideo)
        if (check.isVisible != checkValue) check.isVisible = checkValue
        if (checkValue) initChecked(isSelected)
        if (drag.isVisible != draggable) drag.isVisible = draggable

        if (ActiveServerProvider.isOffline()) {
            star.isGone = true
        } else {
            setupStarButtons(song, useFiveStarRating)
        }

        // Instead of blocking the UI thread while looking up the current state,
        // launch the request in an IO thread and propagate the result through RX
        launch {
            val state = DownloadService.getDownloadState(song)
            RxBus.trackDownloadStatePublisher.onNext(
                RxBus.TrackDownloadState(song.id, state, null)
            )
        }

        if (useFiveStarRating) {
            setFiveStars(entry?.userRating ?: 0)
        } else {
            setSingleStar(entry!!.starred)
        }

        if (song.isVideo) {
            artist.isGone = true
            progressIndicator.isGone = true
        }

        // Create new Disposable for the new Subscriptions
        rxBusSubscription = CompositeDisposable()
        rxBusSubscription!! += RxBus.playerStateObservable.subscribe {
            setPlayIcon(it.index == bindingAdapterPosition && it.track?.id == song.id)
        }

        rxBusSubscription!! += RxBus.trackDownloadStateObservable.subscribe {
            if (it.id != song.id) return@subscribe
            updateStatus(it.state, it.progress)
        }

        Timber.v("Setting song done")
    }

    // This is called when the Holder is recycled and receives a new Song
    fun dispose() {
        rxBusSubscription?.dispose()
    }

    private val playingIcon by lazy {
        ContextCompat.getDrawable(view.context, R.drawable.ic_stat_play)!!
    }

    private fun setPlayIcon(isPlaying: Boolean) {
        if (isPlaying && !isPlayingCached) {
            isPlayingCached = true
            title.setCompoundDrawablesWithIntrinsicBounds(
                playingIcon, null, null, null
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
            star.isGone = true
            rating.isVisible = true
            val rating = if (song.userRating == null) 0 else song.userRating!!
            setFiveStars(rating)
        } else {
            star.isVisible = true
            rating.isGone = true
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

    private fun updateStatus(status: DownloadState, progress: Int?) {
        progressIndicator.progress = progress ?: 0

        if (status == cachedStatus) return
        cachedStatus = status

        when (status) {
            DownloadState.DONE -> {
                showStatusImage(R.drawable.ic_downloaded)
            }
            DownloadState.PINNED -> {
                showStatusImage(R.drawable.ic_menu_pin)
            }
            DownloadState.FAILED -> {
                showStatusImage(R.drawable.ic_baseline_error)
            }
            DownloadState.DOWNLOADING -> {
                showProgress()
            }
            DownloadState.RETRYING,
            DownloadState.QUEUED -> {
                showIndefiniteProgress()
            }
            else -> {
                // This handles CANCELLED too.
                // Usually it means no error, just that the track wasn't downloaded
                showStatusImage(null)
            }
        }
    }

    private fun showStatusImage(image: Int?) {
        progressIndicator.isGone = true
        statusImage.isVisible = true
        if (image != null) {
            statusImage.setImageResource(image)
        } else {
            statusImage.setImageDrawable(null)
        }
    }

    private fun showIndefiniteProgress() {
        statusImage.isGone = true
        progressIndicator.isVisible = true
        progressIndicator.isIndeterminate = true
        progressIndicator.indicatorDirection =
            CircularProgressIndicator.INDICATOR_DIRECTION_COUNTERCLOCKWISE
        progressIndicator.trackThickness = INDICATOR_THICKNESS_INDEFINITE
    }

    private fun showProgress() {
        statusImage.isGone = true
        progressIndicator.isVisible = true
        progressIndicator.isIndeterminate = false
        progressIndicator.indicatorDirection =
            CircularProgressIndicator.INDICATOR_DIRECTION_CLOCKWISE
        progressIndicator.trackThickness = INDICATOR_THICKNESS_DEFINITE
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
