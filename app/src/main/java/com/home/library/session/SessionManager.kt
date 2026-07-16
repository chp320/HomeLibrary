package com.home.library.session

import com.home.library.data.local.entity.UserEntity
import com.home.library.data.local.enums.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로그인 세션을 메모리에만 보관한다 (설계 원칙 9: SharedPreferences 등 평문 저장 금지).
 * 프로세스가 종료되면 세션도 사라진다.
 *
 * 활동시각(lastActivityAt)은 사용자 입력(터치/키)마다 갱신되며,
 * SessionTimeoutHandler가 이 값을 폴링해 자동 로그아웃을 판정한다.
 */
@Singleton
class SessionManager @Inject constructor() {

    private val _state = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val isLoggedIn: Boolean
        get() = _state.value is SessionState.LoggedIn

    /** 로그인 성공 시 세션 시작. 현재 시각을 활동시각 기준점으로 삼는다. */
    fun start(user: UserEntity) {
        _state.value = SessionState.LoggedIn(
            userId = user.userId,
            loginId = user.loginId,
            name = user.name,
            role = user.role,
            lastActivityAt = System.currentTimeMillis(),
        )
    }

    /** 로그아웃(수동/자동 공통). */
    fun clear() {
        _state.value = SessionState.LoggedOut
    }

    /** 사용자 활동 감지 시 활동시각 갱신. 로그인 상태가 아니면 무시. */
    fun recordActivity() {
        val current = _state.value
        if (current is SessionState.LoggedIn) {
            _state.value = current.copy(lastActivityAt = System.currentTimeMillis())
        }
    }

    // 대출/반납 트랜잭션 중 자동 로그아웃 유예용 카운터.
    private val criticalSections = AtomicInteger(0)

    /** 크리티컬 섹션 진행 중이면 SessionTimeoutHandler가 만료 판정을 미룬다. */
    val inCriticalSection: Boolean
        get() = criticalSections.get() > 0

    /**
     * 블록 실행 동안 자동 로그아웃을 유예한다. 예외가 나도 카운터가 반드시 복원되도록 try/finally.
     * 완료 후 활동시각을 갱신해 남은 타이머를 리셋한다.
     */
    suspend fun <T> withCriticalSection(block: suspend () -> T): T {
        criticalSections.incrementAndGet()
        try {
            return block()
        } finally {
            criticalSections.decrementAndGet()
            recordActivity()
        }
    }
}

sealed interface SessionState {
    data object LoggedOut : SessionState

    data class LoggedIn(
        val userId: Long,
        val loginId: String,
        val name: String,
        val role: UserRole,
        val lastActivityAt: Long,
    ) : SessionState
}
