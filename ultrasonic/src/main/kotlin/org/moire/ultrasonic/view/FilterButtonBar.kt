/*
 * FilterButtonBar.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/**
 * This Widget provides a FilterBar, which allow to set the layout options
 * or sort order of a linked fragment
 */
class FilterButtonBar : ConstraintLayout {
    private var adapter: ArrayAdapter<TranslatedSortOrder>? = null
    private var orderChangedListener: ((SortOrder) -> Unit)? = null
    private var layoutTypeChangedListener: ((LayoutType) -> Unit)? = null
    private var layoutType: LayoutType = LayoutType.LIST
    private var viewTypeToggle: Chip? = null
    private var setsToggle: MaterialSwitch? = null
    private var sortOrderMenu: TextInputLayout? = null
    private var sortOrderOptions: AppCompatAutoCompleteTextView? = null
    private var setsToggleListener: ((Boolean) -> Unit)? = null

    constructor(context: Context) : super(context) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.filter_button_bar, this)
        setup()
    }

    /**
     * Set which capabilities are supported by
     * the linked fragment and configure the UI accordingly
     *
     * @param caps
     */
    fun configureWithCapabilities(caps: ViewCapabilities) {
        viewTypeToggle!!.isVisible = caps.supportsGrid
        setsToggle?.isVisible = caps.supportsSetsToggle
        if (caps.supportsSetsToggle) {
            setsToggle?.isChecked = Settings.isSetsMode
        }
        updateBrandColors()
        sortOrderMenu!!.isVisible = caps.supportedSortOrders.isNotEmpty()

        if (caps.supportedSortOrders.isNotEmpty()) {
            Timber.i("Calculating order")
            val translatedOrders = caps.supportedSortOrders.map {
                TranslatedSortOrder(
                    sortOrder = it,
                    string = context.getString(getStringForSortOrder(it))
                )
            }
            // Fill the visible with  the first available option
            sortOrderOptions!!.setText(getStringForSortOrder(translatedOrders.first().sortOrder))
            adapter!!.clear()
            // Next line addresses a bug in Android components:
            // https://github.com/material-components/material-components-android/issues/1464
            adapter!!.filter.filter("")
            adapter!!.addAll(translatedOrders)
        }
    }

    /**
     * This listener is called when the user has changed the layout type.
     * Register a callback from the linked fragment here, to trigger a relayout
     *
     * @param callback
     * @receiver
     */
    fun setOnLayoutTypeChangedListener(callback: (LayoutType) -> Unit) {
        layoutTypeChangedListener = callback
    }

    /**
     * This listener is called when the user has changed the sort order.
     * Register a callback from the linked fragment here, to trigger a resort
     *
     * @param callback
     * @receiver
     */
    fun setOnOrderChangedListener(callback: (SortOrder) -> Unit) {
        orderChangedListener = callback
    }

    /**
     * This listener is called when the user has toggled the sets mode.
     * Register a callback from the linked fragment here.
     *
     * @param callback
     */
    fun setOnSetsToggleListener(callback: (Boolean) -> Unit) {
        setsToggleListener = callback
    }

    /**
     * Setup the necessary bindings
     */

    fun setup() {
        // Link layout toggle Chip
        viewTypeToggle = findViewById(R.id.chip_view_toggle)
        setsToggle = findViewById(R.id.switch_sets)
        sortOrderMenu = findViewById(R.id.sort_order_menu)
        sortOrderOptions = findViewById(R.id.sort_order_menu_options)

        viewTypeToggle!!.setOnClickListener {
            val newType = setLayoutType()
            layoutTypeChangedListener?.let { it(newType) }
        }

        setsToggle?.setOnCheckedChangeListener { _, isChecked ->
            setsToggleListener?.invoke(isChecked)
        }

        @SuppressLint("PrivateResource")
        adapter = ArrayAdapter<TranslatedSortOrder>(
            context,
            com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
            mutableListOf<TranslatedSortOrder>()
        )

        sortOrderOptions!!.setAdapter(adapter)
        sortOrderOptions!!.setOnItemClickListener { _, _, position, _ ->
            val item = adapter!!.getItem(position)
            item?.let { setOrderType(it.sortOrder) }
        }
    }

    /**
     * This function can be called from externally to set the layout type programmatically
     *
     * @param newType
     */
    fun setLayoutType(newType: LayoutType = toggleLayoutType()): LayoutType {
        layoutType = newType
        updateToggleChipState(newType)
        return newType
    }

    private fun toggleLayoutType(): LayoutType = when (layoutType) {
        LayoutType.LIST -> {
            LayoutType.COVER
        }
        LayoutType.COVER -> {
            LayoutType.LIST
        }
    }

    /**
     * This function can be called from externally to set the layout type programmatically
     *
     * @param order
     */
    fun setOrderType(order: SortOrder) {
        orderChangedListener?.let { it(order) }
    }

    private fun updateToggleChipState(layoutType: LayoutType) {
        when (layoutType) {
            LayoutType.COVER -> {
                viewTypeToggle!!.chipIcon = AppCompatResources.getDrawable(
                    context,
                    R.drawable.ic_baseline_view_grid
                )
                viewTypeToggle!!.text = context.getString(R.string.grid_view)
            }
            LayoutType.LIST -> {
                viewTypeToggle!!.chipIcon = AppCompatResources.getDrawable(
                    context,
                    R.drawable.ic_baseline_view_list
                )
                viewTypeToggle!!.text = context.getString(R.string.list_view)
            }
        }
    }

    /**
     * Update the accent colors based on the current mode
     */
    fun updateBrandColors() {
        val brandColor = if (Settings.isSetsMode) {
            context.getColor(R.color.spotify_blue)
        } else {
            context.getColor(R.color.spotify_green)
        }

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(brandColor, Color.WHITE)
        val trackColors = intArrayOf(brandColor.withAlpha(0x80), Color.DKGRAY)

        setsToggle?.thumbTintList = ColorStateList(states, thumbColors)
        setsToggle?.trackTintList = ColorStateList(states, trackColors)

        // Update chips if they have green accents
        viewTypeToggle?.chipIconTint = ColorStateList.valueOf(brandColor)
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun getStringForSortOrder(sortOrder: SortOrder): Int = when (sortOrder) {
        SortOrder.RANDOM -> R.string.main_albums_random
        SortOrder.NEWEST -> R.string.main_albums_newest
        SortOrder.HIGHEST -> R.string.main_albums_highest
        SortOrder.FREQUENT -> R.string.main_albums_frequent
        SortOrder.RECENT -> R.string.main_albums_recent
        SortOrder.BY_NAME -> R.string.main_albums_alphaByName
        SortOrder.BY_ARTIST -> R.string.main_albums_alphaByArtist
        SortOrder.STARRED -> R.string.main_albums_starred
        SortOrder.BY_YEAR -> R.string.main_albums_by_year
    }
}

/**
 * Wrapper which contains one sort order and the translated UI string for it
 *
 * @property sortOrder
 * @property string
 */
data class TranslatedSortOrder(val sortOrder: SortOrder, val string: String) {
    override fun toString() = string
}

/**
 * ViewCapabilities defines which layout and sort orders a view can support
 *
 * @property supportsGrid
 * @property supportedSortOrders
 */
data class ViewCapabilities(
    val supportsGrid: Boolean = false,
    val supportsSetsToggle: Boolean = false,
    val supportedSortOrders: List<SortOrder>
)

val EMPTY_CAPABILITIES = ViewCapabilities(
    supportsGrid = false,
    supportsSetsToggle = false,
    supportedSortOrders = listOf()
)
