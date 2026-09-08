package com.ordia.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val status: String = "PENDING",
    val priority: String = "MEDIUM",
    val dueDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
