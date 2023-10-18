/*
 * RefreshableFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

interface RefreshableFragment {
    var swipeRefresh: SwipeRefreshLayout?
}
