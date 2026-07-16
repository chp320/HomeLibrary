package com.home.library.ui.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.auth.AuthValidator
import com.home.library.auth.LoginIdError
import com.home.library.auth.NameError
import com.home.library.auth.PasswordError
import com.home.library.data.repository.AuthRepository
import com.home.library.data.repository.SignUpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SignUpUiState())
    val ui: StateFlow<SignUpUiState> = _ui.asStateFlow()

    fun onLoginIdChange(v: String) = _ui.update { it.copy(loginId = v, loginIdError = null, loginIdDuplicate = false) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, passwordError = null) }
    fun onPasswordConfirmChange(v: String) = _ui.update { it.copy(passwordConfirm = v, confirmMismatch = false) }
    fun onNameChange(v: String) = _ui.update { it.copy(name = v, nameError = null) }
    fun onPhoneChange(v: String) = _ui.update { it.copy(phone = v) }

    fun submit() {
        val s = _ui.value
        if (s.loading) return

        val loginIdError = AuthValidator.validateLoginId(s.loginId)
        val passwordError = AuthValidator.validatePassword(s.password)
        val confirmMismatch = !AuthValidator.passwordsMatch(s.password, s.passwordConfirm)
        val nameError = AuthValidator.validateName(s.name)

        if (loginIdError != null || passwordError != null || confirmMismatch || nameError != null) {
            _ui.update {
                it.copy(
                    loginIdError = loginIdError,
                    passwordError = passwordError,
                    confirmMismatch = confirmMismatch,
                    nameError = nameError,
                )
            }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            when (authRepository.signUp(s.loginId.trim(), s.password, s.name, s.phone)) {
                is SignUpResult.Success -> _ui.update { it.copy(loading = false, done = true) }
                SignUpResult.DuplicateLoginId -> _ui.update { it.copy(loading = false, loginIdDuplicate = true) }
            }
        }
    }
}

data class SignUpUiState(
    val loginId: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val name: String = "",
    val phone: String = "",
    val loginIdError: LoginIdError? = null,
    val loginIdDuplicate: Boolean = false,
    val passwordError: PasswordError? = null,
    val confirmMismatch: Boolean = false,
    val nameError: NameError? = null,
    val loading: Boolean = false,
    /** 가입 성공. 화면은 이 값을 보고 로그인 화면으로 돌아간다. */
    val done: Boolean = false,
)
