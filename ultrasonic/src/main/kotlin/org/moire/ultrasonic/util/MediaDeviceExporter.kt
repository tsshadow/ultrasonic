/*
 * MediaDeviceExporter.kt
 * Copyright (C) 2024 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import timber.log.Timber

/**
 * Utility class to export tracks to an external media device using Storage Access Framework.
 */
class MediaDeviceExporter(private val context: Context) {

    /**
     * Exports a list of tracks to the given destination URI.
     *
     * @param destUri The URI of the destination folder (from SAF).
     * @param tracks The list of tracks to export.
     * @param clearFirst Whether to delete all files in the destination folder before exporting.
     * @param onProgress Callback for progress updates (current, total, trackTitle).
     */
    suspend fun export(
        destUri: Uri,
        tracks: List<Track>,
        clearFirst: Boolean,
        onProgress: suspend (Int, Int, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val destDir = DocumentFile.fromTreeUri(context, destUri) ?: return@withContext

        if (clearFirst) {
            destDir.listFiles().forEach {
                try {
                    it.delete()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete file: ${it.name}")
                }
            }
        }

        val total = tracks.size
        tracks.forEachIndexed { index, track ->
            val trackTitle = track.title ?: "Unknown"
            onProgress(index + 1, total, trackTitle)

            try {
                // Construct filename: "01 - Title.mp3"
                val trackNum = track.track?.toString()?.padStart(2, '0') ?: "00"
                val extension = track.suffix ?: "mp3"
                val fileName = "$trackNum - $trackTitle.$extension"
                // Replace illegal characters
                val safeFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

                // Check if file already exists (if we didn't clear)
                val existingFile = if (!clearFirst) destDir.findFile(safeFileName) else null
                if (existingFile != null) {
                    Timber.d("File already exists, skipping: $safeFileName")
                    return@forEachIndexed
                }

                val file = destDir.createFile(track.contentType ?: "audio/mpeg", safeFileName)
                file?.let { documentFile ->
                    context.contentResolver.openOutputStream(documentFile.uri)?.use { outputStream ->
                        val musicService = getMusicService()
                        // Use max quality for export (0 means unlimited)
                        val streamResult = musicService.getDownloadInputStream(track, 0, 0, false)
                        streamResult.first.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export track: ${track.title}")
            }
        }
    }
}
