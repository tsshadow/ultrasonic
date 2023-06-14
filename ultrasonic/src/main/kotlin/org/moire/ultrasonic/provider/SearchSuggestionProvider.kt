/*
 * SearchSuggestionProvider.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.provider

import android.content.SearchRecentSuggestionsProvider
import org.moire.ultrasonic.BuildConfig

/**
 * Provides search suggestions based on recent searches.
 */
class SearchSuggestionProvider : SearchRecentSuggestionsProvider() {
    init {
        setupSuggestions(AUTHORITY, MODE)
    }

    companion object {
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.SearchSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }
}
