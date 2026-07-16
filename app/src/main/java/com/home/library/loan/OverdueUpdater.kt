package com.home.library.loan

import com.home.library.data.local.dao.LoanDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기한 지난 대출을 OVERDUE로 전환. 멱등이라 여러 번 호출해도 안전.
 * MainActivity.onStart에서 호출한다(백그라운드 오래 뒀다 복귀 시에도 갱신되도록 onCreate 아님).
 */
@Singleton
class OverdueUpdater @Inject constructor(
    private val loanDao: LoanDao,
) {
    suspend fun run(): Int = loanDao.markOverdue(System.currentTimeMillis())
}
