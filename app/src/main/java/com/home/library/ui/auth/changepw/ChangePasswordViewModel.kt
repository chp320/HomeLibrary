package com.home.library.ui.auth.changepw

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.auth.AuthValidator
import com.home.library.auth.PasswordError
import com.home.library.data.repository.AuthRepository
import com.home.library.data.repository.ChangePasswordResult
import com.home.library.session.SessionManager
import com.home.library.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: Long = savedStateHandle[Routes.CHANGE_PASSWORD_ARG_USER_ID] ?: -1L

    private val _ui = MutableStateFlow(ChangePasswordUiState())
    val ui: StateFlow<ChangePasswordUiState> = _ui.asStateFlow()

    fun onNewPasswordChange(v: String) = _ui.update { it.copy(newPassword = v, passwordError = null, sameAsOld = false) }
    fun onConfirmChange(v: String) = _ui.update { it.copy(confirm = v, confirmMismatch = false) }

    fun submit() {
        val s = _ui.value
        if (s.loading) return

        val passwordError = AuthValidator.validatePassword(s.newPassword)
        val confirmMismatch = !AuthValidator.passwordsMatch(s.newPassword, s.confirm)
        if (passwordError != null || confirmMismatch) {
            _ui.update { it.copy(passwordError = passwordError, confirmMismatch = confirmMismatch) }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            when (val result = authRepository.changePassword(userId, s.newPassword)) {
                is ChangePasswordResult.Success -> {
                    // 변경 완료 → 세션 시작(AppNavHost가 홈으로 이동)
                    sessionManager.start(result.user)
                    _ui.update { it.copy(loading = false) }
                }
                ChangePasswordResult.SameAsOld -> _ui.update { it.copy(loading = false, sameAsOld = true) }
                ChangePasswordResult.UserNotFound -> _ui.update { it.copy(loading = false) }
            }
        }
    }
}

data class ChangePasswordUiState(
    val newPassword: String = "",
    val confirm: String = "",
    val passwordError: PasswordError? = null,
    val confirmMismatch: Boolean = false,
    val sameAsOld: Boolean = false,
    val loading: Boolean = false,
)
