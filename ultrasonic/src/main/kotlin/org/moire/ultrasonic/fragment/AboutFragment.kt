/*
 * AboutFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.UpdateChecker
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.getVersionName
import org.moire.ultrasonic.util.Util.showChangelog

/**
 * Displays the About page
 */
class AboutFragment : Fragment() {
    private var titleText: TextView? = null
    private var webPageButton: Button? = null
    private var reportBugButton: Button? = null
    private var updateButton: Button? = null
    private var changelogButton: Button? = null
    private var viewLogButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.help, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        titleText = view.findViewById(R.id.help_title)
        webPageButton = view.findViewById(R.id.help_webpage)
        reportBugButton = view.findViewById(R.id.help_report)
        updateButton = view.findViewById(R.id.help_update)
        changelogButton = view.findViewById(R.id.help_changelog)
        viewLogButton = view.findViewById(R.id.help_view_log)

        val versionName = getVersionName(requireContext())
        val title = String.format(
            Locale.getDefault(),
            "%s (%s)",
            getString(R.string.common_appname),
            versionName
        )

        FragmentTitle.setTitle(this@AboutFragment, getString(R.string.menu_about))
        titleText?.text = title

        if (BuildConfig.DEBUG) {
            val helpText: TextView = view.findViewById(R.id.help_text)
            val debugInfo = "\n\n--- DEBUG INFO ---\n" +
                "Build Type: ${BuildConfig.BUILD_TYPE}\n" +
                "Version Code: ${BuildConfig.VERSION_CODE}\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            helpText.append(debugInfo)
            titleText?.setTextColor(Color.YELLOW)
        }

        webPageButton?.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_webpage_url)))
            )
        }

        reportBugButton?.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_report_url)))
            )
        }

        var lastClickTime: Long = 0
        updateButton?.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) {
                UpdateChecker.openUpdateSite(requireContext())
            } else {
                lifecycleScope.launch {
                    UpdateChecker.checkForUpdates(requireContext(), manual = true)
                }
            }
            lastClickTime = currentTime
        }

        changelogButton?.setOnClickListener {
            showChangelog(requireContext())
        }

        viewLogButton?.setOnClickListener {
            findNavController().navigate(R.id.logViewerFragment)
        }
    }
}
