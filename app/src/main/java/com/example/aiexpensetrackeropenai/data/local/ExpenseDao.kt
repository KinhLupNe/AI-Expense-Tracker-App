package com.example.aiexpensetrackeropenai.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert
    fun insertExpense(expense: ExpenseEntity): Long

    @androidx.room.Update
    fun updateExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    fun getUnsyncedExpenses(): List<ExpenseEntity>

    @Delete
    fun deleteExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET category = :newCategory WHERE category = :oldCategory")
    fun updateCategoryName(oldCategory: String, newCategory: String)
}
