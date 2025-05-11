package org.moire.ultrasonic.fragment.tsshadow

import FilterOptionsViewModel
import FilterState
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Observer
import com.google.android.material.slider.Slider
import org.moire.ultrasonic.R
import org.moire.ultrasonic.view.MultiSpinnerView

enum class FilterModalType {
    SONG, LIVESET
}

class FilterModalFragment : DialogFragment() {

    private val filterOptionsViewModel: FilterOptionsViewModel by activityViewModels()

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
    private lateinit var resultCountSlider: Slider
    private lateinit var resultCountLabel: TextView
    private val resultCountSteps = listOf(10, 25, 50, 100, 500, 2500)
    private var selectedResultCount = 25

    private lateinit var labelContainer: View
    private lateinit var labelSpinner: MultiSpinnerView
    private lateinit var festivalContainer: View
    private lateinit var festivalSpinner: MultiSpinnerView
    private lateinit var festivalLineupContainer: View
    private lateinit var festivalLineupSpinner: Spinner

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.UltrasonicFilterDialogTheme)
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
        festivalLineupContainer = view.findViewById(R.id.select_festival_lineup_container)
        festivalLineupSpinner = view.findViewById(R.id.select_festival_lineup)
        resultCountSlider = view.findViewById(R.id.select_result_count_slider)
        resultCountLabel = view.findViewById(R.id.select_result_count_label)

        searchButton = view.findViewById(R.id.search)
        saveButton = view.findViewById(R.id.save)
        updateButton = view.findViewById(R.id.update)
        deleteButton = view.findViewById(R.id.delete)

        setupAdapters()

        val initialFilters = arguments?.getParcelable<FilterState>("initialFilters")
        val editMode = arguments?.getBoolean("editMode") ?: false
        modalType = arguments?.getSerializable("type") as? FilterModalType ?: FilterModalType.SONG

        val modalTitle = if (editMode) getString(R.string.edit_filter_title) else getString(R.string.new_filter_title)
        view.findViewById<TextView>(R.id.filter_title).text = modalTitle

        labelContainer.visibility = if (modalType == FilterModalType.SONG) View.VISIBLE else View.GONE
        festivalLineupContainer.visibility = if (modalType == FilterModalType.SONG) View.VISIBLE else View.GONE
        festivalContainer.visibility = if (modalType == FilterModalType.LIVESET) View.VISIBLE else View.GONE

        showCorrectButtons(editMode)
        setupListeners()
        observeFilterOptions(initialFilters)
        initialFilters?.let { applyFilterState(it) }

        view.findViewById<ImageButton>(R.id.close_button).setOnClickListener {
            dismiss()
        }
    }

    private fun setupAdapters() {
        ratingMinSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, (0..5).toList())
        ratingMaxSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, (0..5).toList())
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
            count = selectedResultCount,
            ratingMin = ratingMinSpinner.selectedItem as Int,
            ratingMax = ratingMaxSpinner.selectedItem as Int,
            sortMethod = sortMethodMap[sortMethodSpinner.selectedItem as? String ?: ""] ?: "None",
            label = labelSpinner.getSelectedItems(),
            festival = festivalSpinner.getSelectedItems(),
            festivalLineup = (festivalLineupSpinner.selectedItem as? String)?.takeIf { it.isNotBlank() }
        )
    }

    private fun applyFilterState(state: FilterState) {
        titleInput.setText(state.title)
        ratingMinSpinner.setSelection(state.ratingMin)
        ratingMaxSpinner.setSelection(state.ratingMax)

        val resultIndex = resultCountSteps.indexOf(state.count).takeIf { it >= 0 } ?: 1
        resultCountSlider.value = resultIndex.toFloat()
        selectedResultCount = resultCountSteps[resultIndex]
        resultCountLabel.text = getString(R.string.result_count_format, selectedResultCount)

        state.festivalLineup?.let { lineup ->
            val adapter = festivalLineupSpinner.adapter as? ArrayAdapter<String>
            val pos = adapter?.getPosition(lineup)?.coerceAtLeast(0) ?: 0
            festivalLineupSpinner.setSelection(pos)
        }

        val index = sortMethodMap.values.indexOf(state.sortMethod)
        if (index >= 0) {
            sortMethodSpinner.setSelection(index)
        }
    }

    private fun observeFilterOptions(initialFilters: FilterState?) {
        filterOptionsViewModel.genres.observe(viewLifecycleOwner, Observer { genres ->
            genreSpinner.setItems(genres)
            initialFilters?.let { genreSpinner.setSelectedItems(it.genres) }
        })
        filterOptionsViewModel.years.observe(viewLifecycleOwner, Observer { years ->
            yearSpinner.setItems(years)
            initialFilters?.let { yearSpinner.setSelectedItems(it.years) }
        })
        filterOptionsViewModel.labels.observe(viewLifecycleOwner, Observer { labels ->
            labelSpinner.setItems(labels)
            initialFilters?.let { labelSpinner.setSelectedItems(it.label) }
        })
        filterOptionsViewModel.festivals.observe(viewLifecycleOwner, Observer { festivals ->
            festivalSpinner.setItems(festivals)
            initialFilters?.let { festivalSpinner.setSelectedItems(it.festival) }
        })

        filterOptionsViewModel.lineups.observe(viewLifecycleOwner, Observer { lineups ->
            festivalLineupSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                lineups
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            initialFilters?.festivalLineup?.let { lineup ->
                val pos = lineups.indexOf(lineup).coerceAtLeast(0)
                festivalLineupSpinner.setSelection(pos)
            }
        })

        resultCountSlider.addOnChangeListener { _, value, _ ->
            selectedResultCount = resultCountSteps[value.toInt()]
            resultCountLabel.text = getString(R.string.result_count_format, selectedResultCount)
        }
    }
}
