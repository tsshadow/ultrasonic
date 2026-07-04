package org.moire.ultrasonic.fragment.tsshadow

import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
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

    private var songState = FilterState(modalType = FilterModalType.SONG)
    private var livesetState = FilterState(modalType = FilterModalType.LIVESET)

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

        val initialType = BundleCompat.getSerializable(
            requireArguments(),
            ARG_TYPE,
            FilterModalType::class.java
        )!!
        val initialFilters = BundleCompat.getParcelable(
            requireArguments(),
            ARG_INITIAL_FILTERS,
            FilterState::class.java
        )

        if (initialFilters != null) {
            if (initialFilters.modalType == FilterModalType.SONG) {
                songState = initialFilters
            } else {
                livesetState = initialFilters
            }
        }
        modalType = initialFilters?.modalType ?: initialType
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
        applyStateToUI(if (modalType == FilterModalType.SONG) songState else livesetState)
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

        binding.durationSlider.addOnChangeListener { _, value, _ ->
            val minutes = value.toInt() / 60
            binding.durationValueText.text = "$minutes min"
        }

        binding.chipSongs.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && modalType != FilterModalType.SONG) {
                switchType(FilterModalType.SONG)
            }
        }
        binding.chipSets.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && modalType != FilterModalType.LIVESET) {
                switchType(FilterModalType.LIVESET)
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

    private fun updateDurationLabel() {
        if (modalType == FilterModalType.LIVESET) {
            binding.durationLabel.text = "Min Duration"
            binding.chipSets.isChecked = true
        } else {
            binding.durationLabel.text = "Max Duration"
            binding.chipSongs.isChecked = true
        }
        updateBrandColors()
    }

    private fun updateBrandColors() {
        val brandColor = if (modalType == FilterModalType.LIVESET) {
            requireContext().getColor(R.color.spotify_blue)
        } else {
            requireContext().getColor(R.color.spotify_green)
        }

        binding.durationSlider.thumbTintList = ColorStateList.valueOf(brandColor)
        binding.durationSlider.trackActiveTintList = ColorStateList.valueOf(brandColor)
        binding.selectResultCountSlider.thumbTintList = ColorStateList.valueOf(brandColor)
        binding.selectResultCountSlider.trackActiveTintList = ColorStateList.valueOf(brandColor)

        binding.favoriteButton.imageTintList = ColorStateList.valueOf(brandColor)

        val selectedColor = ColorStateList.valueOf(brandColor)
        val unselectedColor = ColorStateList.valueOf(requireContext().getColor(R.color.spotify_card))

        binding.chipSongs.chipBackgroundColor = if (modalType == FilterModalType.SONG) selectedColor else unselectedColor
        binding.chipSets.chipBackgroundColor = if (modalType == FilterModalType.LIVESET) selectedColor else unselectedColor

        binding.save.backgroundTintList = ColorStateList.valueOf(brandColor)
        binding.search.backgroundTintList = ColorStateList.valueOf(brandColor)
        binding.update.backgroundTintList = ColorStateList.valueOf(brandColor)
        binding.delete.backgroundTintList = ColorStateList.valueOf(brandColor)
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
            val brandColor = if (modalType == FilterModalType.LIVESET) {
                requireContext().getColor(R.color.spotify_blue)
            } else {
                requireContext().getColor(R.color.spotify_green)
            }

            values.forEach { value ->
                val chip = layoutInflater.inflate(R.layout.tsshadow_chip, group, false) as Chip
                chip.text = value
                chip.chipBackgroundColor = ColorStateList.valueOf(brandColor)
                chip.setTextColor(Color.WHITE)
                chip.closeIconTint = ColorStateList.valueOf(Color.WHITE)
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
                addChip.chipIconTint = ColorStateList.valueOf(brandColor)
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

    private fun switchType(newType: FilterModalType) {
        if (modalType == FilterModalType.SONG) {
            songState = collectFilterState()
        } else {
            livesetState = collectFilterState()
        }

        modalType = newType
        applyStateToUI(if (modalType == FilterModalType.SONG) songState else livesetState)

        updateDurationLabel()
        redrawAllChips(
            filterOptionsViewModel.genres.value,
            filterOptionsViewModel.years.value,
            filterOptionsViewModel.labels.value,
            filterOptionsViewModel.lineups.value,
            filterOptionsViewModel.festivals.value,
            filterOptionsViewModel.artists.value
        )
    }

    private fun applyStateToUI(state: FilterState) {
        binding.selectTitle.setText(state.title)
        selectedGenres.clear()
        selectedGenres.addAll(state.genres)
        selectedArtists.clear()
        selectedArtists.addAll(state.artists)
        selectedYears.clear()
        selectedYears.addAll(state.years)
        selectedLabels.clear()
        selectedLabels.addAll(state.label)
        selectedFestivalLineup = state.festivalLineup
        selectedFestivals.clear()
        selectedFestivals.addAll(state.festival)

        selectedResultCount = state.count
        binding.selectResultCountSlider.value =
            resultCountSteps.indexOf(state.count).toFloat()
        binding.selectResultCountLabel.text =
            getString(R.string.result_count_format, selectedResultCount)
        binding.selectRatingMin.setSelection(state.ratingMin)
        binding.selectRatingMax.setSelection(state.ratingMax)
        binding.favoriteButton.isSelected = state.favorite
        val iconRes = if (state.favorite) R.drawable.ic_star_full else R.drawable.ic_star_hollow
        binding.favoriteButton.setImageResource(iconRes)
        sortOptions.indexOfFirst { it.second == state.sortMethod }
            .takeIf { it >= 0 }
            ?.let { binding.selectSortMethod.setSelection(it) }

        modalType = state.modalType
        updateDurationLabel()
        val durationValue =
            (if (modalType == FilterModalType.LIVESET) state.minDuration else state.maxDuration)
                ?: 0
        binding.durationSlider.value = durationValue.toFloat()
        binding.durationValueText.text = "${durationValue / 60} min"

        val isEdit = arguments?.getBoolean(ARG_EDIT_MODE)!!
        binding.save.visibility = if (isEdit) GONE else VISIBLE
        binding.search.visibility = if (isEdit) GONE else VISIBLE
        binding.update.visibility = if (isEdit) VISIBLE else GONE
        binding.delete.visibility = if (isEdit) VISIBLE else GONE
    }

    private fun collectFilterState(): FilterState {
        val durationValue = binding.durationSlider.value.toInt()
        return FilterState(
            title = binding.selectTitle.text.toString().trim(),
            genres = selectedGenres,
            artists = selectedArtists,
            years = selectedYears,
            count = selectedResultCount,
            ratingMin = binding.selectRatingMin.selectedItem as Int,
            ratingMax = binding.selectRatingMax.selectedItem as Int,
            sortMethod = sortOptions[binding.selectSortMethod.selectedItemPosition].second,
            minDuration = if (modalType == FilterModalType.LIVESET) durationValue else null,
            maxDuration = if (modalType == FilterModalType.SONG) durationValue else null,
            modalType = modalType,
            label = selectedLabels,
            festival = selectedFestivals,
            festivalLineup = selectedFestivalLineup,
            favorite = binding.favoriteButton.isSelected
        )
    }
}
