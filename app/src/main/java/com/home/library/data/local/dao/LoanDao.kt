package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.home.library.data.local.entity.LoanEntity
import com.home.library.data.local.view.ActiveLoanView
import com.home.library.data.local.view.AdminLoanView
import com.home.library.data.local.view.LoanHistoryRecord
import kotlinx.coroutines.flow.Flow

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

    /** 활성 대출(대출중/연체) 권수. 최대권수 검증용. */
    @Query("SELECT COUNT(*) FROM loans WHERE user_id = :userId AND status IN ('LOANED', 'OVERDUE')")
    suspend fun countActiveByUser(userId: Long): Int

    /** 동일 사용자·동일 도서의 활성 대출 수. 동시 중복 대출 차단용(요건 7.2). */
    @Query(
        """
        SELECT COUNT(*) FROM loans
        WHERE user_id = :userId AND book_id = :bookId AND status IN ('LOANED', 'OVERDUE')
        """,
    )
    suspend fun countActiveByUserAndBook(userId: Long, bookId: Long): Int

    /** 연체 보유 수. 연체 시 신규대출 차단용. */
    @Query("SELECT COUNT(*) FROM loans WHERE user_id = :userId AND status = 'OVERDUE'")
    suspend fun countOverdueByUser(userId: Long): Int

    /** 앱 시작 시 1회. 기한 지난 대출중 건을 OVERDUE로 전환(멱등). */
    @Query("UPDATE loans SET status = 'OVERDUE' WHERE status = 'LOANED' AND due_date < :now")
    suspend fun markOverdue(now: Long): Int

    /**
     * 사용자의 활성 대출 목록(반납 화면 / 6단계 내 대출현황 공용).
     * books와 JOIN해 도서 제목을 함께 반환. Flow라 반납 시 자동 갱신.
     */
    @Query(
        """
        SELECT l.loan_id AS loanId, l.book_id AS bookId, b.title AS bookTitle,
               l.due_date AS dueDate, l.status AS status
        FROM loans l JOIN books b ON l.book_id = b.book_id
        WHERE l.user_id = :userId AND l.status IN ('LOANED', 'OVERDUE')
        ORDER BY l.due_date ASC
        """,
    )
    fun getActiveLoansByUser(userId: Long): Flow<List<ActiveLoanView>>

    /**
     * 내 대출 이력(반납 완료 포함). 도서명 부분일치 + 대출일 기간 필터 + 페이징(LIMIT/OFFSET). HIST-002.
     * fromDate/toDate는 열린 경계를 0 / Long.MAX_VALUE로 넘긴다.
     */
    @Query(
        """
        SELECT l.loan_id AS loanId, b.title AS bookTitle, l.loan_date AS loanDate,
               l.due_date AS dueDate, l.return_date AS returnDate, l.status AS status
        FROM loans l JOIN books b ON l.book_id = b.book_id
        WHERE l.user_id = :userId
          AND (:bookQuery = '' OR b.title LIKE '%' || :bookQuery || '%')
          AND l.loan_date >= :fromDate AND l.loan_date <= :toDate
        ORDER BY l.loan_date DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getUserLoanHistory(
        userId: Long,
        bookQuery: String,
        fromDate: Long,
        toDate: Long,
        limit: Int,
        offset: Int,
    ): List<LoanHistoryRecord>

    /** 관리자 전체 활성 대출 현황(도서·대출자명). SCR-13. */
    @Query(
        """
        SELECT l.loan_id AS loanId, b.title AS bookTitle, u.name AS borrowerName,
               l.due_date AS dueDate, l.status AS status
        FROM loans l
          JOIN books b ON l.book_id = b.book_id
          JOIN users u ON l.user_id = u.user_id
        WHERE l.status IN ('LOANED', 'OVERDUE')
        ORDER BY l.due_date ASC
        """,
    )
    fun getAllActiveLoans(): Flow<List<AdminLoanView>>
}
