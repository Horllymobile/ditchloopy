package com.ditchloopy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reflections")
data class Reflection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invitationId: String,
    val text: String,
    val mood: String, // Calm, Grateful, Inspired, Curious, Nostalgic
    val timestamp: Long = System.currentTimeMillis(),
    val tagText: String? = null
)
