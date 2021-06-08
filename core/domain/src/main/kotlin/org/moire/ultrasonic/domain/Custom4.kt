package org.moire.ultrasonic.domain

import java.io.Serializable

data class Custom4(
    val name: String,
    val index: String
) : Serializable {
    companion object {
        private const val serialVersionUID = -3943025175219134028L
    }
}
