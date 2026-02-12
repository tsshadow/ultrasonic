package org.moire.ultrasonic.fragment.tsshadow

import FilterOptionsViewModel
import FilterState
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import org.moire.ultrasonic.R
import org.moire.ultrasonic.databinding.TsshadowFiltersBinding

enum class FilterModalType { SONG, LIVESET }

class FilterModalFragment : BottomSheetDialogFragment() {

    private var _binding: TsshadowFiltersBinding? = null
    private val binding get() = _binding!!

    private val filterOptionsViewModel: FilterOptionsViewModel by activityViewModels()

    private val selectedGenres = mutableListOf<String>()
    private val selectedArtists = mutableListOf<String>()
    private val selectedYears = mutableListOf<String>()
    private val selectedLabels = mutableListOf<String>()
    private var selectedFestivalLineup: String? = null
    private val selectedFestivals = mutableListOf<String>()

    private var selectedResultCount = 25
    private val resultCountSteps = listOf(10, 25, 50, 100, 500, 2500)

    private val sortOptions by lazy {
        listOf(
            getString(R.string.sort_none) to "None",
            getString(R.string.sort_random) to "Random",
            getString(R.string.sort_date_and_release) to "DateDescAndRelease",
            getString(R.string.sort_added_desc) to "AddedDesc",
            getString(R.string.sort_written_desc) to "LastWrittenDesc"
        )
    }

    private lateinit var modalType: FilterModalType

