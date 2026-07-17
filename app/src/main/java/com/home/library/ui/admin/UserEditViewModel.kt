package com.home.library.ui.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.auth.AuthValidator
import com.home.library.auth.LoginIdError
import com.home.library.auth.NameError
import com.home.library.auth.PasswordError
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus
import com.home.library.data.repository.CreateUserResult
import com.home.library.data.repository.UpdateUserResult
import com.home.library.data.repository.UserRepository
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import com.home.library.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserEditViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: Long = savedStateHandle[Routes.USER_EDIT_ARG_ID] ?: Routes.USER_EDIT_NEW_ID
    private val isEdit: Boolean = userId != Routes.USER_EDIT_NEW_ID

    private val _ui = MutableStateFlow(UserEditUiState(isEdit = isEdit, isAdmin = isAdmin()))
    val ui: StateFlow<UserEditUiState> = _ui.asStateFlow()

    init {
        if (isEdit) {
            viewModelScope.launch {
                userRepository.getById(userId)?.let { u ->
                    _ui.update {
                        it.copy(
                            loginId = u.loginId,
                            name = u.name,
                            phone = u.phone.orEmpty(),
                            role = u.role,
                            status = u.status,
                        )
                    }
                }
            }
        }
    }

    fun onLoginIdChange(v: String) = _ui.update { it.copy(loginId = v, loginIdError = null, loginIdDuplicate = false) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, passwordError = null) }
    fun onNameChange(v: String) = _ui.update { it.copy(name = v, nameError = null) }
    fun onPhoneChange(v: String) = _ui.update { it.copy(phone = v) }
    fun onRoleChange(v: UserRole) = _ui.update { it.copy(role = v) }
    fun onStatusChange(v: UserStatus) = _ui.update { it.copy(status = v, hasActiveLoans = false) }

    fun submit() {
        val s = _ui.value
        if (s.loading) return
        if (!isAdmin()) {
            _ui.update { it.copy(isAdmin = false) }
            return
        }

        val nameError = AuthValidator.validateName(s.name)
        // 등록: 아이디·비번 필수 검증. 수정: 아이디는 불변, 비번은 입력 시에만 검증.
        val loginIdError = if (!isEdit) AuthValidator.validateLoginId(s.loginId) else null
        val passwordError = when {
            !isEdit -> AuthValidator.validatePassword(s.password)
            s.password.isNotBlank() -> AuthValidator.validatePassword(s.password)
            else -> null
        }
        if (nameError != null || loginIdError != null || passwordError != null) {
            _ui.update { it.copy(nameError = nameError, loginIdError = loginIdError, passwordError = passwordError) }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            if (isEdit) {
                val result = userRepository.updateUser(
                    userId = userId,
                    name = s.name,
                    phone = s.phone,
                    role = s.role,
                    status = s.status,
                    newPassword = s.password.ifBlank { null },
                )
                _ui.update {
                    when (result) {
                        UpdateUserResult.Success, UpdateUserResult.NotFound -> it.copy(loading = false, done = true)
                        UpdateUserResult.HasActiveLoans -> it.copy(loading = false, hasActiveLoans = true)
                    }
                }
            } else {
                val result = userRepository.createUser(
                    // 정규화(trim)는 name/phone과 마찬가지로 Repository가 담당한다.
                    loginId = s.loginId,
                    password = s.password,
                    name = s.name,
                    phone = s.phone,
                    role = s.role,
                )
                _ui.update {
                    when (result) {
                        CreateUserResult.Success -> it.copy(loading = false, done = true)
                        CreateUserResult.DuplicateLoginId -> it.copy(loading = false, loginIdDuplicate = true)
                    }
                }
            }
        }
    }

    private fun isAdmin(): Boolean =
        (sessionManager.state.value as? SessionState.LoggedIn)?.role == UserRole.ROLE_ADMIN
}

data class UserEditUiState(
    val isEdit: Boolean = false,
    val isAdmin: Boolean = true,
    val loginId: String = "",
    val password: String = "",
    val name: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.ROLE_USER,
    val status: UserStatus = UserStatus.ACTIVE,
    val loginIdError: LoginIdError? = null,
    val loginIdDuplicate: Boolean = false,
    val passwordError: PasswordError? = null,
    val nameError: NameError? = null,
    val hasActiveLoans: Boolean = false,
    val loading: Boolean = false,
    val done: Boolean = false,
)
