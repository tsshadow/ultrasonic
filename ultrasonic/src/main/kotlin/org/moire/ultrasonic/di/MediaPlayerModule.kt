package org.moire.ultrasonic.di

import org.koin.dsl.module
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.service.ExternalStorageMonitor
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.PlaybackStateSerializer
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler

/**
 * This Koin module contains the registration of classes related to the media player
 */
val mediaPlayerModule = module {

    // These are dependency-free
    single { PlaybackStateSerializer() }
    single { ExternalStorageMonitor() }
    single { NetworkAndStorageChecker() }
    single { ShareHandler() }

    scope<NavigationActivity> {
        scoped { MediaPlayerManager(get(), get()) }
        scoped { MediaPlayerLifecycleSupport(get(), get(), get(), get()) }
    }
}
