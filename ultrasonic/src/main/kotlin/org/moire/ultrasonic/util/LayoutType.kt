/*
 * ViewType.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

enum class LayoutType(val value: Int) {
    LIST(0),
    COVER(1);

    companion object {
        private val map = values().associateBy { it.value }
        fun from(value: Int): LayoutType {
            // Default to list if unmappable
            return map[value] ?: LIST
        }
    }
}
