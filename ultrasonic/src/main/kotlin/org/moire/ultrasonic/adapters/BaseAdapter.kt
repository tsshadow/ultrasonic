/*
 * BaseAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import com.drakeet.multitype.MultiTypeAdapter
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.util.BoundedTreeSet
import org.moire.ultrasonic.util.SettableAsyncListDiffer
import timber.log.Timber

/**
 * The BaseAdapter which extends the MultiTypeAdapter from an external library.
 * It provides selection support as well as Diffing the submitted lists for performance.
 *
 * It should be kept generic enough that it can be used a Base for all lists in the app.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class BaseAdapter<T : Identifiable>(allowDuplicateEntries: Boolean = false) :
    MultiTypeAdapter(), FastScrollRecyclerView.SectionedAdapter {

    // Update the BoundedTreeSet if selection type is changed
    internal var selectionType: SelectionType = SelectionType.MULTIPLE
        set(newValue) {
            field = newValue
            selectedSet.setMaxSize(newValue.size)
        }

    internal var selectedSet: BoundedTreeSet<Long> = BoundedTreeSet(selectionType.size)
    internal var selectionRevision: MutableLiveData<Int> = MutableLiveData(0)

    private val diffCallback = GenericDiffCallback<T>()

    init {
        setHasStableIds(!allowDuplicateEntries)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).longId
    }

    private fun getItem(position: Int): T {
        return mDiffer.currentList[position]
    }

    override var items: List<Any>
        get() = getCurrentList()
        set(value) {
            throw IllegalAccessException("You must use submitList() to add data to the Adapter")
        }

    private var mDiffer: SettableAsyncListDiffer<T> = SettableAsyncListDiffer(
        AdapterListUpdateCallback(this),
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val mListener: SettableAsyncListDiffer.ListListener<T> =
        object : SettableAsyncListDiffer.ListListener<T> {
            override fun onCurrentListChanged(previousList: List<T>, currentList: List<T>) {
                this@BaseAdapter.onCurrentListChanged(
                    previousList,
                    currentList
                )
            }
        }

    init {
        mDiffer.addListListener(mListener)
    }

    /**
     * Sets the List to a new value without invoking the diffing
     */
    fun setList(newList: List<T>) {
        Timber.v("SetList updated list in differ, size %s", newList.size)
        mDiffer.setList(newList)
    }

    /**
     * Submits a new list to be diffed, and displayed.
     *
     *
     * If a list is already being displayed, a diff will be computed on a background thread, which
     * will dispatch Adapter.notifyItem events on the main thread.
     *
     * @param list The new list to be displayed.
     */
    fun submitList(list: List<T>?) {
        Timber.v("Received fresh list, size %s", list?.size)
        mDiffer.submitList(list)
    }

    /**
     * Set the new list to be displayed.
     *
     *
     * If a List is already being displayed, a diff will be computed on a background thread, which
     * will dispatch Adapter.notifyItem events on the main thread.
     *
     *
     * The commit callback can be used to know when the List is committed, but note that it
     * may not be executed. If List B is submitted immediately after List A, and is
     * committed directly, the callback associated with List A will not be run.
     *
     * @param list The new list to be displayed.
     * @param commitCallback Optional runnable that is executed when the List is committed, if
     * it is committed.
     */
    fun submitList(list: List<T>?, commitCallback: Runnable?) {
        mDiffer.submitList(list, commitCallback)
    }

    override fun getItemCount(): Int {
        return mDiffer.currentList.size
    }

    /**
     * Get the current List - any diffing to present this list has already been computed and
     * dispatched via the ListUpdateCallback.
     *
     *
     * If a `null` List, or no List has been submitted, an empty list will be returned.
     *
     *
     * The returned list may not be mutated - mutations to content must be done through
     * [.submitList].
     *
     * @return The list currently being displayed.
     *
     * @see .onCurrentListChanged
     */
    fun getCurrentList(): List<T> {
        return mDiffer.currentList
    }

    /**
     * Called when the current List is updated.
     *
     *
     * If a `null` List is passed to [.submitList], or no List has been
     * submitted, the current List is represented as an empty List.
     *
     * @param previousList List that was displayed previously.
     * @param currentList new List being displayed, will be empty if `null` was passed to
     * [.submitList].
     *
     * @see .getCurrentList
     */
    fun onCurrentListChanged(previousList: List<T>, currentList: List<T>) {
        previousList.minus(currentList.toSet()).map {
            selectedSet.remove(it.longId)
        }
        selectionRevision.postValue(selectionRevision.value!! + 1)
    }

    fun notifySelected(id: Long) {
        selectedSet.add(id)

        // Update revision counter
        selectionRevision.postValue(selectionRevision.value!! + 1)
    }

    fun notifyUnselected(id: Long) {
        selectedSet.remove(id)

        // Update revision counter
        selectionRevision.postValue(selectionRevision.value!! + 1)
    }

    fun notifyChanged() {
        // When the download state of an entry was changed by an external process,
        // increase the revision counter in order to update the UI
        selectionRevision.postValue(selectionRevision.value!! + 1)
    }

    fun setSelectionStatusOfAll(select: Boolean): Int {
        // Clear current selection
        selectedSet.clear()

        // Update revision counter
        selectionRevision.postValue(selectionRevision.value!! + 1)

        // Nothing to reselect
        if (!select) return 0

        // Select them all
        getCurrentList().mapNotNullTo(
            selectedSet
        ) { entry ->
            // Exclude any -1 ids, eg. headers and other UI elements
            entry.longId.takeIf { it != -1L }
        }

        return selectedSet.count()
    }

    fun isSelected(longId: Long): Boolean {
        return selectedSet.contains(longId)
    }

    fun hasSingleSelection(): Boolean {
        return selectionType == SelectionType.SINGLE
    }

    fun hasMultipleSelection(): Boolean {
        return selectionType == SelectionType.MULTIPLE
    }

    enum class SelectionType(val size: Int) {
        SINGLE(1),
        MULTIPLE(Int.MAX_VALUE)
    }

    /**
     * Calculates the differences between data sets
     */
    class GenericDiffCallback<T : Identifiable> : DiffUtil.ItemCallback<T>() {
        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem == newItem
        }

        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem.id == newItem.id
        }
    }

    override fun getSectionName(position: Int): String {
        val type = getItemViewType(position)
        val binder = types.getType<Any>(type).delegate

        if (binder is Utils.SectionedBinder) {
            return binder.getSectionName(items[position] as Identifiable)
        }

        return ""
    }
}
