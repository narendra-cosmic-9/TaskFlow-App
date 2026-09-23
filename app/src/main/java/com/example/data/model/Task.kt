package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String, // Study, Work, Personal, Health, Shopping, Other
    val priority: String, // Low, Medium, High
    val dueDate: String, // YYYY-MM-DD
    val dueTime: String, // HH:MM
    val isCompleted: Boolean = false,
    val completionDate: String? = null, // YYYY-MM-DD
    val createdAt: Long = System.currentTimeMillis()
)
