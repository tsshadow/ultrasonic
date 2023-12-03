package org.moire.ultrasonic.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.google.android.material.button.MaterialButton

class AutoRepeatButton : MaterialButton {
    private val initialRepeatDelay: Long = 1000
    private val repeatIntervalInMilliseconds: Long = 300
    private var doClick = true
    private var repeatEvent: Runnable? = null
    private val repeatClickWhileButtonHeldRunnable: Runnable = object : Runnable {
        override fun run() {
            doClick = false
            // Perform the present repetition of the click action provided by the user
            // in setOnClickListener().
            if (repeatEvent != null) repeatEvent!!.run()

            // Schedule the next repetitions of the click action, using a faster repeat
            // interval than the initial repeat delay interval.
            postDelayed(this, repeatIntervalInMilliseconds)
        }
    }

    private fun commonConstructorCode() {
        setOnTouchListener { _, event ->
            val action = event.action
            if (action == MotionEvent.ACTION_DOWN) {
                doClick = true
                // Just to be sure that we removed all callbacks,
                // which should have occurred in the ACTION_UP
                removeCallbacks(repeatClickWhileButtonHeldRunnable)

                // Schedule the start of repetitions after a one half second delay.
                postDelayed(repeatClickWhileButtonHeldRunnable, initialRepeatDelay)
                isPressed = true
            } else if (action == MotionEvent.ACTION_UP) {
                // Cancel any repetition in progress.
                removeCallbacks(repeatClickWhileButtonHeldRunnable)
                if (doClick || repeatEvent == null) {
                    performClick()
                }
                isPressed = false
            }

            // Returning true here prevents performClick() from getting called
            // in the usual manner, which would be redundant, given that we are
            // already calling it above.
            true
        }
    }

    fun setOnRepeatListener(runnable: Runnable?) {
        repeatEvent = runnable
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!,
        attrs,
        defStyle
    ) {
        commonConstructorCode()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!,
        attrs
    ) {
        commonConstructorCode()
    }

    constructor(context: Context?) : super(context!!) {
        commonConstructorCode()
    }
}
