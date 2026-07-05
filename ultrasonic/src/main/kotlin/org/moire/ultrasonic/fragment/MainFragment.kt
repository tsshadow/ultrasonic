/*
 * MainFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.lang.ref.SoftReference
import kotlin.collections.HashMap
import kotlin.collections.hashMapOf
import kotlin.collections.set
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinScopeComponent
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.fragment.tsshadow.SelectFragment
import org.moire.ultrasonic.fragment.tsshadow.SelectSongFragment
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.EMPTY_CAPABILITIES
import org.moire.ultrasonic.view.FilterButtonBar
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities
import timber.log.Timber

class MainFragment :
    ScopeFragment(),
    KoinScopeComponent {

    private var filterButtonBar: FilterButtonBar? = null
    private var layoutType: LayoutType = LayoutType.COVER
    private var binding: View? = null

    private lateinit var musicCollectionAdapter: MusicCollectionAdapter
    private lateinit var viewPager: ViewPager2

    private var rxBusSubscription = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.i("onCreate")
        binding = inflater.inflate(R.layout.primary, container, false)
        return binding!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentTitle.setTitle(this, R.string.music_library_label)

        // Load last layout from settings
        layoutType = LayoutType.from(Settings.lastViewType)

        // Init ViewPager2
        musicCollectionAdapter = MusicCollectionAdapter(this, layoutType)
        viewPager = binding!!.findViewById(R.id.pager)
        viewPager.adapter = musicCollectionAdapter

        filterButtonBar = binding!!.findViewById(R.id.filter_button_bar)
        musicCollectionAdapter.filterButtonBar = filterButtonBar

        filterButtonBar!!.setOnLayoutTypeChangedListener {
            updateLayoutTypeOnCurrentFragment(it)
        }

        filterButtonBar!!.setOnOrderChangedListener {
            updateSortOrderOnCurrentFragment(it)
        }

        rxBusSubscription += RxBus.setsModeChangedObservable.subscribe {
            updateSetsToggleOnCurrentFragment(it)
        }

        // Set layout toggle Chip to correct state
        filterButtonBar!!.setLayoutType(layoutType)

        updateBrandColors()

        // Listen to changes in the current page (=fragment)
        val chipGroup: com.google.android.material.chip.ChipGroup = binding!!.findViewById(R.id.main_type_toggle_group)
        val chipIds = listOf(R.id.chip_songs, R.id.chip_albums, R.id.chip_artists)

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val position = chipIds.indexOf(checkedId)
            if (position != -1 && viewPager.currentItem != position) {
                viewPager.setCurrentItem(position, true)
            }
        }

        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                Timber.i("On Page changed $position")

                if (position < chipIds.size) {
                    chipGroup.check(chipIds[position])
                }

                // This is a bit tricky. We need to configure the FilterButtonBar based on the
                // fragments capabilities. But this function can be called before the fragment has
                // been created, and the ViewPager might create the fragments in arbitrary order.
                // Therefore we store a flag in the Adapter, to signal that the next created
                // fragment of the given position should propagate its capabilities
                val frag = findFragmentAtPosition(childFragmentManager, position)
                if (frag != null) {
                    filterButtonBar!!.configureWithCapabilitiesFromFragment(frag)
                } else {
                    musicCollectionAdapter.propagateCapabilitiesMatcher = position
                }
                requireActivity().invalidateMenu()
            }
        })

    }

    override fun onDestroyView() {
        super.onDestroyView()
        rxBusSubscription.clear()
        binding = null
    }

    private fun updateLayoutTypeOnCurrentFragment(it: LayoutType) {
        val curFrag = findCurrentFragment()

        if (curFrag is FilterableFragment) {
            curFrag.setLayoutType(it)
        }

        Settings.lastViewType = layoutType.value
    }

    private fun updateSortOrderOnCurrentFragment(it: SortOrder) {
        val curFrag = findCurrentFragment()

        if (curFrag is FilterableFragment) {
            curFrag.setOrderType(it)
        }
    }

    private fun updateBrandColors() {
        val curFrag = findCurrentFragment()
        val isSets = if (curFrag is FilterableFragment) curFrag.isSetsMode else Settings.isSetsMode
        val brandColor = if (isSets) {
            requireContext().getColor(R.color.spotify_blue)
        } else {
            requireContext().getColor(R.color.spotify_green)
        }

        val chipGroup: com.google.android.material.chip.ChipGroup = binding!!.findViewById(R.id.main_type_toggle_group)
        val chipIds = listOf(R.id.chip_songs, R.id.chip_albums, R.id.chip_artists)

        for (id in chipIds) {
            val chip = chipGroup.findViewById<com.google.android.material.chip.Chip>(id)
            chip.chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(brandColor, requireContext().getColor(R.color.spotify_card))
            )
        }

        filterButtonBar?.updateBrandColors(isSets)
    }

    private fun updateSetsToggleOnCurrentFragment(it: Boolean) {
        val curFrag = findCurrentFragment()
        if (curFrag is FilterableFragment) {
            curFrag.setOnSetsToggle(it)
        }
        if (curFrag is MultiListFragment<*>) {
            curFrag.updateBrandColors()
        } else if (curFrag is SelectFragment) {
            curFrag.updateBrandColors()
        }
        updateBrandColors()
        requireActivity().invalidateMenu()
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun findCurrentFragment(): Fragment? = findFragmentAtPosition(childFragmentManager, viewPager.currentItem)

    private fun findFragmentAtPosition(fragmentManager: FragmentManager, position: Int): Fragment? {
        // If a fragment was recently created and never shown the fragment manager might not
        // hold a reference to it. Fallback on the WeakMap instead.
        return fragmentManager.findFragmentByTag("f$position")
            ?: musicCollectionAdapter.fragmentMap[position]?.get()
    }
}

private val EMPTY_CAPABILITIES = ViewCapabilities(false, false, emptyList())

private fun FilterButtonBar.configureWithCapabilitiesFromFragment(frag: Fragment?) {
    if (frag is FilterableFragment) {
        Timber.w("Setting kapas: ${frag.viewCapabilities}")
        this.configureWithCapabilities(frag.viewCapabilities, frag.isSetsMode)
    } else {
        Timber.w("Setting kapas: $EMPTY_CAPABILITIES")
        this.configureWithCapabilities(EMPTY_CAPABILITIES, Settings.isSetsMode)
    }
}

@Suppress("MagicNumber")
class MusicCollectionAdapter(fragment: Fragment, initialType: LayoutType = LayoutType.LIST) : FragmentStateAdapter(fragment) {

    var filterButtonBar: FilterButtonBar? = null
    private var layoutType: LayoutType = initialType

    var propagateCapabilitiesMatcher: Int? = null

    // viewPager.findFragmentAtPosition(childFragmentManager, position) is sometimes delayed..
    var fragmentMap: HashMap<Int, SoftReference<Fragment>> = hashMapOf()

    override fun getItemCount(): Int {
        // Hide Genre tab when offline
        // We merged Songs and Livesets, so we have one less tab
        return if (ActiveServerProvider.isOffline()) 2 else 3
    }

    override fun createFragment(position: Int): Fragment {
        Timber.i("Creating new fragment at position: $position")

        val action = when (position) {
            0 -> NavigationGraphDirections.toSongList()
            1 -> NavigationGraphDirections.toAlbumList(
                AlbumListType.NEWEST,
                size = Settings.maxAlbums
            )
            2 -> NavigationGraphDirections.toArtistList()
            else -> NavigationGraphDirections.toSongList()
        }

        val fragment = when (position) {
            0 -> SelectSongFragment()
            1 -> AlbumListFragment(layoutType)
            2 -> ArtistListFragment()
            else -> SelectSongFragment()
        }

        fragmentMap[position] = SoftReference(fragment)
        fragment.arguments = action.arguments

        // See comment in onPageSelected
        if (propagateCapabilitiesMatcher == position) {
            Timber.w("Setting capacities while creating, $position")
            propagateCapabilitiesMatcher = null
            filterButtonBar!!.configureWithCapabilitiesFromFragment(fragment)
        }

        return fragment
    }

    fun getTitleForFragment(pos: Int, context: Context): String = when (pos) {
        0 -> context.getString(R.string.main_songs_title)
        1 -> context.getString(R.string.main_albums_title)
        2 -> context.getString(R.string.main_artists_title)
        else -> "Unknown"
    }
}

