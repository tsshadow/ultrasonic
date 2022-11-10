/*
 * PlayerFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color.argb
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.SessionResult
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.toTrack
import org.moire.ultrasonic.view.AutoRepeatButton
import timber.log.Timber

/**
 * Contains the Music Player screen of Ultrasonic with playback controls and the playlist
 * TODO: Add timeline lister -> updateProgressBar().
 */
@Suppress("LargeClass", "TooManyFunctions", "MagicNumber")
class PlayerFragment :
    Fragment(),
    GestureDetector.OnGestureListener,
    KoinComponent,
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    // Settings
    private var swipeDistance = 0
    private var swipeVelocity = 0
    private var jukeboxAvailable = false
    private var useFiveStarRating = false
    private var isEqualizerAvailable = false

    // Detectors & Callbacks
    private lateinit var gestureScanner: GestureDetector
    private lateinit var cancellationToken: CancellationToken
    private lateinit var dragTouchHelper: ItemTouchHelper

    // Data & Services
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val mediaPlayerController: MediaPlayerController by inject()
    private val shareHandler: ShareHandler by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private var currentSong: Track? = null
    private lateinit var viewManager: LinearLayoutManager
    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()
    private lateinit var executorService: ScheduledExecutorService
    private var ioScope = CoroutineScope(Dispatchers.IO)

    // Views and UI Elements
    private lateinit var playlistNameView: EditText
    private lateinit var starMenuItem: MenuItem
    private lateinit var fiveStar1ImageView: ImageView
    private lateinit var fiveStar2ImageView: ImageView
    private lateinit var fiveStar3ImageView: ImageView
    private lateinit var fiveStar4ImageView: ImageView
    private lateinit var fiveStar5ImageView: ImageView
    private lateinit var playlistFlipper: ViewFlipper
    private lateinit var emptyTextView: TextView
    private lateinit var songTitleTextView: TextView
    private lateinit var artistTextView: TextView
    private lateinit var albumTextView: TextView
    private lateinit var genreTextView: TextView
    private lateinit var bitrateFormatTextView: TextView
    private lateinit var albumArtImageView: ImageView
    private lateinit var playlistView: RecyclerView
    private lateinit var positionTextView: TextView
    private lateinit var downloadTrackTextView: TextView
    private lateinit var downloadTotalDurationTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var pauseButton: View
    private lateinit var stopButton: View
    private lateinit var playButton: View
    private lateinit var shuffleButton: View
    private lateinit var repeatButton: MaterialButton
    private lateinit var progressBar: SeekBar
    private val hollowStar = R.drawable.ic_star_hollow
    private val fullStar = R.drawable.ic_star_full

    private val viewAdapter: BaseAdapter<Identifiable> by lazy {
        BaseAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.current_playing, container, false)
    }

    private fun findViews(view: View) {
        playlistFlipper = view.findViewById(R.id.current_playing_playlist_flipper)
        emptyTextView = view.findViewById(R.id.playlist_empty)
        songTitleTextView = view.findViewById(R.id.current_playing_song)
        artistTextView = view.findViewById(R.id.current_playing_artist)
        albumTextView = view.findViewById(R.id.current_playing_album)
        genreTextView = view.findViewById(R.id.current_playing_genre)
        bitrateFormatTextView = view.findViewById(R.id.current_playing_bitrate_format)
        albumArtImageView = view.findViewById(R.id.current_playing_album_art_image)
        positionTextView = view.findViewById(R.id.current_playing_position)
        downloadTrackTextView = view.findViewById(R.id.current_playing_track)
        downloadTotalDurationTextView = view.findViewById(R.id.current_total_duration)
        durationTextView = view.findViewById(R.id.current_playing_duration)
        progressBar = view.findViewById(R.id.current_playing_progress_bar)
        playlistView = view.findViewById(R.id.playlist_view)

        pauseButton = view.findViewById(R.id.button_pause)
        stopButton = view.findViewById(R.id.button_stop)
        playButton = view.findViewById(R.id.button_start)
        repeatButton = view.findViewById(R.id.button_repeat)
        fiveStar1ImageView = view.findViewById(R.id.song_five_star_1)
        fiveStar2ImageView = view.findViewById(R.id.song_five_star_2)
        fiveStar3ImageView = view.findViewById(R.id.song_five_star_3)
        fiveStar4ImageView = view.findViewById(R.id.song_five_star_4)
        fiveStar5ImageView = view.findViewById(R.id.song_five_star_5)
    }

    @Suppress("LongMethod", "DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cancellationToken = CancellationToken()
        setTitle(this, R.string.common_appname)

        val windowManager = requireActivity().windowManager
        val width: Int
        val height: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            width = size.x
            height = size.y
        }

        setHasOptionsMenu(true)
        useFiveStarRating = Settings.useFiveStarRating
        swipeDistance = (width + height) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100
        swipeVelocity = swipeDistance
        gestureScanner = GestureDetector(context, this)

        findViews(view)
        val previousButton: AutoRepeatButton = view.findViewById(R.id.button_previous)
        val nextButton: AutoRepeatButton = view.findViewById(R.id.button_next)
        shuffleButton = view.findViewById(R.id.button_shuffle)
        updateShuffleButtonState(mediaPlayerController.isShufflePlayEnabled)
        updateRepeatButtonState(mediaPlayerController.repeatMode)

        val ratingLinearLayout = view.findViewById<LinearLayout>(R.id.song_rating)
        if (!useFiveStarRating) ratingLinearLayout.isVisible = false

        fiveStar1ImageView.setOnClickListener { setSongRating(1) }
        fiveStar2ImageView.setOnClickListener { setSongRating(2) }
        fiveStar3ImageView.setOnClickListener { setSongRating(3) }
        fiveStar4ImageView.setOnClickListener { setSongRating(4) }
        fiveStar5ImageView.setOnClickListener { setSongRating(5) }

        albumArtImageView.setOnTouchListener { _, me ->
            gestureScanner.onTouchEvent(me)
        }

        albumArtImageView.setOnClickListener {
            toggleFullScreenAlbumArt()
        }

        previousButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.previous()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        previousButton.setOnRepeatListener {
            seek(false)
        }

        nextButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.next()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        nextButton.setOnRepeatListener {
            seek(true)
        }

        pauseButton.setOnClickListener {
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.pause()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        stopButton.setOnClickListener {
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.reset()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        playButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.play()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        shuffleButton.setOnClickListener {
            toggleShuffle()
        }

        repeatButton.setOnClickListener {
            var newRepeat = mediaPlayerController.repeatMode + 1
            if (newRepeat == 3) {
                newRepeat = 0
            }

            mediaPlayerController.repeatMode = newRepeat

            onPlaylistChanged()

            when (newRepeat) {
                0 -> Util.toast(
                    context, R.string.download_repeat_off
                )
                1 -> Util.toast(
                    context, R.string.download_repeat_single
                )
                2 -> Util.toast(
                    context, R.string.download_repeat_all
                )
                else -> {
                }
            }
        }

        progressBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                launch(CommunicationError.getHandler(context)) {
                    mediaPlayerController.seekTo(progressBar.progress)
                    onSliderProgressChanged()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        initPlaylistDisplay()

        EqualizerController.get().observe(
            requireActivity()
        ) { equalizerController ->
            isEqualizerAvailable = if (equalizerController != null) {
                Timber.d("EqualizerController Observer.onChanged received controller")
                true
            } else {
                Timber.d("EqualizerController Observer.onChanged has no controller")
                false
            }
        }

        // Observe playlist changes and update the UI
        rxBusSubscription += RxBus.playlistObservable.subscribe {
            onPlaylistChanged()
            onSliderProgressChanged()
        }

        rxBusSubscription += RxBus.playerStateObservable.subscribe {
            update()
        }

        // Query the Jukebox state in an IO Context
        ioScope.launch(CommunicationError.getHandler(context)) {
            try {
                jukeboxAvailable = mediaPlayerController.isJukeboxAvailable
            } catch (all: Exception) {
                Timber.e(all)
            }
        }

        view.setOnTouchListener { _, event -> gestureScanner.onTouchEvent(event) }
    }

    private fun updateShuffleButtonState(isEnabled: Boolean) {
        if (isEnabled) {
            shuffleButton.alpha = ALPHA_FULL
        } else {
            shuffleButton.alpha = ALPHA_DEACTIVATED
        }
    }

    private fun updateRepeatButtonState(repeatMode: Int) {
        when (repeatMode) {
            0 -> {
                repeatButton.setIconResource(R.drawable.media_repeat_off)
                repeatButton.alpha = ALPHA_DEACTIVATED
            }
            1 -> {
                repeatButton.setIconResource(R.drawable.media_repeat_one)
                repeatButton.alpha = ALPHA_FULL
            }
            2 -> {
                repeatButton.setIconResource(R.drawable.media_repeat_all)
                repeatButton.alpha = ALPHA_FULL
            }
            else -> {
            }
        }
    }

    private fun toggleShuffle() {
        val isEnabled = mediaPlayerController.toggleShuffle()

        if (isEnabled) {
            Util.toast(activity, R.string.download_menu_shuffle_on)
        } else {
            Util.toast(activity, R.string.download_menu_shuffle_off)
        }

        updateShuffleButtonState(isEnabled)
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayerController.currentMediaItem == null) {
            playlistFlipper.displayedChild = 1
        } else {
            // Download list and Album art must be updated when resumed
            onPlaylistChanged()
            onCurrentChanged()
        }

        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable { handler.post { update(cancellationToken) } }
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService.scheduleWithFixedDelay(runnable, 0L, 500L, TimeUnit.MILLISECONDS)

        if (mediaPlayerController.keepScreenOn) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        requireActivity().invalidateOptionsMenu()
    }

    // Scroll to current playing.
    private fun scrollToCurrent() {
        val index = mediaPlayerController.currentMediaItemIndex

        if (index != -1) {
            val smoothScroller = LinearSmoothScroller(context)
            smoothScroller.targetPosition = index
            viewManager.startSmoothScroll(smoothScroller)
        }
    }

    override fun onPause() {
        super.onPause()
        executorService.shutdown()
    }

    override fun onDestroyView() {
        rxBusSubscription.dispose()
        cancel("CoroutineScope cancelled because the view was destroyed")
        cancellationToken.cancel()
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.nowplaying, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val screenOption = menu.findItem(R.id.menu_item_screen_on_off)
        val jukeboxOption = menu.findItem(R.id.menu_item_jukebox)
        val equalizerMenuItem = menu.findItem(R.id.menu_item_equalizer)
        val shareMenuItem = menu.findItem(R.id.menu_item_share)
        val shareSongMenuItem = menu.findItem(R.id.menu_item_share_song)
        starMenuItem = menu.findItem(R.id.menu_item_star)
        val bookmarkMenuItem = menu.findItem(R.id.menu_item_bookmark_set)
        val bookmarkRemoveMenuItem = menu.findItem(R.id.menu_item_bookmark_delete)

        if (isOffline()) {
            if (shareMenuItem != null) {
                shareMenuItem.isVisible = false
            }
            starMenuItem.isVisible = false
            if (bookmarkMenuItem != null) {
                bookmarkMenuItem.isVisible = false
            }
            if (bookmarkRemoveMenuItem != null) {
                bookmarkRemoveMenuItem.isVisible = false
            }
        }
        if (equalizerMenuItem != null) {
            equalizerMenuItem.isEnabled = isEqualizerAvailable
            equalizerMenuItem.isVisible = isEqualizerAvailable
        }
        val mediaPlayerController = mediaPlayerController
        val track = mediaPlayerController.currentMediaItem?.toTrack()

        if (track != null) {
            currentSong = track
        }

        if (useFiveStarRating) starMenuItem.isVisible = false

        if (currentSong != null) {
            starMenuItem.setIcon(if (currentSong!!.starred) fullStar else hollowStar)
            shareSongMenuItem.isVisible = true
        } else {
            starMenuItem.setIcon(hollowStar)
            shareSongMenuItem.isVisible = false
        }

        if (mediaPlayerController.keepScreenOn) {
            screenOption?.setTitle(R.string.download_menu_screen_off)
        } else {
            screenOption?.setTitle(R.string.download_menu_screen_on)
        }

        if (jukeboxOption != null) {
            jukeboxOption.isEnabled = jukeboxAvailable
            jukeboxOption.isVisible = jukeboxAvailable
            if (mediaPlayerController.isJukeboxEnabled) {
                jukeboxOption.setTitle(R.string.download_menu_jukebox_off)
            } else {
                jukeboxOption.setTitle(R.string.download_menu_jukebox_on)
            }
        }
    }

    private fun onCreateContextMenu(view: View, track: Track): PopupMenu {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.nowplaying_context, popup.menu)

        if (track.parent == null) {
            val menuItem = popup.menu.findItem(R.id.menu_show_album)
            if (menuItem != null) {
                menuItem.isVisible = false
            }
        }

        if (isOffline() || !Settings.shouldUseId3Tags) {
            popup.menu.findItem(R.id.menu_show_artist)?.isVisible = false
        }

        popup.menu.findItem(R.id.menu_lyrics)?.isVisible = !isOffline()
        popup.show()
        return popup
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return menuItemSelected(item.itemId, currentSong) || super.onOptionsItemSelected(item)
    }

    private fun onContextMenuItemSelected(
        menuItem: MenuItem,
        item: MusicDirectory.Child
    ): Boolean {
        if (item !is Track) return false
        return menuItemSelected(menuItem.itemId, item)
    }

    @Suppress("ComplexMethod", "LongMethod", "ReturnCount")
    private fun menuItemSelected(menuItemId: Int, track: Track?): Boolean {
        when (menuItemId) {
            R.id.menu_show_artist -> {
                if (track == null) return false

                if (Settings.shouldUseId3Tags) {
                    val action = PlayerFragmentDirections.playerToAlbumsList(
                        type = AlbumListType.SORTED_BY_NAME,
                        byArtist = true,
                        id = track.artistId,
                        title = track.artist,
                        offset = 0,
                        size = 1000
                    )
                    findNavController().navigate(action)
                }
                return true
            }
            R.id.menu_show_album -> {
                if (track == null) return false

                val albumId = if (Settings.shouldUseId3Tags) track.albumId else track.parent

                val action = PlayerFragmentDirections.playerToSelectAlbum(
                    id = albumId,
                    name = track.album,
                    parentId = track.parent,
                    isAlbum = true
                )

                findNavController().navigate(action)
                return true
            }
            R.id.menu_lyrics -> {
                if (track?.artist == null || track.title == null) return false
                val action = PlayerFragmentDirections.playerToLyrics(track.artist!!, track.title!!)
                Navigation.findNavController(requireView()).navigate(action)
                return true
            }
            R.id.menu_remove -> {
                onPlaylistChanged()
                return true
            }
            R.id.menu_item_screen_on_off -> {
                val window = requireActivity().window
                if (mediaPlayerController.keepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mediaPlayerController.keepScreenOn = false
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mediaPlayerController.keepScreenOn = true
                }
                return true
            }
            R.id.menu_shuffle -> {
                toggleShuffle()
                return true
            }
            R.id.menu_item_equalizer -> {
                Navigation.findNavController(requireView()).navigate(R.id.playerToEqualizer)
                return true
            }
            R.id.menu_item_jukebox -> {
                val jukeboxEnabled = !mediaPlayerController.isJukeboxEnabled
                mediaPlayerController.isJukeboxEnabled = jukeboxEnabled
                Util.toast(
                    context,
                    if (jukeboxEnabled) R.string.download_jukebox_on
                    else R.string.download_jukebox_off,
                    false
                )
                return true
            }
            R.id.menu_item_toggle_list -> {
                toggleFullScreenAlbumArt()
                return true
            }
            R.id.menu_item_clear_playlist -> {
                mediaPlayerController.isShufflePlayEnabled = false
                mediaPlayerController.clear()
                onPlaylistChanged()
                return true
            }
            R.id.menu_item_save_playlist -> {
                if (mediaPlayerController.playlistSize > 0) {
                    showSavePlaylistDialog()
                }
                return true
            }
            R.id.menu_item_star -> {
                if (track == null) return true

                val isStarred = track.starred

                mediaPlayerController.toggleSongStarred()?.let {
                    Futures.addCallback(
                        it,
                        object : FutureCallback<SessionResult> {
                            override fun onSuccess(result: SessionResult?) {
                                if (isStarred) {
                                    starMenuItem.setIcon(hollowStar)
                                    track.starred = false
                                } else {
                                    starMenuItem.setIcon(fullStar)
                                    track.starred = true
                                }
                            }

                            override fun onFailure(t: Throwable) {
                                Toast.makeText(context, "SetRating failed", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        this.executorService
                    )
                }

                return true
            }
            R.id.menu_item_bookmark_set -> {
                if (track == null) return true

                val songId = track.id
                val playerPosition = mediaPlayerController.playerPosition
                track.bookmarkPosition = playerPosition
                val bookmarkTime = Util.formatTotalDuration(playerPosition.toLong(), true)
                Thread {
                    val musicService = getMusicService()
                    try {
                        musicService.createBookmark(songId, playerPosition)
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }.start()
                val msg = resources.getString(
                    R.string.download_bookmark_set_at_position,
                    bookmarkTime
                )
                Util.toast(context, msg)
                return true
            }
            R.id.menu_item_bookmark_delete -> {
                if (track == null) return true

                val bookmarkSongId = track.id
                track.bookmarkPosition = 0
                Thread {
                    val musicService = getMusicService()
                    try {
                        musicService.deleteBookmark(bookmarkSongId)
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }.start()
                Util.toast(context, R.string.download_bookmark_removed)
                return true
            }
            R.id.menu_item_share -> {
                val mediaPlayerController = mediaPlayerController
                val tracks: MutableList<Track?> = ArrayList()
                val playlist = mediaPlayerController.playlist
                for (item in playlist) {
                    val playlistEntry = item.toTrack()
                    tracks.add(playlistEntry)
                }
                shareHandler.createShare(
                    this,
                    tracks = tracks,
                    swipe = null,
                    cancellationToken = cancellationToken,
                )
                return true
            }
            R.id.menu_item_share_song -> {
                if (track == null) return true

                val tracks: MutableList<Track?> = ArrayList()
                tracks.add(track)

                shareHandler.createShare(
                    this,
                    tracks,
                    swipe = null,
                    cancellationToken = cancellationToken
                )
                return true
            }
            else -> return false
        }
    }

    private fun update(cancel: CancellationToken? = null) {
        if (cancel?.isCancellationRequested == true) return
        val mediaPlayerController = mediaPlayerController
        if (currentSong?.id != mediaPlayerController.currentMediaItem?.mediaId) {
            onCurrentChanged()
        }
        onSliderProgressChanged()
        requireActivity().invalidateOptionsMenu()
    }

    private fun savePlaylistInBackground(playlistName: String) {
        Util.toast(context, resources.getString(R.string.download_playlist_saving, playlistName))
        mediaPlayerController.suggestedPlaylistName = playlistName

        // The playlist can be acquired only from the main thread
        val entries = mediaPlayerController.playlist.map {
            it.toTrack()
        }

        ioScope.launch {
            val musicService = getMusicService()
            musicService.createPlaylist(null, playlistName, entries)
        }.invokeOnCompletion {
            if (it == null || it is CancellationException) {
                Util.toast(context, R.string.download_playlist_done)
            } else {
                Timber.e(it, "Exception has occurred in savePlaylistInBackground")
                val msg = String.format(
                    Locale.ROOT,
                    "%s %s",
                    resources.getString(R.string.download_playlist_error),
                    CommunicationError.getErrorMessage(it, context)
                )
                Util.toast(context, msg)
            }
        }
    }

    private fun toggleFullScreenAlbumArt() {
        if (playlistFlipper.displayedChild == 1) {
            playlistFlipper.inAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_down_in)
            playlistFlipper.outAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_down_out)
            playlistFlipper.displayedChild = 0
        } else {
            playlistFlipper.inAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_up_in)
            playlistFlipper.outAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_up_out)
            playlistFlipper.displayedChild = 1
        }
        scrollToCurrent()
    }

    private fun initPlaylistDisplay() {
        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        playlistView.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        // Create listener
        val clickHandler: ((Track, Int) -> Unit) = { _, pos ->
            mediaPlayerController.seekTo(pos, 0)
            mediaPlayerController.prepare()
            mediaPlayerController.play()
            onCurrentChanged()
            onSliderProgressChanged()
        }

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = clickHandler,
                onContextMenuClick = { menu, id -> onContextMenuItemSelected(menu, id) },
                checkable = false,
                draggable = true,
                lifecycleOwner = viewLifecycleOwner,
            ) { view, track -> onCreateContextMenu(view, track) }.apply {
                this.startDrag = { holder ->
                    dragTouchHelper.startDrag(holder)
                }
            }
        )

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {

            var dragging = false
            var startPosition = 0
            var endPosition = 0

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {

                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition

                // The item must be moved manually in the viewAdapter, because it must be
                // moved synchronously, before this function returns. AsyncListDiffer would execute
                // the move too late
                val items = viewAdapter.getCurrentList().toMutableList()
                if (from < to) {
                    for (i in from until to) {
                        Collections.swap(items, i, i + 1)
                    }
                } else {
                    for (i in from downTo to + 1) {
                        Collections.swap(items, i, i - 1)
                    }
                }
                viewAdapter.setList(items)
                viewAdapter.notifyItemMoved(from, to)
                endPosition = to

                // When the user moves an item, onMove may be called many times quickly,
                // especially while scrolling. We only update the playlist when the item
                // is released (see onSelectedChanged)

                // It was moved, so return true
                return true
            }

            // Swipe to delete from playlist
            @SuppressLint("NotifyDataSetChanged")
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val item = mediaPlayerController.getMediaItemAt(pos)

                // Remove the item from the list quickly
                val items = viewAdapter.getCurrentList().toMutableList()
                items.removeAt(pos)
                viewAdapter.setList(items)
                viewAdapter.notifyItemRemoved(pos)

                val songRemoved = String.format(
                    resources.getString(R.string.download_song_removed),
                    item?.mediaMetadata?.title
                )

                Util.toast(context, songRemoved)

                // Remove the item from the playlist
                mediaPlayerController.removeFromPlaylist(pos)
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = ALPHA_DEACTIVATED
                    dragging = true
                    startPosition = viewHolder!!.bindingAdapterPosition
                }

                // We only move the item in the playlist when the user finished dragging
                if (actionState == ACTION_STATE_IDLE && dragging) {
                    dragging = false
                    // Move the item in the playlist separately
                    mediaPlayerController.moveItemInPlaylist(startPosition, endPosition)
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val drawable = ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_menu_remove_all,
                        null
                    )
                    val iconSize = Util.dpToPx(ICON_SIZE, activity!!)
                    val swipeRatio = abs(dX) / viewHolder.itemView.width.toFloat()
                    val itemAlpha = ALPHA_FULL - swipeRatio
                    val backgroundAlpha = min(ALPHA_HALF + swipeRatio, ALPHA_FULL)
                    val backgroundColor = argb((backgroundAlpha * 255).toInt(), 255, 0, 0)

                    if (dX > 0) {
                        canvas.clipRect(
                            itemView.left.toFloat(), itemView.top.toFloat(),
                            dX, itemView.bottom.toFloat()
                        )
                        canvas.drawColor(backgroundColor)
                        val left = itemView.left + Util.dpToPx(16, activity!!)
                        val top = itemView.top + (itemView.bottom - itemView.top - iconSize) / 2
                        drawable?.setBounds(left, top, left + iconSize, top + iconSize)
                        drawable?.draw(canvas)
                    } else {
                        canvas.clipRect(
                            itemView.right.toFloat() + dX, itemView.top.toFloat(),
                            itemView.right.toFloat(), itemView.bottom.toFloat(),
                        )
                        canvas.drawColor(backgroundColor)
                        val left = itemView.right - Util.dpToPx(16, activity!!) - iconSize
                        val top = itemView.top + (itemView.bottom - itemView.top - iconSize) / 2
                        drawable?.setBounds(left, top, left + iconSize, top + iconSize)
                        drawable?.draw(canvas)
                    }

                    // Fade out the view as it is swiped out of the parent's bounds
                    viewHolder.itemView.alpha = itemAlpha
                    viewHolder.itemView.translationX = dX
                } else {
                    super.onChildDraw(
                        canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                    )
                }
            }
        }

        dragTouchHelper = ItemTouchHelper(callback)

        dragTouchHelper.attachToRecyclerView(playlistView)
    }

    private fun onPlaylistChanged() {
        val mediaPlayerController = mediaPlayerController
        val list = mediaPlayerController.playlist
        emptyTextView.setText(R.string.playlist_empty)

        viewAdapter.submitList(list.map(MediaItem::toTrack))

        emptyTextView.isVisible = list.isEmpty()

        updateRepeatButtonState(mediaPlayerController.repeatMode)
    }

    private fun onCurrentChanged() {
        currentSong = mediaPlayerController.currentMediaItem?.toTrack()

        scrollToCurrent()
        val totalDuration = mediaPlayerController.playListDuration
        val totalSongs = mediaPlayerController.playlistSize
        val currentSongIndex = mediaPlayerController.currentMediaItemIndex + 1
        val duration = Util.formatTotalDuration(totalDuration)
        val trackFormat =
            String.format(Locale.getDefault(), "%d / %d", currentSongIndex, totalSongs)
        if (currentSong != null) {
            songTitleTextView.text = currentSong!!.title
            artistTextView.text = currentSong!!.artist
            albumTextView.text = currentSong!!.album
            if (currentSong!!.year != null && Settings.showNowPlayingDetails)
                albumTextView.append(String.format(Locale.ROOT, " (%d)", currentSong!!.year))

            if (Settings.showNowPlayingDetails) {
                genreTextView.text = currentSong!!.genre
                genreTextView.isVisible =
                    (currentSong!!.genre != null && currentSong!!.genre!!.isNotBlank())

                var bitRate = ""
                if (currentSong!!.bitRate != null && currentSong!!.bitRate!! > 0)
                    bitRate = String.format(
                        Util.appContext().getString(R.string.song_details_kbps),
                        currentSong!!.bitRate
                    )
                bitrateFormatTextView.text = String.format(
                    Locale.ROOT, "%s %s",
                    bitRate, currentSong!!.suffix
                )
                bitrateFormatTextView.isVisible = true
            } else {
                genreTextView.isVisible = false
                bitrateFormatTextView.isVisible = false
            }

            downloadTrackTextView.text = trackFormat
            downloadTotalDurationTextView.text = duration
            imageLoaderProvider.getImageLoader()
                .loadImage(albumArtImageView, currentSong, true, 0)
            displaySongRating()
        } else {
            currentSong = null
            songTitleTextView.text = null
            artistTextView.text = null
            albumTextView.text = null
            genreTextView.text = null
            bitrateFormatTextView.text = null
            downloadTrackTextView.text = null
            downloadTotalDurationTextView.text = null
            imageLoaderProvider.getImageLoader()
                .loadImage(albumArtImageView, null, true, 0)
        }
    }

    @Suppress("LongMethod")
    @Synchronized
    private fun onSliderProgressChanged() {

        val isJukeboxEnabled: Boolean = mediaPlayerController.isJukeboxEnabled
        val millisPlayed: Int = max(0, mediaPlayerController.playerPosition)
        val duration: Int = mediaPlayerController.playerDuration
        val playbackState: Int = mediaPlayerController.playbackState
        val isPlaying = mediaPlayerController.isPlaying

        if (cancellationToken.isCancellationRequested) return
        if (currentSong != null) {
            positionTextView.text = Util.formatTotalDuration(millisPlayed.toLong(), true)
            durationTextView.text = Util.formatTotalDuration(duration.toLong(), true)
            progressBar.max =
                if (duration == 0) 100 else duration // Work-around for apparent bug.
            progressBar.progress = millisPlayed
            progressBar.isEnabled = mediaPlayerController.isPlaying || isJukeboxEnabled
        } else {
            positionTextView.setText(R.string.util_zero_time)
            durationTextView.setText(R.string.util_no_time)
            progressBar.progress = 0
            progressBar.max = 0
            progressBar.isEnabled = false
        }

        val progress = mediaPlayerController.bufferedPercentage

        when (playbackState) {
            Player.STATE_BUFFERING -> {

                val downloadStatus = resources.getString(
                    R.string.download_playerstate_loading
                )
                progressBar.secondaryProgress = progress
                setTitle(this@PlayerFragment, downloadStatus)
            }
            Player.STATE_READY -> {
                progressBar.secondaryProgress = progress
                if (mediaPlayerController.isShufflePlayEnabled) {
                    setTitle(
                        this@PlayerFragment,
                        R.string.download_playerstate_playing_shuffle
                    )
                } else {
                    setTitle(this@PlayerFragment, R.string.common_appname)
                }
            }
            Player.STATE_IDLE,
            Player.STATE_ENDED,
            -> {
            }
            else -> setTitle(this@PlayerFragment, R.string.common_appname)
        }

        when (playbackState) {
            Player.STATE_READY -> {
                pauseButton.isVisible = isPlaying
                stopButton.isVisible = false
                playButton.isVisible = !isPlaying
            }
            Player.STATE_BUFFERING -> {
                pauseButton.isVisible = false
                stopButton.isVisible = true
                playButton.isVisible = false
            }
            else -> {
                pauseButton.isVisible = false
                stopButton.isVisible = false
                playButton.isVisible = true
            }
        }

        // TODO: It would be a lot nicer if MediaPlayerController would send an event
        // when this is necessary instead of updating every time
        displaySongRating()
    }

    private fun seek(forward: Boolean) {
        launch(CommunicationError.getHandler(context)) {
            if (forward) {
                mediaPlayerController.seekForward()
            } else {
                mediaPlayerController.seekBack()
            }
        }
    }

    override fun onDown(me: MotionEvent): Boolean {
        return false
    }

    @Suppress("ReturnCount")
    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val e1X = e1.x
        val e2X = e2.x
        val e1Y = e1.y
        val e2Y = e2.y
        val absX = abs(velocityX)
        val absY = abs(velocityY)

        // Right to Left swipe
        if (e1X - e2X > swipeDistance && absX > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.next()
            onCurrentChanged()
            onSliderProgressChanged()
            return true
        }

        // Left to Right swipe
        if (e2X - e1X > swipeDistance && absX > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.previous()
            onCurrentChanged()
            onSliderProgressChanged()
            return true
        }

        // Top to Bottom swipe
        if (e2Y - e1Y > swipeDistance && absY > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.seekTo(mediaPlayerController.playerPosition + 30000)
            onSliderProgressChanged()
            return true
        }

        // Bottom to Top swipe
        if (e1Y - e2Y > swipeDistance && absY > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.seekTo(mediaPlayerController.playerPosition - 8000)
            onSliderProgressChanged()
            return true
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    private fun displaySongRating() {
        var rating = 0

        if (currentSong?.userRating != null) {
            rating = currentSong!!.userRating!!
        }

        fiveStar1ImageView.setImageResource(if (rating > 0) fullStar else hollowStar)
        fiveStar2ImageView.setImageResource(if (rating > 1) fullStar else hollowStar)
        fiveStar3ImageView.setImageResource(if (rating > 2) fullStar else hollowStar)
        fiveStar4ImageView.setImageResource(if (rating > 3) fullStar else hollowStar)
        fiveStar5ImageView.setImageResource(if (rating > 4) fullStar else hollowStar)
    }

    private fun setSongRating(rating: Int) {
        if (currentSong == null) return
        displaySongRating()
        mediaPlayerController.setSongRating(rating)
    }

    private fun showSavePlaylistDialog() {
        val layout = LayoutInflater.from(this.context).inflate(R.layout.save_playlist, null)

        playlistNameView = layout.findViewById(R.id.save_playlist_name)

        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.download_playlist_title)
        builder.setMessage(R.string.download_playlist_name)

        builder.setPositiveButton(R.string.common_save) { _, _ ->
            savePlaylistInBackground(
                playlistNameView.text.toString()
            )
        }

        builder.setNegativeButton(R.string.common_cancel) { dialog, _ -> dialog.cancel() }
        builder.setView(layout)
        builder.setCancelable(true)
        val dialog = builder.create()
        val playlistName = mediaPlayerController.suggestedPlaylistName
        if (playlistName != null) {
            playlistNameView.setText(playlistName)
        } else {
            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            playlistNameView.setText(dateFormat.format(Date()))
        }
        dialog.show()
    }

    companion object {
        private const val PERCENTAGE_OF_SCREEN_FOR_SWIPE = 5
        private const val ALPHA_FULL = 1f
        private const val ALPHA_HALF = 0.5f
        private const val ALPHA_DEACTIVATED = 0.4f
        private const val ICON_SIZE = 32
    }
}
