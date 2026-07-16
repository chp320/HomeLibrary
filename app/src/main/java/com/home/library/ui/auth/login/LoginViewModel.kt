package com.home.library.ui.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.repository.AuthRepository
import com.home.library.data.repository.LoginResult
import com.home.library.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun onLoginIdChange(value: String) = _ui.update { it.copy(loginId = value, error = null) }

    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, error = null) }

    fun login() {
        val state = _ui.value
        if (state.loading || state.loginId.isBlank() || state.password.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            when (val result = authRepository.login(state.loginId.trim(), state.password)) {
                is LoginResult.Success -> {
                    if (result.user.pwdChangeRequired) {
                        // 세션은 시작하지 않고 강제 변경 화면으로 유도
                        _ui.update { it.copy(loading = false, forceChangeUserId = result.user.userId) }
                    } else {
                        // 세션 시작 → AppNavHost가 홈으로 이동시킨다
                        sessionManager.start(result.user)
                        _ui.update { it.copy(loading = false, password = "") }
                    }
                }
                is LoginResult.InvalidCredentials ->
                    _ui.update { it.copy(loading = false, error = LoginError.Invalid(result.remainingAttempts)) }
                is LoginResult.Locked ->
                    _ui.update { it.copy(loading = false, error = LoginError.Locked(toMinutes(result.remainingMillis))) }
                LoginResult.Inactive ->
                    _ui.update { it.copy(loading = false, error = LoginError.Inactive) }
            }
        }
    }

    /** 강제 변경 화면으로 이동을 소비한 뒤 호출. */
    fun onForceChangeHandled() = _ui.update { it.copy(forceChangeUserId = null) }

    private fun toMinutes(millis: Long): Int = max(1, ceil(millis / 60_000.0).toInt())
}

data class LoginUiState(
    val loginId: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: LoginError? = null,
    /** 값이 있으면 해당 userId의 강제 비밀번호 변경 화면으로 이동. */
    val forceChangeUserId: Long? = null,
)

sealed interface LoginError {
    data class Invalid(val remainingAttempts: Int?) : LoginError
    data class Locked(val minutes: Int) : LoginError
    data object Inactive : LoginError
}
