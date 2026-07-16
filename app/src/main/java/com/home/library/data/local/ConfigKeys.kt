package com.home.library.data.local

/** APP_CONFIG의 키 상수. 문자열 하드코딩 방지 (설계 원칙 10). */
object ConfigKeys {
    const val LOAN_PERIOD_DAYS = "loan.period.days"
    const val LOAN_MAX_COUNT = "loan.max.count"
    const val LOAN_EXTEND_DAYS = "loan.extend.days"
    const val LOAN_EXTEND_MAX = "loan.extend.max"
    const val SESSION_TIMEOUT_MINUTES = "session.timeout.minutes"
    const val LOGIN_FAIL_LIMIT = "login.fail.limit"
    const val LOGIN_LOCK_MINUTES = "login.lock.minutes"
}
