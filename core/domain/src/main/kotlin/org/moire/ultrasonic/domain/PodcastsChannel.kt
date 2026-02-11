package org.moire.ultrasonic.domain

import java.io.Serializable

data class PodcastsChannel(
    override val id: String,
    val title: String?,
    val url: String?,
    val description: String?,
    val status: String?
) : GenericEntry(),
    Serializable {
    override fun toString(): String = title.toString()

    companion object {
        @Suppress("unused")
        private const val serialVersionUID = 1L
    }
}
