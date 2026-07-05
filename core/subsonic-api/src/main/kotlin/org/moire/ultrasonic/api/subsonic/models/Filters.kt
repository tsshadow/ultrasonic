@file:Suppress("unused")

package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A flexible filter model supporting both single and multi-value entries,
 * for use in Ultrasonic's REST API requests.
 */
class Filter(@JsonProperty("name") val name: String, @JsonProperty("value") val value: Any) {
    override fun toString(): String {
        val valueStr = when (value) {
            is String -> "\"$value\""
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            else -> value.toString()
        }
        return "{\"name\":\"$name\",\"value\":$valueStr}"
    }
}

/**
 * Holds a collection of filters. Used to construct query parameters
 * for filtered song/liveset lookups.
 */
class Filters {

    constructor()

    constructor(filters: Array<Filter>) {
        this.filterItems = filters.toMutableList()
    }

    constructor(filter: Filter) {
        add(filter)
    }

    override fun toString(): String = if (filterItems.isNotEmpty()) {
        filterItems.joinToString(prefix = "[", postfix = "]") { it.toString() }
    } else {
        ""
    }

    fun add(filter: Filter) {
        filterItems.add(filter)
    }

    fun isEmpty(): Boolean = filterItems.isEmpty()

    private var filterItems: MutableList<Filter> = mutableListOf()

    fun getAll(): List<Filter> = filterItems.toList()

    fun sanitized(): Filters = Filters().apply {
        for (filter in this@Filters) {
            when (val value = filter.value) {
                is String -> if (value.isNotBlank()) add(filter)
                is Collection<*> -> if (value.isNotEmpty()) add(filter)
                else -> add(filter)
            }
        }
    }

    operator fun iterator(): Iterator<Filter> = filterItems.iterator()
}
