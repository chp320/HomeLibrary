package com.home.library.book

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 도서 등록/수정 폼 검증 (도메인 계층).
 * 수기 입력과 4단계 API 자동채움이 동일한 검증 경로를 공유한다.
 *
 * - 제목: 공백 불가, 200자 이하.
 * - 수량: 정수, 1~9999.
 * - ISBN: 선택. 입력 시 하이픈/공백 제거 후 13자리 숫자 + EAN-13 체크디지트.
 * - 출판일: 선택. 입력 시 YYYY-MM-DD 형식 + 실존 날짜.
 * - 저자/출판사/분류/위치: 선택, 100자 이하.
 */
object BookFormValidator {
    const val QTY_MIN = 1
    const val QTY_MAX = 9999
    const val TITLE_MAX = 200
    const val OPTIONAL_TEXT_MAX = 100

    private val ISBN13_REGEX = Regex("^\\d{13}$")
    private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun validateTitle(value: String): TitleError? = when {
        value.isBlank() -> TitleError.BLANK
        value.trim().length > TITLE_MAX -> TitleError.TOO_LONG
        else -> null
    }

    fun validateQuantity(value: String): QuantityError? {
        if (value.isBlank()) return QuantityError.BLANK
        val n = value.trim().toIntOrNull() ?: return QuantityError.INVALID
        return when {
            n < QTY_MIN -> QuantityError.TOO_SMALL
            n > QTY_MAX -> QuantityError.TOO_LARGE
            else -> null
        }
    }

    /** 검증 통과를 전제로 수량 정수를 반환. 범위 밖이면 null. */
    fun parseQuantity(value: String): Int? = value.trim().toIntOrNull()?.takeIf { it in QTY_MIN..QTY_MAX }

    /** 하이픈/공백 제거 + 정규화. blank면 null(=ISBN 없음). */
    fun normalizeIsbn(raw: String?): String? =
        raw?.replace("-", "")?.replace(" ", "")?.trim()?.ifBlank { null }

    fun validateIsbn(raw: String?): IsbnError? {
        val cleaned = normalizeIsbn(raw) ?: return null // 빈 값 허용
        if (!ISBN13_REGEX.matches(cleaned)) return IsbnError.FORMAT
        if (!isValidIsbn13Checksum(cleaned)) return IsbnError.CHECKSUM
        return null
    }

    fun validatePubDate(raw: String?): PubDateError? {
        val v = raw?.trim()
        if (v.isNullOrBlank()) return null // 빈 값 허용
        if (!DATE_REGEX.matches(v)) return PubDateError.FORMAT
        return try {
            // ISO_LOCAL_DATE는 STRICT 파싱 → 2024-13-45, 2024-02-30 등 거부
            LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE)
            null
        } catch (e: DateTimeParseException) {
            PubDateError.INVALID
        }
    }

    fun validateOptionalText(value: String): OptionalTextError? =
        if (value.trim().length > OPTIONAL_TEXT_MAX) OptionalTextError.TOO_LONG else null

    /** EAN-13(ISBN-13) 체크디지트 검증. 입력은 13자리 숫자 문자열. */
    private fun isValidIsbn13Checksum(isbn: String): Boolean {
        var sum = 0
        for (i in 0 until 12) {
            val d = isbn[i] - '0'
            sum += if (i % 2 == 0) d else d * 3
        }
        val check = (10 - (sum % 10)) % 10
        return check == (isbn[12] - '0')
    }
}

enum class TitleError { BLANK, TOO_LONG }
enum class QuantityError { BLANK, INVALID, TOO_SMALL, TOO_LARGE }
enum class IsbnError { FORMAT, CHECKSUM }
enum class PubDateError { FORMAT, INVALID }
enum class OptionalTextError { TOO_LONG }
