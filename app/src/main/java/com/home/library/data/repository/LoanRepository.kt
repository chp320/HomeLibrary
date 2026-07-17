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
import com.home.library.data.local.view.AdminLoanView
import com.home.library.data.local.view.BookLoanHistoryView
import com.home.library.data.local.view.LoanHistoryRecord
import com.home.library.loan.LoanAllowance
import com.home.library.loan.LoanCheck
import com.home.library.loan.LoanPolicy
import com.home.library.loan.LoanValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /** 사용자의 활성 대출 목록(반납 화면 / 내 대출 현황 공용). */
    fun activeLoans(userId: Long): Flow<List<ActiveLoanView>> = loanDao.getActiveLoansByUser(userId)

    /**
     * 대출 여력(표시용). 도서 목록·대출 확인 화면 공용.
     *
     * activeLoans Flow에서 파생하므로 대출/반납 즉시 자동 갱신된다.
     * overdueCount를 `status == OVERDUE`로 세는 것은 [LoanDao.countOverdueByUser]와 동일한 규칙이라
     * [LoanValidator]의 판정과 일치한다. (due_date 경과 여부로 세면 검증기와 어긋나므로 쓰지 않는다.)
     */
    fun allowance(userId: Long): Flow<LoanAllowance> =
        loanDao.getActiveLoansByUser(userId).map { loans ->
            LoanAllowance(
                activeCount = loans.size,
                overdueCount = loans.count { it.status == LoanStatus.OVERDUE },
                maxCount = policy.maxCount(),
            )
        }

    /** 대출 기간(일). 대출 확인 화면의 반납 예정일 미리보기용. AppConfig 값이라 정책 변경이 반영된다. */
    suspend fun loanPeriodDays(): Long = policy.periodDays()

    /** 내 대출 이력(도서명·기간 필터 + 페이징). HIST-002. */
    suspend fun userLoanHistory(
        userId: Long,
        bookQuery: String,
        fromDate: Long,
        toDate: Long,
        limit: Int,
        offset: Int,
    ): List<LoanHistoryRecord> =
        loanDao.getUserLoanHistory(userId, bookQuery.trim(), fromDate, toDate, limit, offset)

    /** 관리자 전체 활성 대출 현황. SCR-13. */
    fun allActiveLoans(): Flow<List<AdminLoanView>> = loanDao.getAllActiveLoans()

    /** 도서별 대출 이력(append-only LOAN_HISTORY). BOOK-008. */
    fun bookLoanHistory(bookId: Long): Flow<List<BookLoanHistoryView>> =
        loanHistoryDao.getByBook(bookId)

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

    /**
     * 일괄 반납(A-11).
     *
     * ⚠️ **각 건을 개별 트랜잭션으로 처리한다.** [returnBook]이 건당 `withTransaction`이므로
     * 여기서 루프만 돌면 요건이 충족된다. 이 루프를 `db.withTransaction`으로 감싸면
     * 1건 실패에 **전부 롤백**되어 요건이 깨진다 — 절대 감싸지 말 것.
     *
     * 예외도 건별로 격리한다. 한 건이 터져도 나머지는 계속 진행하고,
     * 이미 커밋된 앞선 성공 건은 그대로 살아남는다.
     *
     * @return 입력 순서대로의 (loanId, 결과). 호출자가 성공/실패를 집계한다.
     */
    suspend fun returnBooks(loanIds: List<Long>, actorId: Long): List<ReturnOutcome> =
        loanIds.map { loanId ->
            val result = try {
                returnBook(loanId, actorId)
            } catch (e: CancellationException) {
                throw e // 코루틴 취소는 삼키지 않는다
            } catch (e: Exception) {
                Log.w(TAG, "returnBooks: loanId=$loanId 반납 실패 — 나머지 건은 계속 진행", e)
                ReturnResult.Failed
            }
            ReturnOutcome(loanId, result)
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

    /** 예기치 못한 실패(예외). 일괄 반납에서 건별 격리 결과로만 쓰인다. */
    data object Failed : ReturnResult
}

/** 일괄 반납의 건별 결과. */
data class ReturnOutcome(val loanId: Long, val result: ReturnResult)