    companion object {
        private const val ARG_INITIAL_FILTERS = "initialFilters"
        private const val ARG_EDIT_MODE = "editMode"
        private const val ARG_TYPE = "type"

        fun newInstance(
            initialFilters: FilterState?,
            editMode: Boolean,
            type: FilterModalType
        ): FilterModalFragment = FilterModalFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_INITIAL_FILTERS, initialFilters)
                putBoolean(ARG_EDIT_MODE, editMode)
                putSerializable(ARG_TYPE, type)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.UltrasonicFilterDialogTheme)
        modalType =
            BundleCompat.getSerializable(
                requireArguments(),
                ARG_TYPE,
                FilterModalType::class.java
            )!!
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(requireContext(), theme).apply {
        setOnShowListener {
            val dialog = this
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = resources.displayMetrics.heightPixels
                it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                it.requestLayout()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TsshadowFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()
        applyInitialFilters()
        observeFilterOptions()

        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }

    private fun setupAdapters() {
        val ratings = (0..5).toList()
        binding.selectRatingMin.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            ratings
        )
        binding.selectRatingMax.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            ratings
        )
        binding.selectRatingMax.setSelection(ratings.lastIndex)
        binding.selectSortMethod.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            sortOptions.map { it.first }
        )
    }

    private fun setupListeners() {
        binding.selectResultCountSlider.apply {
            valueFrom = 0f
            valueTo = (resultCountSteps.size - 1).toFloat()
            stepSize = 1f
            value = resultCountSteps.indexOf(selectedResultCount).toFloat()
            addOnChangeListener { _: Slider, value: Float, _: Boolean ->
                selectedResultCount = resultCountSteps[value.toInt()]
                binding.selectResultCountLabel.text =
                    getString(R.string.result_count_format, selectedResultCount)
            }
        }
        with(binding) {
            search.setOnClickListener { sendResult("search") }
            save.setOnClickListener { sendResult("save") }
            update.setOnClickListener { sendResult("update") }
            delete.setOnClickListener { sendResult("delete") }
            favoriteButton.setOnClickListener {
                favoriteButton.isSelected = !favoriteButton.isSelected
                val iconRes =
                    if (favoriteButton.isSelected) R.drawable.ic_star_full else R.drawable.ic_star_hollow
                favoriteButton.setImageResource(iconRes)
            }
        }
    }

    private fun sendResult(action: String) {
        val filters = collectFilterState()
        setFragmentResult(
            "filters_result",
            bundleOf(
                "filters" to filters,
                "action" to action
            )
        )
        dismiss()
    }

    private fun observeFilterOptions() {
        val redraw: () -> Unit = {
            redrawAllChips(
                filterOptionsViewModel.genres.value,
                filterOptionsViewModel.years.value,
                filterOptionsViewModel.labels.value,
                filterOptionsViewModel.lineups.value,
                filterOptionsViewModel.festivals.value,
                filterOptionsViewModel.artists.value
            )
        }
        filterOptionsViewModel.genres.observe(viewLifecycleOwner) { redraw() }
        filterOptionsViewModel.years.observe(viewLifecycleOwner) { redraw() }
        filterOptionsViewModel.labels.observe(viewLifecycleOwner) { redraw() }
        filterOptionsViewModel.lineups.observe(viewLifecycleOwner) { redraw() }
        filterOptionsViewModel.festivals.observe(viewLifecycleOwner) { redraw() }
        filterOptionsViewModel.artists.observe(viewLifecycleOwner) { redraw() }
        filterOptionsViewModel.favorite.observe(viewLifecycleOwner) { favorite ->
            binding.favoriteButton.isSelected = favorite
            val iconRes = if (favorite) R.drawable.ic_star_full else R.drawable.ic_star_hollow
            binding.favoriteButton.setImageResource(iconRes)
        }
    }

    private fun redrawAllChips(
        genres: List<String>?,
        years: List<String>?,
        labels: List<String>?,
        lineups: List<String>?,
        festivals: List<String>?,
        artists: List<String>?
    ) {
        val group = binding.addChipGroup
        group.removeAllViews()

        fun addSelectedChips(
            title: String,
            values: MutableList<String>,
            allOptions: List<String>?
        ) {
            values.forEach { value ->
                val chip = layoutInflater.inflate(R.layout.tsshadow_chip, group, false) as Chip
                chip.text = value
                chip.setOnCloseIconClickListener {
                    values.remove(value)
                    redrawAllChips(genres, years, labels, lineups, festivals, artists)
                }
                group.addView(chip)
            }
            if (values.isEmpty() && allOptions != null) {
                val addChip =
                    layoutInflater.inflate(R.layout.tsshadow_chip_add, group, false) as Chip
                addChip.text = getString(R.string.tsshadow_add_format, title)
                addChip.setOnClickListener {
                    val checked = BooleanArray(allOptions.size)
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.tsshadow_select_format, title))
                        .setMultiChoiceItems(
                            allOptions.toTypedArray(),
                            checked
                        ) { _, which, isChecked ->
                            checked[which] = isChecked
                        }
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val result = allOptions.filterIndexed { index, _ -> checked[index] }
                            values.clear()
                            values.addAll(result)
                            redrawAllChips(genres, years, labels, lineups, festivals, artists)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                group.addView(addChip)
            }
        }

        addSelectedChips(getString(R.string.year), selectedYears, years)
        addSelectedChips(getString(R.string.genre), selectedGenres, genres)

        if (modalType == FilterModalType.SONG) {
            addSelectedChips(getString(R.string.common_artist), selectedArtists, artists)
            addSelectedChips(getString(R.string.label), selectedLabels, labels)
            if (selectedFestivalLineup != null) {
                val chip = layoutInflater.inflate(R.layout.tsshadow_chip, group, false) as Chip
                chip.text = selectedFestivalLineup
                chip.setOnCloseIconClickListener {
                    selectedFestivalLineup = null
                    redrawAllChips(genres, years, labels, lineups, festivals, artists)
                }
                group.addView(chip)
            } else if (lineups != null) {
                val addChip =
                    layoutInflater.inflate(R.layout.tsshadow_chip_add, group, false) as Chip
                addChip.text =
                    getString(R.string.tsshadow_add_format, getString(R.string.festival_lineup))
                addChip.setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(
                            getString(
                                R.string.tsshadow_select_format,
                                getString(R.string.festival_lineup)
                            )
                        )
                        .setItems(lineups.toTypedArray()) { _, which ->
                            selectedFestivalLineup = lineups[which]
                            redrawAllChips(genres, years, labels, lineups, festivals, artists)
                        }
                        .show()
                }
                group.addView(addChip)
            }
        }

        if (modalType == FilterModalType.LIVESET) {
            addSelectedChips(getString(R.string.festival), selectedFestivals, festivals)
        }
    }

    private fun applyInitialFilters() {
        val filters =
            BundleCompat.getParcelable(
                requireArguments(),
                ARG_INITIAL_FILTERS,
                FilterState::class.java
            )
                ?: return
        binding.selectTitle.setText(filters.title)
        selectedGenres.addAll(filters.genres)
        selectedArtists.addAll(filters.artists)
        selectedYears.addAll(filters.years)
        selectedLabels.addAll(filters.label)
        selectedFestivalLineup = filters.festivalLineup
        selectedFestivals.addAll(filters.festival)
        selectedResultCount = filters.count
        binding.selectResultCountSlider.value =
            resultCountSteps.indexOf(filters.count).toFloat()
        binding.selectResultCountLabel.text =
            getString(R.string.result_count_format, selectedResultCount)
        binding.selectRatingMin.setSelection(filters.ratingMin)
        binding.selectRatingMax.setSelection(filters.ratingMax)
        binding.favoriteButton.isSelected = filters.favorite
        val iconRes = if (filters.favorite) R.drawable.ic_star_full else R.drawable.ic_star_hollow
        binding.favoriteButton.setImageResource(iconRes)
        sortOptions.indexOfFirst { it.second == filters.sortMethod }
            .takeIf { it >= 0 }
            ?.let { binding.selectSortMethod.setSelection(it) }

        val isEdit = arguments?.getBoolean(ARG_EDIT_MODE)!!
        binding.save.visibility = if (isEdit) GONE else VISIBLE
        binding.search.visibility = if (isEdit) GONE else VISIBLE
        binding.update.visibility = if (isEdit) VISIBLE else GONE
        binding.delete.visibility = if (isEdit) VISIBLE else GONE
    }

    private fun collectFilterState(): FilterState = FilterState(
        title = binding.selectTitle.text.toString().trim(),
        genres = selectedGenres,
        artists = selectedArtists,
        years = selectedYears,
        count = selectedResultCount,
        ratingMin = binding.selectRatingMin.selectedItem as Int,
        ratingMax = binding.selectRatingMax.selectedItem as Int,
        sortMethod = sortOptions[binding.selectSortMethod.selectedItemPosition].second,
        label = selectedLabels,
        festival = selectedFestivals,
        festivalLineup = selectedFestivalLineup,
        favorite = binding.favoriteButton.isSelected
    )
}
