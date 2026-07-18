package com.ditchloopy.data

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
) {
    val xp: Int
        get() {
            val base = 100
            val diffBonus = when (difficulty) {
                "Deep" -> 150
                "Medium" -> 80
                else -> 40
            }
            val customBonus = if (isCustom) 100 else 0
            val lengthBonus = (title.length + description.length) / 8
            return base + diffBonus + customBonus + lengthBonus
        }

    val noveltyScore: Int
        get() {
            val base = 75
            val categoryBonus = when (category) {
                "Discover" -> 15
                "Experience" -> 12
                "Reflect" -> 8
                "Grow" -> 20
                else -> 10
            }
            val customBonus = if (isCustom) 4 else 0
            val idHash = kotlin.math.abs(id.hashCode()) % 5
            return kotlin.math.min(99, base + categoryBonus + customBonus + idHash)
        }
}
