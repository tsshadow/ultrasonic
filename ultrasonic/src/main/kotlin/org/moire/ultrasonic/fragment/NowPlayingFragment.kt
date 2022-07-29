/*
 * NowPlayingFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import io.reactivex.rxjava3.disposables.Disposable
import java.lang.Exception
import kotlin.math.abs
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.getNotificationImageSize
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

/**
 * Contains the mini-now playing information box displayed at the bottom of the screen
 */
@Suppress("unused")
class NowPlayingFragment : Fragment() {

    private var downX = 0f
    private var downY = 0f

    private var playButton: ImageView? = null
    private var nowPlayingAlbumArtImage: ImageView? = null
    private var nowPlayingTrack: TextView? = null
    private var nowPlayingArtist: TextView? = null

    private var rxBusSubscription: Disposable? = null
    private val mediaPlayerController: MediaPlayerController by inject()
    private val imageLoader: ImageLoaderProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.now_playing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        playButton = view.findViewById(R.id.now_playing_control_play)
        nowPlayingAlbumArtImage = view.findViewById(R.id.now_playing_image)
        nowPlayingTrack = view.findViewById(R.id.now_playing_trackname)
        nowPlayingArtist = view.findViewById(R.id.now_playing_artist)
        rxBusSubscription = RxBus.playerStateObservable.subscribe { update() }
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBusSubscription!!.dispose()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun update() {
        try {
            if (mediaPlayerController.isPlaying) {
                playButton!!.setImageResource(R.drawable.media_pause_normal)
            } else {
                playButton!!.setImageResource(R.drawable.media_start_normal)
            }

            val file = mediaPlayerController.currentMediaItem?.toTrack()

            if (file != null) {
                val title = file.title
                val artist = file.artist

                imageLoader.getImageLoader().loadImage(
                    nowPlayingAlbumArtImage,
                    file,
                    false,
                    getNotificationImageSize(requireContext())
                )

                nowPlayingTrack!!.text = title
                nowPlayingArtist!!.text = artist

                nowPlayingAlbumArtImage!!.setOnClickListener {
                    val bundle = Bundle()

                    if (Settings.shouldUseId3Tags) {
                        bundle.putBoolean(Constants.INTENT_IS_ALBUM, true)
                        bundle.putString(Constants.INTENT_ID, file.albumId)
                    } else {
                        bundle.putBoolean(Constants.INTENT_IS_ALBUM, false)
                        bundle.putString(Constants.INTENT_ID, file.parent)
                    }

                    bundle.putString(Constants.INTENT_NAME, file.album)
                    bundle.putString(Constants.INTENT_NAME, file.album)

                    Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.trackCollectionFragment, bundle)
                }
            }

            requireView().setOnTouchListener { _: View?, event: MotionEvent ->
                handleOnTouch(event)
            }

            // This empty onClickListener is necessary for the onTouchListener to work
            requireView().setOnClickListener { }
            playButton!!.setOnClickListener { mediaPlayerController.togglePlayPause() }
        } catch (all: Exception) {
            Timber.w(all, "Failed to get notification cover art")
        }
    }

    private fun handleOnTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val upX = event.x
                val upY = event.y
                val deltaX = downX - upX
                val deltaY = downY - upY

                if (abs(deltaX) > MIN_DISTANCE) {
                    // left or right
                    if (deltaX < 0) {
                        mediaPlayerController.previous()
                    }
                    if (deltaX > 0) {
                        mediaPlayerController.next()
                    }
                } else if (abs(deltaY) > MIN_DISTANCE) {
                    if (deltaY < 0) {
                        RxBus.dismissNowPlayingCommandPublisher.onNext(Unit)
                    }
                } else {
                    Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.playerFragment)
                }
            }
        }
        return false
    }

    companion object {
        private const val MIN_DISTANCE = 30
    }
}
