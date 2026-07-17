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

    /**
     * 아이디 정규화. 앞뒤 공백 제거.
     *
     * 저장·조회·검증이 **모두 같은 값**을 보게 하려고 도메인 계층에 둔다
     * (ISBN의 [BookFormValidator.normalizeIsbn]과 같은 취지).
     * 저장 시점 정규화의 최종 책임은 Repository에 있다 — UI를 거치지 않는 호출자도 있기 때문.
     */
    fun normalizeLoginId(value: String): String = value.trim()

    /** 정규화 후 검증한다. 붙여넣기로 딸려온 공백 때문에 FORMAT 오류가 나지 않도록. */
    fun validateLoginId(value: String): LoginIdError? {
        val v = normalizeLoginId(value)
        return when {
            v.isBlank() -> LoginIdError.BLANK
            v.length < LOGIN_ID_MIN || v.length > LOGIN_ID_MAX -> LoginIdError.LENGTH
            !LOGIN_ID_REGEX.matches(v) -> LoginIdError.FORMAT
            else -> null
        }
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
