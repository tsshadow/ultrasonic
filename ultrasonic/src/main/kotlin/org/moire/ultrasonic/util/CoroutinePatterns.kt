/*
 * CoroutinePatterns.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.moire.ultrasonic.app.UApp
import timber.log.Timber

object CoroutinePatterns {
    val loggingExceptionHandler by lazy {
        CoroutineExceptionHandler { _, exception ->
            Handler(Looper.getMainLooper()).post {
                Timber.w(exception)
            }
        }
    }
}

fun CoroutineScope.launchWithToast(
    block: suspend CoroutineScope.() -> String?
) {
    // Launch the Job
    val deferred = async(CoroutinePatterns.loggingExceptionHandler, block = block)

    // Setup a handler when the job is done
    deferred.invokeOnCompletion {
        val toastString = if (it != null && it !is CancellationException) {
            CommunicationError.getErrorMessage(it)
        } else {
            null
        }

        launch(Dispatchers.Main) {
            val successString = toastString ?: deferred.await()
            if (successString != null) {
                Util.toast(successString, UApp.applicationContext())
            }
        }
    }
}

// Unused, kept commented for eventual later use
// fun CoroutineScope.executeTaskWithModalDialog(
//    fragment: Fragment,
//    task: suspend CoroutineScope.() -> String?
// ) {
//    // Create the job
//    val job = launchWithToast(task)
//
//    // Create the dialog
//    val builder = InfoDialog.Builder(fragment.requireContext())
//    builder.setTitle(R.string.background_task_wait)
//    builder.setMessage(R.string.background_task_loading)
//    builder.setOnCancelListener { job.cancel() }
//    builder.setPositiveButton(R.string.common_cancel) { _, _ -> job.cancel() }
//    val dialog = builder.create()
//    dialog.show()
//
//    // Add additional handler to close the dialog
//    job.invokeOnCompletion {
//        launch(Dispatchers.Main) {
//            dialog.dismiss()
//        }
//    }
// }
