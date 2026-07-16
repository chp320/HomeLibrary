package com.home.library.data.repository

import com.home.library.data.local.ConfigKeys
import com.home.library.data.local.dao.AppConfigDao
import com.home.library.data.local.dao.UserDao
import com.home.library.data.local.entity.UserEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus
import com.home.library.security.PasswordHasher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인증 도메인: 가입 / 로그인 / 비밀번호 변경.
 * 세션 보관은 SessionManager가, 필드 형식 검증은 AuthValidator가 담당한다.
 *
 * 잠금 임계값(login.fail.limit)과 잠금 시간(login.lock.minutes)은 AppConfig에서 읽는다(하드코딩 금지).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val appConfigDao: AppConfigDao,
) {

    /** 회원가입. 일반 사용자(ROLE_USER)로 생성. loginId 중복이면 실패. */
    suspend fun signUp(
        loginId: String,
        password: String,
        name: String,
        phone: String?,
    ): SignUpResult {
        if (userDao.getByLoginId(loginId) != null) return SignUpResult.DuplicateLoginId
        val now = System.currentTimeMillis()
        val user = UserEntity(
            loginId = loginId,
            passwordHash = PasswordHasher.hash(password),
            name = name.trim(),
            phone = phone?.trim()?.ifBlank { null },
            role = UserRole.ROLE_USER,
            status = UserStatus.ACTIVE,
            failCount = 0,
            lockedUntil = null,
            pwdChangeRequired = false,
            createdAt = now,
        )
        val userId = userDao.insert(user)
        return SignUpResult.Success(userId)
    }

    /**
     * 로그인 처리 순서 (설계 확정):
     * 1) 조회 → 없으면 일반 실패
     * 2) status 판정 (INACTIVE 거부)
     * 3) 잠금 판정 (LOCKED & locked_until > now 이면 거부, 만료면 해제 후 진행)
     * 4) 해시 검증 (실패 시 fail_count++, 임계 도달 시 LOCKED)
     * 5) 성공 시 fail_count/lock 리셋
     * 6) pwd_change_required는 결과에 담아 호출자가 라우팅
     */
    suspend fun login(loginId: String, password: String): LoginResult {
        val user = userDao.getByLoginId(loginId) ?: return LoginResult.InvalidCredentials(null)

        // 2) status 판정
        if (user.status == UserStatus.INACTIVE) return LoginResult.Inactive

        // 3) 잠금 판정
        val now = System.currentTimeMillis()
        var lockExpired = false
        if (user.status == UserStatus.LOCKED) {
            if (user.lockedUntil != null && user.lockedUntil > now) {
                return LoginResult.Locked(user.lockedUntil - now)
            }
            lockExpired = true // 잠금 시간 경과 → 해제하고 검증 진행
        }

        // 4) 해시 검증
        val verified = PasswordHasher.verify(password, user.passwordHash)
        if (!verified) {
            val base = if (lockExpired) 0 else user.failCount
            val newFail = base + 1
            val limit = failLimit()
            return if (newFail >= limit) {
                val lockMs = lockMinutes() * 60_000L
                userDao.update(
                    user.copy(
                        failCount = newFail,
                        status = UserStatus.LOCKED,
                        lockedUntil = now + lockMs,
                    ),
                )
                LoginResult.Locked(lockMs)
            } else {
                userDao.update(
                    user.copy(
                        failCount = newFail,
                        status = UserStatus.ACTIVE,
                        lockedUntil = null,
                    ),
                )
                LoginResult.InvalidCredentials(remainingAttempts = limit - newFail)
            }
        }

        // 5) 성공 → 리셋
        val loggedIn = user.copy(
            failCount = 0,
            lockedUntil = null,
            status = UserStatus.ACTIVE,
        )
        userDao.update(loggedIn)

        // 6) pwd_change_required 여부는 결과에 담아 전달
        return LoginResult.Success(loggedIn)
    }

    /**
     * 비밀번호 변경(admin 최초 강제 변경 포함).
     * 기존 비밀번호 재사용은 거부한다(admin1234 등).
     */
    suspend fun changePassword(userId: Long, newPassword: String): ChangePasswordResult {
        val user = userDao.getById(userId) ?: return ChangePasswordResult.UserNotFound
        if (PasswordHasher.verify(newPassword, user.passwordHash)) {
            return ChangePasswordResult.SameAsOld
        }
        val updated = user.copy(
            passwordHash = PasswordHasher.hash(newPassword),
            pwdChangeRequired = false,
        )
        userDao.update(updated)
        return ChangePasswordResult.Success(updated)
    }

    private suspend fun failLimit(): Int =
        appConfigDao.getValue(ConfigKeys.LOGIN_FAIL_LIMIT)?.toIntOrNull() ?: DEFAULT_FAIL_LIMIT

    private suspend fun lockMinutes(): Long =
        appConfigDao.getValue(ConfigKeys.LOGIN_LOCK_MINUTES)?.toLongOrNull() ?: DEFAULT_LOCK_MINUTES

    companion object {
        // 정상 경로에서는 시드로 항상 존재. DB 손상 등 예외 상황의 방어적 기본값.
        private const val DEFAULT_FAIL_LIMIT = 5
        private const val DEFAULT_LOCK_MINUTES = 5L
    }
}

sealed interface SignUpResult {
    data class Success(val userId: Long) : SignUpResult
    data object DuplicateLoginId : SignUpResult
}

sealed interface LoginResult {
    data class Success(val user: UserEntity) : LoginResult
    /** 아이디 없음 또는 비밀번호 불일치. remainingAttempts=null이면 아이디 미상(정보 노출 방지). */
    data class InvalidCredentials(val remainingAttempts: Int?) : LoginResult
    data class Locked(val remainingMillis: Long) : LoginResult
    data object Inactive : LoginResult
}

sealed interface ChangePasswordResult {
    data class Success(val user: UserEntity) : ChangePasswordResult
    data object SameAsOld : ChangePasswordResult
    data object UserNotFound : ChangePasswordResult
}
