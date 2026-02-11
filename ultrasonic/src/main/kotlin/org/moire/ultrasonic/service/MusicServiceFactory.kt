/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package org.moire.ultrasonic.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.qualifier.named
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.di.OFFLINE_MUSIC_SERVICE
import org.moire.ultrasonic.di.ONLINE_MUSIC_SERVICE
import org.moire.ultrasonic.di.musicServiceModule
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/*
 * TODO: When resetMusicService is called, a large number of classes are completely newly instantiated,
 * which take quite a bit of time.
 *
 * Instead it would probably be faster to listen to Rx
 */
object MusicServiceFactory : KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()
    private val connectivityManager: ConnectivityManager
        get() =
            UApp.applicationContext().getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var previousServerId: Int? = null

    private fun isNetworkAvailable(): Boolean {
        val cm = connectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.i("Network available, resetting music service")
                previousServerId?.let {
                    activeServerProvider.setActiveServerById(it)
                    previousServerId = null
                }
                resetMusicService()
                RxBus.createServiceCommandPublisher.onNext(Unit)
                networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
                networkCallback = null
            }
        }
        connectivityManager.registerNetworkCallback(request, callback)
        networkCallback = callback
    }

    @JvmStatic
    fun getMusicService(): MusicService {
        if (ActiveServerProvider.isOffline()) {
            previousServerId = null
            return get(named(OFFLINE_MUSIC_SERVICE))
        }

        if (!isNetworkAvailable()) {
            Timber.w("Active server unreachable, falling back to offline service")
            if (Settings.autoSwitchOffline) {
                if (previousServerId == null) {
                    previousServerId = ActiveServerProvider.getActiveServerId()
                    activeServerProvider.setActiveServerById(ActiveServerProvider.OFFLINE_DB_ID)
                }
                registerNetworkCallback()
                return get(named(OFFLINE_MUSIC_SERVICE))
            }
        }

        if (Settings.autoSwitchOffline &&
            previousServerId != null &&
            ActiveServerProvider.isOffline()
        ) {
            activeServerProvider.setActiveServerById(previousServerId!!)
            previousServerId = null
        }

        return get(named(ONLINE_MUSIC_SERVICE))
    }

    /**
     * Resets [MusicService] to initial state, so on next call to [.getMusicService]
     * it will return updated instance of it.
     */
    @JvmStatic
    fun resetMusicService() {
        Timber.i("Regenerating Koin Music Service Module")
        unloadKoinModules(musicServiceModule)
        loadKoinModules(musicServiceModule)
    }
}
