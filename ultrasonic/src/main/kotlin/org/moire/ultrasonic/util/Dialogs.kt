/*
 * Dialogs.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.app.Activity
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.moire.ultrasonic.R

/*
 * InfoDialog can be used to show some information to the user. Typically it cannot be cancelled,
 * only dismissed via OK.
 */
open class InfoDialog(
    context: Context,
    message: CharSequence?,
    private val activity: Activity? = null,
    private val finishActivityOnClose: Boolean = false
) {

    open var builder: MaterialAlertDialogBuilder = Builder(activity ?: context, message)

    fun show() {
        builder.setOnCancelListener {
            if (finishActivityOnClose) {
                activity!!.finish()
            }
        }
        builder.setPositiveButton(R.string.common_ok) { _, _ ->
            if (finishActivityOnClose) {
                activity!!.finish()
            }
        }
        builder.create().show()
    }

    class Builder(context: Context) : MaterialAlertDialogBuilder(context) {

        constructor(context: Context, message: CharSequence?) : this(context) {
            setMessage(message)
        }

        init {
            setIcon(R.drawable.ic_baseline_info)
            setTitle(R.string.common_confirm)
            setPositiveButton(R.string.common_ok) { _, _ ->
                // Just close it
            }
        }
    }
}

/*
 * ErrorDialog can be used to show some an error to the user.
 * Typically it cannot be cancelled, only dismissed via OK.
 */
class ErrorDialog(
    context: Context,
    message: CharSequence?,
    activity: Activity? = null,
    finishActivityOnClose: Boolean = false
) : InfoDialog(context, message, activity, finishActivityOnClose) {

    override var builder: MaterialAlertDialogBuilder = Builder(activity ?: context, message)

    class Builder(context: Context) : MaterialAlertDialogBuilder(context) {
        constructor(context: Context, message: CharSequence?) : this(context) {
            setMessage(message)
        }

        init {
            setIcon(R.drawable.ic_baseline_warning)
            setTitle(R.string.error_label)
            setPositiveButton(R.string.common_ok) { _, _ ->
                // Just close it
            }
        }
    }
}

/*
 * ConfirmationDialog can be used to present a choice to the user.
 * Typically it will be cancelable..
 */
class ConfirmationDialog(
    context: Context,
    message: CharSequence?,
    activity: Activity? = null,
    finishActivityOnClose: Boolean = false
) : InfoDialog(context, message, activity, finishActivityOnClose) {

    override var builder: MaterialAlertDialogBuilder = Builder(activity ?: context, message)

    class Builder(context: Context) : MaterialAlertDialogBuilder(context) {
        constructor(context: Context, message: CharSequence?) : this(context) {
            setMessage(message)
        }

        init {
            setIcon(R.drawable.ic_baseline_info)
            setTitle(R.string.common_confirm)
            setCancelable(true)

            setPositiveButton(R.string.common_confirm) { _, _ ->
                // Gets overwritten
            }

            setNegativeButton(R.string.common_cancel) { _, _ ->
                // Just close it
            }
        }
    }
}
