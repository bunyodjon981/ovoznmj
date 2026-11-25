package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userCommand: String,
    val aiResponse: String,
    val executedActions: String,
    val modelUsed: String,
    val isSuccess: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)
