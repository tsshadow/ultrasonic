package org.moire.ultrasonic.domain

import java.io.Serializable

data class ChatMessage(val username: String, val time: Long, val message: String) : Serializable
