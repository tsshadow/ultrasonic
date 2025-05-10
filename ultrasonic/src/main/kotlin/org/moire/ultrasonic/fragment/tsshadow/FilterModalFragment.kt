package org.moire.ultrasonic.fragment.tsshadow

import FilterState
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.MultiSpinnerView

enum class FilterModalType {
    SONG, LIVESET
}

class FilterModalFragment : DialogFragment() {

    private lateinit var genreSpinner: MultiSpinnerView
    private lateinit var yearSpinner: MultiSpinnerView
    private lateinit var ratingMinSpinner: Spinner
    private lateinit var ratingMaxSpinner: Spinner
    private lateinit var sortMethodSpinner: Spinner
    private lateinit var titleInput: EditText
    private lateinit var searchButton: Button
    private lateinit var saveButton: Button
    private lateinit var updateButton: Button
    private lateinit var deleteButton: Button

    private lateinit var labelContainer: View
    private lateinit var labelSpinner: MultiSpinnerView
    private lateinit var festivalContainer: View
    private lateinit var festivalSpinner: MultiSpinnerView

    private lateinit var modalType: FilterModalType

    private val sortMethodMap by lazy {
        mapOf(
            getString(R.string.sort_none) to "None",
            getString(R.string.sort_random) to "Random",
            getString(R.string.sort_added_desc) to "AddedDesc",
            getString(R.string.sort_written_desc) to "LastWrittenDesc"
        )
    }

    companion object {
        fun newInstance(
            initialFilters: FilterState?,
            editMode: Boolean,
            type: FilterModalType
        ): FilterModalFragment {
            val fragment = FilterModalFragment()
            val args = Bundle().apply {
                putParcelable("initialFilters", initialFilters)
                putBoolean("editMode", editMode)
                putSerializable("type", type)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.tsshadow_filters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        genreSpinner = view.findViewById(R.id.select_genre)
        yearSpinner = view.findViewById(R.id.select_year)
        ratingMinSpinner = view.findViewById(R.id.select_rating_min)
        ratingMaxSpinner = view.findViewById(R.id.select_rating_max)
        sortMethodSpinner = view.findViewById(R.id.select_sort_method)
        titleInput = view.findViewById(R.id.select_title)

        labelContainer = view.findViewById(R.id.select_label_container)
        labelSpinner = view.findViewById(R.id.select_label)
        festivalContainer = view.findViewById(R.id.select_festival_container)
        festivalSpinner = view.findViewById(R.id.select_festival)

        searchButton = view.findViewById(R.id.search)
        saveButton = view.findViewById(R.id.save)
        updateButton = view.findViewById(R.id.update)
        deleteButton = view.findViewById(R.id.delete)

        setupAdapters()

        val initialFilters = arguments?.getParcelable<FilterState>("initialFilters")
        val editMode = arguments?.getBoolean("editMode") ?: false
        modalType = arguments?.getSerializable("type") as? FilterModalType ?: FilterModalType.SONG

        labelContainer.visibility = if (modalType == FilterModalType.SONG) View.VISIBLE else View.GONE
        festivalContainer.visibility = if (modalType == FilterModalType.LIVESET) View.VISIBLE else View.GONE

        initialFilters?.let { applyFilterState(it) }

        showCorrectButtons(editMode)
        setupListeners()
        loadFilterOptions()

        // sluitknop (kruisje)
        view.findViewById<ImageButton>(R.id.close_button).setOnClickListener {
            dismiss()
        }
    }

    private fun setupAdapters() {
        ratingMinSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, (0..5).toList())
        ratingMaxSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, (0..5).toList())
        ratingMaxSpinner.setSelection(5)

        val translatedSortMethods = sortMethodMap.keys.toList()
        sortMethodSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            translatedSortMethods
        )
    }

    private fun showCorrectButtons(editMode: Boolean) {
        if (editMode) {
            saveButton.visibility = View.GONE
            searchButton.visibility = View.GONE
            updateButton.visibility = View.VISIBLE
            deleteButton.visibility = View.VISIBLE
        } else {
            saveButton.visibility = View.VISIBLE
            searchButton.visibility = View.VISIBLE
            updateButton.visibility = View.GONE
            deleteButton.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        fun sendResult(action: String) {
            val filterState = collectFilterState()
            val result = Bundle().apply {
                putParcelable("filters", filterState)
                putString("action", action)
            }
            setFragmentResult("filters_result", result)
            dismiss()
        }

        searchButton.setOnClickListener { sendResult("search") }
        saveButton.setOnClickListener { sendResult("save") }
        updateButton.setOnClickListener { sendResult("update") }
        deleteButton.setOnClickListener { sendResult("delete") }
    }

    private fun collectFilterState(): FilterState {
        return FilterState(
            title = titleInput.text.toString(),
            genres = genreSpinner.getSelectedItems(),
            years = yearSpinner.getSelectedItems(),
            ratingMin = ratingMinSpinner.selectedItem as Int,
            ratingMax = ratingMaxSpinner.selectedItem as Int,
            sortMethod = sortMethodMap[sortMethodSpinner.selectedItem as? String ?: ""] ?: "None",
            label = labelSpinner.getSelectedItems(),
            festival = festivalSpinner.getSelectedItems()
        )
    }

    private fun applyFilterState(state: FilterState) {
        titleInput.setText(state.title)
        genreSpinner.setSelectedItems(state.genres)
        yearSpinner.setSelectedItems(state.years)
        ratingMinSpinner.setSelection(state.ratingMin)
        ratingMaxSpinner.setSelection(state.ratingMax)

        val index = sortMethodMap.values.indexOf(state.sortMethod)
        if (index >= 0) {
            sortMethodSpinner.setSelection(index)
        }

        labelSpinner.setSelectedItems(state.label)
        festivalSpinner.setSelectedItems(state.festival)
    }

    private fun loadFilterOptions() {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val musicService = getMusicService()

            val genres = withContext(Dispatchers.IO) {
                musicService.getGenres(false, null, null)
            }
            genreSpinner.setItems(genres.map { it.name })

            val years = withContext(Dispatchers.IO) {
                musicService.getTags(false, "YEAR", null, null)
            }.sortedByDescending { it.name }
            yearSpinner.setItems(years.map { it.name })

            if (modalType == FilterModalType.SONG) {
                val labels = withContext(Dispatchers.IO) {
                    musicService.getTags(false, "PUBLISHER", null, null)
                }
                labelSpinner.setItems(labels.map { it.name })
            }

            if (modalType == FilterModalType.LIVESET) {
                val festivals = withContext(Dispatchers.IO) {
                    musicService.getTags(false, "FESTIVAL", null, null)
                }
                festivalSpinner.setItems(festivals.map { it.name })
            }
        }
    }
}
