package com.home.library.session

import com.home.library.data.local.ConfigKeys
import com.home.library.data.local.dao.AppConfigDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 자동 로그아웃 감시자.
 * - 10초 주기 폴링으로 (현재시각 - 마지막 활동시각) >= 타임아웃 을 검사한다.
 * - 타임아웃 값은 AppConfig(session.timeout.minutes)에서 읽는다 (하드코딩 금지).
 * - 만료 판정은 wall-clock 타임스탬프 비교라, 백그라운드로 경과한 시간도 그대로 반영된다.
 *   따라서 별도의 백그라운드 타이머 없이도 복귀 시 [checkExpiry] 한 번으로 즉시 만료를 잡아낸다.
 *
 * MainActivity가 onStart에서 [start], onResume에서 [checkExpiry], onStop에서 [stop]을 호출한다.
 */
@Singleton
class SessionTimeoutHandler @Inject constructor(
    private val sessionManager: SessionManager,
    private val appConfigDao: AppConfigDao,
) {
    private var pollJob: Job? = null

    /** 폴링 시작. 이미 돌고 있으면 무시. */
    fun start(scope: CoroutineScope) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                checkExpiry()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** 폴링 중지(백그라운드 진입 시). 세션 자체는 유지된다. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /** 즉시 만료 판정. 백그라운드 복귀(onResume) 직후 호출해 경과분을 반영한다. */
    suspend fun checkExpiry() {
        val current = sessionManager.state.value
        if (current !is SessionState.LoggedIn) return
        // 대출/반납 트랜잭션 진행 중이면 만료 유예(요건: 트랜잭션 중 자동 로그아웃 방지)
        if (sessionManager.inCriticalSection) return
        val elapsed = System.currentTimeMillis() - current.lastActivityAt
        if (elapsed >= timeoutMillis()) {
            sessionManager.clear()
        }
    }

    private suspend fun timeoutMillis(): Long {
        // 정상 경로에서는 시드로 항상 존재. DB 손상 등으로 null이면 방어적 기본값 사용.
        val minutes = appConfigDao.getValue(ConfigKeys.SESSION_TIMEOUT_MINUTES)?.toLongOrNull()
            ?: DEFAULT_TIMEOUT_MINUTES
        return minutes * 60_000L
    }

    companion object {
        private const val POLL_INTERVAL_MS = 10_000L
        private const val DEFAULT_TIMEOUT_MINUTES = 5L
    }
}
