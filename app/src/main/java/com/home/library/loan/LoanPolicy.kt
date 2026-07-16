package com.home.library.loan

import com.home.library.data.local.ConfigKeys
import com.home.library.data.local.dao.AppConfigDao
import javax.inject.Inject
import javax.inject.Singleton

/** 대출 정책. AppConfig에서 조회(하드코딩 금지). DB 손상 대비 방어적 기본값 보유. */
@Singleton
class LoanPolicy @Inject constructor(
    private val appConfigDao: AppConfigDao,
) {
    suspend fun periodDays(): Long =
        appConfigDao.getValue(ConfigKeys.LOAN_PERIOD_DAYS)?.toLongOrNull() ?: DEFAULT_PERIOD_DAYS

    suspend fun maxCount(): Int =
        appConfigDao.getValue(ConfigKeys.LOAN_MAX_COUNT)?.toIntOrNull() ?: DEFAULT_MAX_COUNT

    companion object {
        private const val DEFAULT_PERIOD_DAYS = 14L
        private const val DEFAULT_MAX_COUNT = 5
    }
}
