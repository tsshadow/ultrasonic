package org.moire.ultrasonic.domain

import java.io.Serializable

data class Playlist @JvmOverloads constructor(
    override val id: String,
    override var name: String,
    val owner: String = "",
    val comment: String = "",
    val songCount: String = "",
    val created: String = "",
    val public: Boolean? = null
) : GenericEntry(),
    Serializable {
    override fun toString(): String = name
}
