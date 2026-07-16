package com.home.library.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.home.library.data.local.AppDatabase
import com.home.library.data.local.dao.BookDao
import com.home.library.data.local.dao.LoanDao
import com.home.library.data.local.dao.LoanHistoryDao
import com.home.library.data.local.entity.LoanEntity
import com.home.library.data.local.entity.LoanHistoryEntity
import com.home.library.data.local.enums.BookStatus
import com.home.library.data.local.enums.LoanAction
import com.home.library.data.local.enums.LoanStatus
import com.home.library.data.local.view.ActiveLoanView
import com.home.library.loan.LoanCheck
import com.home.library.loan.LoanPolicy
import com.home.library.loan.LoanValidator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 대출/반납. 모두 @Transaction(원자적).
 * - loan(): 4대 검증 → decreaseAvailable(0이면 롤백) → LOANS/LOAN_HISTORY.
 * - returnBook(): increaseAvailable → LOANS(RETURNED)/LOAN_HISTORY. 수량증가 0(데이터 불일치)이면
 *   사용자를 가두지 않기 위해 로그만 남기고 반납은 진행한다.
 * 자가대출 모델: actor_id = 대출자(userId).
 */
@Singleton
class LoanRepository @Inject constructor(
    private val db: AppDatabase,
    private val bookDao: BookDao,
    private val loanDao: LoanDao,
    private val loanHistoryDao: LoanHistoryDao,
    private val policy: LoanPolicy,
    private val validator: LoanValidator,
) {

    /** 사용자의 활성 대출 목록(반납 화면 / 6단계 현황 공용). */
    fun activeLoans(userId: Long): Flow<List<ActiveLoanView>> = loanDao.getActiveLoansByUser(userId)

    suspend fun loan(userId: Long, bookId: Long): LoanResult = db.withTransaction {
        val book = bookDao.getById(bookId)
        if (book == null || book.status != BookStatus.AVAILABLE) {
            return@withTransaction LoanResult.BookUnavailable
        }
        when (val check = validator.validate(userId, book)) {
            LoanCheck.AlreadyBorrowed -> return@withTransaction LoanResult.AlreadyBorrowed
            LoanCheck.HasOverdue -> return@withTransaction LoanResult.HasOverdue
            is LoanCheck.MaxCountExceeded -> return@withTransaction LoanResult.MaxCountExceeded(check.max)
            LoanCheck.NotAvailable -> return@withTransaction LoanResult.NotAvailable
            LoanCheck.Ok -> Unit
        }

        val now = System.currentTimeMillis()
        // 조건부 SQL 방어: 반환 0이면 가용 없음 → 아무 것도 기록 안 하고 실패
        if (bookDao.decreaseAvailable(bookId, now) == 0) {
            return@withTransaction LoanResult.NotAvailable
        }
        val dueDate = now + policy.periodDays() * DAY_MS
        val loanId = loanDao.insert(
            LoanEntity(
                bookId = bookId,
                userId = userId,
                loanDate = now,
                dueDate = dueDate,
                status = LoanStatus.LOANED,
            ),
        )
        loanHistoryDao.insert(
            LoanHistoryEntity(loanId = loanId, action = LoanAction.LOAN, actionAt = now, actorId = userId),
        )
        LoanResult.Success(loanId, dueDate)
    }

    suspend fun returnBook(loanId: Long, actorId: Long): ReturnResult = db.withTransaction {
        val loan = loanDao.getById(loanId) ?: return@withTransaction ReturnResult.NotFound
        if (loan.status == LoanStatus.RETURNED) {
            return@withTransaction ReturnResult.AlreadyReturned
        }
        val now = System.currentTimeMillis()
        if (bookDao.increaseAvailable(loan.bookId, now) == 0) {
            // 데이터 불일치(available>=total). 사용자를 가두지 않기 위해 로그만 남기고 반납 진행.
            Log.w(TAG, "returnBook: increaseAvailable=0(available>=total) loanId=$loanId bookId=${loan.bookId} — 반납은 진행")
        }
        loanDao.update(loan.copy(status = LoanStatus.RETURNED, returnDate = now))
        loanHistoryDao.insert(
            LoanHistoryEntity(loanId = loanId, action = LoanAction.RETURN, actionAt = now, actorId = actorId),
        )
        ReturnResult.Success
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val TAG = "HomeLib"
    }
}

sealed interface LoanResult {
    data class Success(val loanId: Long, val dueDate: Long) : LoanResult
    data object BookUnavailable : LoanResult
    data object AlreadyBorrowed : LoanResult
    data object HasOverdue : LoanResult
    data class MaxCountExceeded(val max: Int) : LoanResult
    data object NotAvailable : LoanResult
}

sealed interface ReturnResult {
    data object Success : ReturnResult
    data object NotFound : ReturnResult
    data object AlreadyReturned : ReturnResult
}
