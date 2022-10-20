/*
 * Storage.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import timber.log.Timber

/**
 * Provides filesystem access abstraction which works
 * both on File based paths and Storage Access Framework Uris
 */
object Storage {

    val mediaRoot: ResettableLazy<AbstractFile> = ResettableLazy {
        getRoot()!!
    }

    fun reset() {
        StorageFile.storageFilePathDictionary.clear()
        StorageFile.notExistingPathDictionary.clear()
        mediaRoot.reset()
        Timber.i("StorageFile caches were reset")
    }

    fun ensureRootIsAvailable() {
        val root = getRoot()
        if (root == null) {
            Settings.customCacheLocation = false
            Settings.cacheLocationUri = ""
            Util.toast(UApp.applicationContext(), R.string.settings_cache_location_error)
        }
    }

    fun getOrCreateFileFromPath(path: String): AbstractFile {
        return mediaRoot.value.getOrCreateFileFromPath(path)
    }

    fun isPathExists(path: String): Boolean {
        return mediaRoot.value.isPathExists(path)
    }

    fun getFromPath(path: String): AbstractFile? {
        return mediaRoot.value.getFromPath(path)
    }

    fun createDirsOnPath(path: String) {
        mediaRoot.value.createDirsOnPath(path)
    }

    fun rename(pathFrom: String, pathTo: String) {
        mediaRoot.value.rename(pathFrom, pathTo)
    }

    fun rename(pathFrom: AbstractFile, pathTo: String) {
        mediaRoot.value.rename(pathFrom, pathTo)
    }

    fun renameOrDeleteIfAlreadyExists(pathFrom: AbstractFile, pathTo: String) {
        try {
            rename(pathFrom, pathTo)
        } catch (ignored: FileAlreadyExistsException) {
            // Play console has revealed a crash when for some reason both files exist
            delete(pathFrom.path)
        }
    }

    fun delete(path: String): Boolean {
        // Some implementations will return false on Error,
        // others will throw a FileNotFoundException...
        // Handle both here..

        val success: Boolean

        try {
            val storageFile = getFromPath(path)
            success = storageFile?.delete() == true
        } catch (all: Exception) {
            Timber.d(all, "Failed to delete file $path")
            return false
        }

        if (!success) {
            Timber.d("Failed to delete file %s", path)
        }

        return success
    }

    private fun getRoot(): AbstractFile? {
        return if (Settings.customCacheLocation) {
            if (Settings.cacheLocationUri.isBlank()) return null
            val documentFile = DocumentFile.fromTreeUri(
                UApp.applicationContext(),
                Uri.parse(Settings.cacheLocationUri)
            ) ?: return null
            if (!documentFile.exists()) return null
            StorageFile(null, documentFile.uri, documentFile.name!!, documentFile.isDirectory)
        } else {
            val file = File(FileUtil.defaultMusicDirectory.path)
            JavaFile(null, file)
        }
    }
}
