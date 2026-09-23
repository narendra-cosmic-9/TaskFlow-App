package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: Int = 1,
    val streak: Int = 0,
    val lastCompletedDate: String? = null, // YYYY-MM-DD
    val totalCompleted: Int = 0,
    val productivityScore: Int = 0 // scale of 0 - 100
)
