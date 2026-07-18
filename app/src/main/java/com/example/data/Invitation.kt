package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invitations")
data class Invitation(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String, // Discover, Experience, Reflect, Grow
    val difficulty: String, // Easy, Medium, Deep
    val duration: String, // e.g. "5m", "30m", "1h"
    val energyLevel: String, // Low, Medium, High
    val indoorOutdoor: String, // Indoor, Outdoor, Either
    val introvertScore: String, // Solo, Social, Either
    val cost: String, // Free, Low, Medium
    val isCompleted: Boolean = false,
    val isSelectedForWeek: Boolean = false,
    val dayOfWeek: Int = 0, // 1 = Monday, 2 = Tuesday, ... 7 = Sunday, 0 = Unassigned
    val completedAt: Long? = null,
    val isCustom: Boolean = false
)
