package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.home.library.data.local.entity.LoanEntity

@Dao
interface LoanDao {

    @Insert
    suspend fun insert(loan: LoanEntity): Long

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("SELECT * FROM loans WHERE loan_id = :loanId LIMIT 1")
    suspend fun getById(loanId: Long): LoanEntity?

    @Query("SELECT * FROM loans WHERE user_id = :userId ORDER BY loan_date DESC")
    suspend fun getByUser(userId: Long): List<LoanEntity>

    @Query("SELECT COUNT(*) FROM loans WHERE user_id = :userId AND status IN ('LOANED', 'OVERDUE')")
    suspend fun countActiveByUser(userId: Long): Int
}
