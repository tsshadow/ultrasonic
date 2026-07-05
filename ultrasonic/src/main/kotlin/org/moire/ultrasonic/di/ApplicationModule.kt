package org.moire.ultrasonic.di

import org.koin.dsl.module
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner

/**
 * This Koin module contains the registration of general classes needed for Ultrasonic
 */
val applicationModule = module {
    single { ActiveServerProvider(get()) }
    single { ImageLoaderProvider() }
    single { CacheCleaner() }
}
