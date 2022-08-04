/*
 * OnBackPressedHandler.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

/**
 * Interface for fragments handling their own Back button
 */
interface OnBackPressedHandler {
    fun onBackPressed()
}
