package com.home.library.security

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BCrypt 비밀번호 해싱 (설계 원칙 7).
 * - cost 12
 * - 평문 저장·로그 금지
 * - 앱 런타임 해싱은 Dispatchers.Default에서 수행 (suspend API).
 * - 시드 콜백은 이미 백그라운드 스레드이므로 blocking API 사용.
 */
object PasswordHasher {

    private const val COST = 12

    /** 앱 런타임용. Dispatchers.Default에서 해싱. */
    suspend fun hash(plain: String): String = withContext(Dispatchers.Default) {
        hashBlocking(plain)
    }

    /** 앱 런타임용. Dispatchers.Default에서 검증. */
    suspend fun verify(plain: String, hash: String): Boolean = withContext(Dispatchers.Default) {
        verifyBlocking(plain, hash)
    }

    /** 시드 콜백 등 이미 백그라운드 스레드인 경우에 한해 사용. */
    fun hashBlocking(plain: String): String =
        BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun verifyBlocking(plain: String, hash: String): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hash).verified
}
