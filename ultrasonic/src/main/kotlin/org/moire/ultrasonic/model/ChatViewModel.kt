/*
 * ChatViewModel.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.moire.ultrasonic.domain.ChatMessage

class ChatViewModel : ViewModel() {

    // MutableLiveData to store chat messages
    private val _chatMessages = MutableLiveData<List<ChatMessage>>()

    // LiveData to observe chat messages
    val chatMessages: LiveData<List<ChatMessage>>
        get() = _chatMessages

    // Last chat message time
    var lastChatMessageTime: Long = 0

    // Function to update chat messages
    fun updateChatMessages(messages: List<ChatMessage>) {
        val updatedMessages = _chatMessages.value.orEmpty() + messages
        _chatMessages.postValue(updatedMessages)
    }
}
