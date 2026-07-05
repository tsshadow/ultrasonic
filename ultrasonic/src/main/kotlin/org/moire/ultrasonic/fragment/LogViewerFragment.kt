/*
 * LogViewerFragment.kt
 * Copyright (C) 2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.toast
import java.io.File

/**
 * Fragment to view the application logs.
 */
class LogViewerFragment : Fragment() {

    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(requireContext())
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logTextView = view.findViewById(R.id.log_text)
        FragmentTitle.setTitle(this, getString(R.string.settings_debug_view_log))
        loadLogs()
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val logContent = withContext(Dispatchers.IO) {
                val logFiles = FileLoggerTree.getLogFileList()
                if (logFiles.isNullOrEmpty()) {
                    null
                } else {
                    logFiles.sortByDescending { it.lastModified() }
                    val latestLog = logFiles[0]
                    try {
                        latestLog.readText()
                    } catch (e: Exception) {
                        "Error reading log file: ${e.message}"
                    }
                }
            }
            logTextView.text = logContent ?: getString(R.string.settings_debug_log_deleted)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.log_viewer_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_share_log -> {
                shareLog()
                true
            }
            R.id.menu_refresh_log -> {
                loadLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareLog() {
        val logFiles = FileLoggerTree.getLogFileList()
        if (logFiles.isNullOrEmpty()) {
            toast(R.string.settings_debug_log_deleted)
            return
        }
        logFiles.sortByDescending { it.lastModified() }
        val latestLog = logFiles[0]

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            latestLog
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.settings_debug_share_log)))
    }
}
