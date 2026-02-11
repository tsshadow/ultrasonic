package org.moire.ultrasonic.domain

import java.io.Serializable

data class ChatMessage(val username: String, val time: Long, val message: String) : Serializable {
    companion object {
        @Suppress("unused")
        private const val serialVersionUID = 1L
    }
}
