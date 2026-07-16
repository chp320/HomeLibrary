package com.home.library.loan

import com.home.library.data.local.dao.LoanDao
import com.home.library.data.local.entity.BookEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 대출 4대 검증.
 * 1) 동일 사용자·동일 도서 중복 대출 차단 (요건 7.2)
 * 2) 연체 보유 시 신규 대출 차단
 * 3) 최대 권수 초과 차단
 * 4) 가용 수량 0 차단
 * 트랜잭션 내부에서 호출된다(읽기 일관성).
 */
@Singleton
class LoanValidator @Inject constructor(
    private val loanDao: LoanDao,
    private val policy: LoanPolicy,
) {
    suspend fun validate(userId: Long, book: BookEntity): LoanCheck = when {
        loanDao.countActiveByUserAndBook(userId, book.bookId) > 0 -> LoanCheck.AlreadyBorrowed
        loanDao.countOverdueByUser(userId) > 0 -> LoanCheck.HasOverdue
        loanDao.countActiveByUser(userId) >= policy.maxCount() -> LoanCheck.MaxCountExceeded(policy.maxCount())
        book.availableQty <= 0 -> LoanCheck.NotAvailable
        else -> LoanCheck.Ok
    }
}

sealed interface LoanCheck {
    data object Ok : LoanCheck
    data object AlreadyBorrowed : LoanCheck
    data object HasOverdue : LoanCheck
    data class MaxCountExceeded(val max: Int) : LoanCheck
    data object NotAvailable : LoanCheck
}
