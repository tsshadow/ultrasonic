/*
 * CoroutinePatterns.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
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

fun CoroutineScope.executeTaskWithToast(
    fragment: Fragment,
    task: suspend CoroutineScope.() -> Unit,
    successString: () -> String?
): Job {
    // Launch the Job
    val job = launch(CoroutinePatterns.loggingExceptionHandler, block = task)

    // Setup a handler when the job is done
    job.invokeOnCompletion {
        val toastString = if (it != null && it !is CancellationException) {
            CommunicationError.getErrorMessage(it)
        } else {
            successString()
        }

        // Return early if nothing to post
        if (toastString == null) return@invokeOnCompletion

        launch(Dispatchers.Main) {
            Util.toast(UApp.applicationContext(), toastString)
        }
    }

    return job
}

fun CoroutineScope.executeTaskWithModalDialog(
    fragment: Fragment,
    task: suspend CoroutineScope.() -> Unit,
    successString: () -> String
) {
    // Create the job
    val job = executeTaskWithToast(fragment, task, successString)

    // Create the dialog
    val builder = InfoDialog.Builder(fragment.requireContext())
    builder.setTitle(R.string.background_task_wait)
    builder.setMessage(R.string.background_task_loading)
    builder.setOnCancelListener { job.cancel() }
    builder.setPositiveButton(R.string.common_cancel) { _, _ -> job.cancel() }
    val dialog = builder.create()
    dialog.show()

    // Add additional handler to close the dialog
    job.invokeOnCompletion {
        launch(Dispatchers.Main) {
            dialog.dismiss()
        }
    }
}
