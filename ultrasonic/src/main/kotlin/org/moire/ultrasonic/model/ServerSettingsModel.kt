package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.OFFLINE_DB_ID
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.data.ServerSettingDao
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.Settings
import timber.log.Timber
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * ViewModel to be used in Activities which will handle Server Settings
 */
class ServerSettingsModel(
    private val repository: ServerSettingDao,
    private val activeServerProvider: ActiveServerProvider,
    application: Application
) : AndroidViewModel(application) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Retrieves the list of the configured servers.
     * In static mode, this returns the hardcoded list with credentials from Settings.
     */
    fun getServerList(): LiveData<List<ServerSetting>> {
        val liveData = MutableLiveData<List<ServerSetting>>()
        liveData.postValue(getStaticServersWithCredentials())
        return liveData
    }

    fun getStaticServersWithCredentials(): List<ServerSetting> {
        return STATIC_SERVERS.map { server ->
            server.copy(
                userName = Settings.staticUserName,
                password = Settings.staticPassword
            )
        }
    }

    /**
     * Retrieves a single Server Setting by its index
     */
    fun getServerSetting(index: Int): LiveData<ServerSetting?> {
        val liveData = MutableLiveData<ServerSetting?>()
        val server = STATIC_SERVERS.find { it.index == index }?.copy(
            userName = Settings.staticUserName,
            password = Settings.staticPassword
        )
        liveData.postValue(server)
        return liveData
    }

    /**
     * Moves a Setting up in the Server List - Disabled in static mode
     */
    fun moveItemUp(index: Int) {}

    /**
     * Moves a Setting down in the Server List - Disabled in static mode
     */
    fun moveItemDown(index: Int) {}

    /**
     * Removes a Setting from the database - Disabled in static mode
     */
    fun deleteItemById(id: Int) {}

    private fun isRequiredServer(server: ServerSetting): Boolean {
        return (server.name == LMS_NAME && server.url == LMS_URL) ||
            (server.name == LMS_ALPHA_NAME && server.url == LMS_ALPHA_URL)
    }

    /**
     * Updates a Setting. In static mode, this updates the global static credentials.
     */
    suspend fun updateItem(serverSetting: ServerSetting?) {
        if (serverSetting == null) return

        withContext(Dispatchers.IO) {
            Settings.staticUserName = serverSetting.userName
            Settings.staticPassword = serverSetting.password
            activeServerProvider.invalidateCache()
            Timber.d("updateItem updated static credentials")
        }
    }

    /**
     * Inserts a new Setting into the database
     */
    suspend fun saveNewItem(serverSetting: ServerSetting?) {
        // No new items allowed in static mode
        Timber.w("saveNewItem blocked in static mode")
    }

    /**
     * Parses the DEFAULT_SERVERS_JSON from BuildConfig and adds them to the database.
     * Also adds the demo server.
     */
    /**
     * Forces the first server from BuildConfig as the active server.
     * Only works in debug builds.
     */
    fun forceDefaultServer() {
        // Disabled in static mode
    }

    private suspend fun addConfiguredServers() {
        // Disabled in static mode
    }

    /**
     * Inserts a new Setting into the database
     * @return The id of the demo server
     */
    fun addDemoServer(): Int {
        return -1
    }


    companion object {
        const val LMS_NAME = "LMS"
        const val LMS_URL = "http://lms.teunschriks.nl"
        const val LMS_ALPHA_NAME = "alpha"
        const val LMS_ALPHA_URL = "http://lms-alpha.teunschriks.nl"

        val STATIC_SERVERS = listOf(
            ServerSetting(
                id = 0,
                index = 0,
                name = LMS_NAME,
                url = LMS_URL,
                userName = "",
                password = "",
                allowSelfSignedCertificate = true,
                forcePlainTextPassword = true,
                shareSupport = true,
                jukeboxByDefault = false,
                musicFolderId = null,
                minimumApiVersion = "1.13.0"
            ),
            ServerSetting(
                id = 1,
                index = 1,
                name = LMS_ALPHA_NAME,
                url = LMS_ALPHA_URL,
                userName = "",
                password = "",
                allowSelfSignedCertificate = true,
                forcePlainTextPassword = true,
                shareSupport = true,
                jukeboxByDefault = false,
                musicFolderId = null,
                minimumApiVersion = "1.13.0"
            )
        )
    }
}
