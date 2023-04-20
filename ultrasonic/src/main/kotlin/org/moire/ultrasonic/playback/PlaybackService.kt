/*
 * PlaybackService.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.playback

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.ArtworkBitmapLoader
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.stopForegroundRemoveNotification
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

@SuppressLint("UnsafeOptInUsageError")
class PlaybackService :
    MediaLibraryService(),
    KoinComponent,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private var equalizer: EqualizerController? = null

    private lateinit var librarySessionCallback: MediaLibrarySession.Callback

    private var rxBusSubscription = CompositeDisposable()

    private var isStarted = false

    override fun onCreate() {
        Timber.i("onCreate called")
        super.onCreate()
        initializeSessionAndPlayer()
        setListener(MediaSessionServiceListener())
    }

    private fun getWakeModeFlag(): Int {
        return if (ActiveServerProvider.isOffline()) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK
    }

    override fun onDestroy() {
        Timber.i("onDestroy called")
        releasePlayerAndSession()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.i("Stopping the playback because we were swiped away")
        releasePlayerAndSession()
        super.onTaskRemoved(rootIntent)
    }

    private fun releasePlayerAndSession() {
        // Broadcast that the service is being shutdown
        RxBus.stopServiceCommandPublisher.onNext(Unit)

        // TODO Save the player state before shutdown
        player.removeListener(listener)
        player.release()
        mediaLibrarySession.release()
        rxBusSubscription.dispose()
        isStarted = false
        stopForegroundRemoveNotification()
        stopSelf()
    }

    private val resolver: ResolvingDataSource.Resolver = ResolvingDataSource.Resolver {
        val components = it.uri.toString().split('|')
        val id = components[0]
        val bitrate = components[1].toInt()
        val uri = getMusicService().getStreamUrl(id, bitrate, null)!!
        // AirSonic doesn't seem to stream correctly with the default
        // icy-metadata headers set by media3, so remove them.
        it.buildUpon().setUri(uri).setHttpRequestHeaders(emptyMap()).build()
    }

    private fun initializeSessionAndPlayer() {
        if (isStarted) return

        setMediaNotificationProvider(CustomNotificationProvider(UApp.applicationContext()))

        // Create a new plain OkHttpClient
        val builder = OkHttpClient.Builder()
        val client = builder.build()

        // Create the wrapped data sources:
        // CachedDataSource is the first. If it cannot find a file,
        // it will forward to ResolvingDataSource, which will create a URL through the resolver
        // and pass it onto the OkHttpDataSource.
        val okHttpDataSource = OkHttpDataSource.Factory(client)
        val resolvingDataSource = ResolvingDataSource.Factory(okHttpDataSource, resolver)
        val cacheDataSourceFactory: DataSource.Factory =
            CachedDataSource.Factory(resolvingDataSource)

        // Create a renderer with HW rendering support
        val renderer = DefaultRenderersFactory(this)

        if (Settings.useHwOffload)
            renderer.setEnableAudioOffload(true)

        // Create the player
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(getAudioAttributes(), true)
            .setWakeMode(getWakeModeFlag())
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setRenderersFactory(renderer)
            .setSeekBackIncrementMs(Settings.seekInterval.toLong())
            .setSeekForwardIncrementMs(Settings.seekInterval.toLong())
            .build()

        equalizer = EqualizerController.create(player.audioSessionId)

        // Enable audio offload
        if (Settings.useHwOffload)
            player.experimentalSetOffloadSchedulingEnabled(true)

        // Create browser interface
        librarySessionCallback = AutoMediaBrowserCallback(player, this)

        // This will need to use the AutoCalls
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setSessionActivity(getPendingIntentForContent())
            .setBitmapLoader(ArtworkBitmapLoader())
            .build()

        // Set a listener to update the API client when the active server has changed
        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            // Set the player wake mode
            player.setWakeMode(getWakeModeFlag())
        }

        // Set a listener to reset the ShuffleOrder
        rxBusSubscription += RxBus.shufflePlayObservable.subscribe { shuffle ->
            val len = player.currentTimeline.windowCount

            Timber.i("Resetting shuffle order, isShuffled: %s", shuffle)

            // If disabling Shuffle return early
            if (!shuffle) {
                return@subscribe player.setShuffleOrder(UnshuffledShuffleOrder(len))
            }

            // Get the position of the current track in the unshuffled order
            val cur = player.currentMediaItemIndex
            val seed = System.currentTimeMillis()
            val random = Random(seed)

            val list = createShuffleListFromCurrentIndex(cur, len, random)
            Timber.i("New Shuffle order: %s", list.joinToString { it.toString() })
            player.setShuffleOrder(DefaultShuffleOrder(list, seed))
        }

        // Listen to the shutdown command
        rxBusSubscription += RxBus.shutdownCommandObservable.subscribe {
            Timber.i("Received destroy command via Rx")
            onDestroy()
        }

        player.addListener(listener)
        isStarted = true
    }

    fun createShuffleListFromCurrentIndex(
        currentIndex: Int,
        length: Int,
        random: Random
    ): IntArray {
        val list = IntArray(length) { it }

        // Shuffle the remaining items using a swapping algorithm
        for (i in currentIndex + 1 until length) {
            val swapIndex = (currentIndex + 1) + random.nextInt(i - currentIndex)
            val swapItem = list[i]
            list[i] = list[swapIndex]
            list[swapIndex] = swapItem
        }

        return list
    }

    private val listener: Player.Listener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            cacheNextSongs()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateWidgetTrack(mediaItem?.toTrack())
            cacheNextSongs()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateWidgetPlayerState(isPlaying)
            cacheNextSongs()
        }
    }

    private fun cacheNextSongs() {
        Timber.d("PlaybackService caching the next songs")
        val nextSongs = Util.getPlayListFromTimeline(
            player.currentTimeline,
            player.shuffleModeEnabled,
            player.currentMediaItemIndex,
            Settings.preloadCount
        ).map { it.toTrack() }

        launch {
            DownloadService.download(nextSongs, save = false, isHighPriority = true)
        }
    }

    private fun getPendingIntentForContent(): PendingIntent {
        val intent = Intent(this, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        var flags = FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // needed starting Android 12 (S = 31)
            flags = flags or FLAG_IMMUTABLE
        }
        intent.putExtra(Constants.INTENT_SHOW_PLAYER, true)
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    private fun updateWidgetTrack(song: Track?) {
        val context = UApp.applicationContext()
        UltrasonicAppWidgetProvider.notifyTrackChange(context, song)
    }

    private fun updateWidgetPlayerState(isPlaying: Boolean) {
        val context = UApp.applicationContext()
        UltrasonicAppWidgetProvider.notifyPlayerStateChange(context, isPlaying)
    }

    private inner class MediaSessionServiceListener : Listener {

        /**
         * This method is only required to be implemented on Android 12 or above when an attempt is made
         * by a media controller to resume playback when the {@link MediaSessionService} is in the
         * background.
         */
        override fun onForegroundServiceStartNotAllowedException() {
            val notificationManagerCompat = NotificationManagerCompat.from(this@PlaybackService)
            Util.ensureNotificationChannel(
                id = NOTIFICATION_CHANNEL_ID,
                name = NOTIFICATION_CHANNEL_NAME,
                notificationManager = notificationManagerCompat
            )
            val pendingIntent =
                TaskStackBuilder.create(this@PlaybackService).run {
                    addNextIntent(Intent(this@PlaybackService, NavigationActivity::class.java))

                    val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        FLAG_IMMUTABLE
                    } else {
                        0
                    }
                    getPendingIntent(0, immutableFlag or FLAG_UPDATE_CURRENT)
                }
            val builder =
                NotificationCompat.Builder(this@PlaybackService, NOTIFICATION_CHANNEL_ID)
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.media3_notification_small_icon)
                    .setContentTitle(getString(R.string.foreground_exception_title))
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            getString(R.string.foreground_exception_text)
                        )
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)

            Util.postNotificationIfPermitted(
                notificationManagerCompat,
                NOTIFICATION_ID,
                builder.build()
            )
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic.error"
        private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic error messages"
        private const val NOTIFICATION_ID = 3009
    }
}
