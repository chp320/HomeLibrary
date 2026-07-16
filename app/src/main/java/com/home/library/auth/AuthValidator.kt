package com.home.library.auth

/**
 * 가입/비밀번호 변경 입력 검증 (표준 규칙).
 * - 아이디: 영소문자+숫자 4~20자
 * - 비밀번호: 8~64자, 영문+숫자 각 1자 이상
 * - 이름: 공백 불가, 20자 이하
 *
 * 반환값 null이면 통과, 아니면 오류 코드. 화면(Composable)이 strings.xml로 매핑한다.
 */
object AuthValidator {
    const val LOGIN_ID_MIN = 4
    const val LOGIN_ID_MAX = 20
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64
    const val NAME_MAX = 20

    private val LOGIN_ID_REGEX = Regex("^[a-z0-9]+$")

    fun validateLoginId(value: String): LoginIdError? = when {
        value.isBlank() -> LoginIdError.BLANK
        value.length < LOGIN_ID_MIN || value.length > LOGIN_ID_MAX -> LoginIdError.LENGTH
        !LOGIN_ID_REGEX.matches(value) -> LoginIdError.FORMAT
        else -> null
    }

    fun validatePassword(value: String): PasswordError? = when {
        value.isBlank() -> PasswordError.BLANK
        value.length < PASSWORD_MIN || value.length > PASSWORD_MAX -> PasswordError.LENGTH
        !(value.any { it.isLetter() } && value.any { it.isDigit() }) -> PasswordError.COMPOSITION
        else -> null
    }

    /** 비밀번호 확인란 일치 여부. */
    fun passwordsMatch(password: String, confirm: String): Boolean = password == confirm

    fun validateName(value: String): NameError? = when {
        value.isBlank() -> NameError.BLANK
        value.trim().length > NAME_MAX -> NameError.LENGTH
        else -> null
    }
}

enum class LoginIdError { BLANK, LENGTH, FORMAT }
enum class PasswordError { BLANK, LENGTH, COMPOSITION }
enum class NameError { BLANK, LENGTH }
