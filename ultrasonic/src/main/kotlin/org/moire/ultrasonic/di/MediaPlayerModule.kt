package org.moire.ultrasonic.di

import org.koin.dsl.module
import org.moire.ultrasonic.service.ExternalStorageMonitor
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.PlaybackStateSerializer

/**
 * This Koin module contains the registration of classes related to the media player
 */
val mediaPlayerModule = module {
    single { MediaPlayerLifecycleSupport() }
    single { PlaybackStateSerializer() }
    single { ExternalStorageMonitor() }

    // TODO Ideally this can be cleaned up when all circular references are removed.
    single { MediaPlayerManager(get(), get(), get()) }
}
