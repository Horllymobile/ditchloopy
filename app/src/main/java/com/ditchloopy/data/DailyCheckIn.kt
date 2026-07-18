package com.ditchloopy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_checkins")
data class DailyCheckIn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val mood: String, // Calm, Grateful, Inspired, Curious, Nostalgic, Restless, Tired
    val category: String, // Nature, Connection, Quietness, Small Win, Wonder, Other
    val presenceRating: Int, // 1 to 5
    val timestamp: Long = System.currentTimeMillis()
)
