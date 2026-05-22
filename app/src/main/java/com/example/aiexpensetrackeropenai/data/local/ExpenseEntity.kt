package com.example.aiexpensetrackeropenai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val activity: String,
    val amount: Int,
    val type: String, // "income" or "expense"
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
