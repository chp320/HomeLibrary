package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.home.library.data.local.entity.LoanHistoryEntity
import com.home.library.data.local.view.BookLoanHistoryView
import kotlinx.coroutines.flow.Flow

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

    /** 특정 도서의 대출 이력(행위·시각·행위자명). BOOK-008. Flow라 신규 대출/반납 시 자동 갱신. */
    @Query(
        """
        SELECT h.action, h.action_at AS actionAt, u.name AS actorName, h.memo
        FROM loan_history h
          JOIN loans l ON h.loan_id = l.loan_id
          JOIN users u ON h.actor_id = u.user_id
        WHERE l.book_id = :bookId
        ORDER BY h.action_at DESC
        """,
    )
    fun getByBook(bookId: Long): Flow<List<BookLoanHistoryView>>
}
