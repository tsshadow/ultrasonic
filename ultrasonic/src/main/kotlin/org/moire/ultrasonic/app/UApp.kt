package org.moire.ultrasonic.app

import android.content.Context
import android.os.StrictMode
import androidx.multidex.MultiDexApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.di.appPermanentStorage
import org.moire.ultrasonic.di.applicationModule
import org.moire.ultrasonic.di.baseNetworkModule
import org.moire.ultrasonic.di.mediaPlayerModule
import org.moire.ultrasonic.di.musicServiceModule
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.log.TimberKoinLogger
import org.moire.ultrasonic.util.Settings
import timber.log.Timber
import timber.log.Timber.DebugTree

/**
 * The Main class of the Application
 */

class UApp : MultiDexApplication() {

    private var ioScope = CoroutineScope(Dispatchers.IO)

    init {
        instance = this
        if (BuildConfig.DEBUG)
            StrictMode.enableDefaults()
    }

    var initiated = false

    override fun onCreate() {
        initiated = true
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }

        Timber.d("onCreate called")

        // In general we should not access the settings from the main thread to avoid blocking...
        ioScope.launch {
            if (Settings.debugLogToFile) {
                FileLoggerTree.plantToTimberForest()
            }
        }

        startKoin()
    }

    internal fun startKoin() {
        initiated = true
        startKoin {
            // Sometimes Koin breaks when Kotlin version is upgraded,
            // you can normally fix it by changing to logger(TimberKoinLogger(Level.ERROR))
            // See https://github.com/InsertKoinIO/koin/issues/1188
            logger(TimberKoinLogger())

            // declare Android context
            androidContext(this@UApp)

            // declare modules to use
            modules(
                applicationModule,
                appPermanentStorage,
                baseNetworkModule,
                musicServiceModule,
                mediaPlayerModule
            )
        }
    }

    internal fun shutdownKoin() {
        stopKoin()
        initiated = false
    }

    companion object {
        var instance: UApp? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }
}
