package com.ordia.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val duration: Int? = null, // In minutes
    val priority: Int = 0, // e.g. 0 = normal, 1 = high
    val energy: Int? = null, // e.g. 1 = low, 2 = medium, 3 = high
    val deadline: Long? = null,
    val blockedBy: Long? = null, // ID of another task this depends on
    val createdAt: Long,
    val updatedAt: Long
)
