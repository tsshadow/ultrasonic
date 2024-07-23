package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

class filter(@JsonProperty("name") val name: String , @JsonProperty("value") val value: String) {
    override fun toString(): String {
        return "{\"name\":\"$name\",\"value\":\"$value\" }"
    }
}

class filters() {
    override fun toString(): String {
        var str = "["
        filters.forEach { str+= "$it," }
        str = str.substring(0, str.length - 1)
        str+= "]"
        return str
    }

    fun add(filter: filter) {
        filters += filter
    }

    private var filters: Array<filter> = emptyArray()
}