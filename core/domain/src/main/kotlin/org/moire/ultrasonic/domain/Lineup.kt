package org.moire.ultrasonic.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity
data class Lineup(@PrimaryKey val index: String, val name: String) : Serializable
