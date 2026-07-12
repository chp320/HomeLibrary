package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.home.library.data.local.entity.LoanHistoryEntity

/**
 * append-only DAO (설계 원칙 6).
 * insert/select만 정의한다. update/delete 메서드를 만들지 않는다.
 */
@Dao
interface LoanHistoryDao {

    @Insert
    suspend fun insert(history: LoanHistoryEntity): Long

    @Query("SELECT * FROM loan_history WHERE loan_id = :loanId ORDER BY action_at ASC")
    suspend fun getByLoan(loanId: Long): List<LoanHistoryEntity>

    @Query("SELECT * FROM loan_history ORDER BY action_at DESC")
    suspend fun getAll(): List<LoanHistoryEntity>
}
