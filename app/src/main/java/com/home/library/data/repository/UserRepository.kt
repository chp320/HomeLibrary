package com.home.library.data.repository

import com.home.library.auth.AuthValidator
import com.home.library.data.local.dao.LoanDao
import com.home.library.data.local.dao.UserDao
import com.home.library.data.local.entity.UserEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus
import com.home.library.data.local.view.UserListItem
import com.home.library.security.PasswordHasher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 관리자 사용자 관리. 필드 형식 검증은 AuthValidator(호출 측 ViewModel)가 담당한다.
 * - 수정 시 login_id는 변경하지 않는다(수정 불가).
 * - 비밀번호는 입력 시에만 재해시, 공백이면 기존 해시 유지.
 * - 대출 중(활성 대출 보유)이면 비활성화(INACTIVE) 차단.
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val loanDao: LoanDao,
) {
    fun searchUsers(query: String, role: UserRole?, status: UserStatus?): Flow<List<UserListItem>> =
        userDao.searchUsers(query.trim(), role?.name, status?.name)

    suspend fun getById(userId: Long): UserEntity? = userDao.getById(userId)

    /** 사용자 생성. 초기 비번은 pwd_change_required=true → 최초 로그인 시 강제 변경. */
    suspend fun createUser(
        loginId: String,
        password: String,
        name: String,
        phone: String?,
        role: UserRole,
    ): CreateUserResult {
        // 중복 검사·저장 모두 정규화된 값으로(AuthRepository.signUp과 동일 규칙).
        val id = AuthValidator.normalizeLoginId(loginId)
        if (userDao.getByLoginId(id) != null) return CreateUserResult.DuplicateLoginId
        userDao.insert(
            UserEntity(
                loginId = id,
                passwordHash = PasswordHasher.hash(password),
                name = name.trim(),
                phone = phone?.trim()?.ifBlank { null },
                role = role,
                status = UserStatus.ACTIVE,
                pwdChangeRequired = true,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return CreateUserResult.Success
    }

    /**
     * 사용자 수정. login_id는 유지. newPassword가 blank면 기존 해시 유지.
     * 대출 중인데 INACTIVE로 바꾸려 하면 차단.
     */
    suspend fun updateUser(
        userId: Long,
        name: String,
        phone: String?,
        role: UserRole,
        status: UserStatus,
        newPassword: String?,
    ): UpdateUserResult {
        val user = userDao.getById(userId) ?: return UpdateUserResult.NotFound
        if (status == UserStatus.INACTIVE && loanDao.countActiveByUser(userId) > 0) {
            return UpdateUserResult.HasActiveLoans
        }
        val passwordHash = if (!newPassword.isNullOrBlank()) {
            PasswordHasher.hash(newPassword)
        } else {
            user.passwordHash
        }
        userDao.update(
            user.copy(
                name = name.trim(),
                phone = phone?.trim()?.ifBlank { null },
                role = role,
                status = status,
                passwordHash = passwordHash,
            ),
        )
        return UpdateUserResult.Success
    }
}

sealed interface CreateUserResult {
    data object Success : CreateUserResult
    data object DuplicateLoginId : CreateUserResult
}

sealed interface UpdateUserResult {
    data object Success : UpdateUserResult
    data object NotFound : UpdateUserResult
    data object HasActiveLoans : UpdateUserResult
}
