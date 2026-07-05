package org.moire.ultrasonic.fragment

import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities

interface FilterableFragment {
    fun setLayoutType(newType: LayoutType) {}
    fun setOrderType(newOrder: SortOrder)
    fun setOnSetsToggle(isSets: Boolean) {}
    val isSetsMode: Boolean get() = Settings.isSetsMode
    var viewCapabilities: ViewCapabilities
}
