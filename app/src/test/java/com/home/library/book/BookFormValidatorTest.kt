package com.home.library.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISBN-10 → ISBN-13 변환 및 X(=10) 체크디지트 처리 검증.
 * 특히 X가 all(isDigit) 류 검사에 걸러지지 않는지 명시적으로 고정한다.
 */
class BookFormValidatorTest {

    // ── normalizeIsbn: ISBN-10 → 13 변환 ──────────────────────────

    @Test
    fun normalize_isbn10_digitsOnly_convertsTo13() {
        assertEquals("9788992632317", BookFormValidator.normalizeIsbn("8992632312"))
    }

    @Test
    fun normalize_isbn10_withCheckDigitX_convertsTo13() {
        // X(=10) 체크디지트가 걸러지면 안 됨 (사용자 검산값)
        assertEquals("9780975229804", BookFormValidator.normalizeIsbn("097522980X"))
    }

    @Test
    fun normalize_isbn10_lowercaseX_convertsTo13() {
        assertEquals("9780975229804", BookFormValidator.normalizeIsbn("097522980x"))
    }

    @Test
    fun normalize_isbn10_withHyphens_convertsTo13() {
        assertEquals("9788992632317", BookFormValidator.normalizeIsbn("89-926323-1-2"))
    }

    @Test
    fun normalize_isbn13_unchanged() {
        assertEquals("9788992632317", BookFormValidator.normalizeIsbn("9788992632317"))
    }

    @Test
    fun normalize_blank_returnsNull() {
        assertNull(BookFormValidator.normalizeIsbn(""))
        assertNull(BookFormValidator.normalizeIsbn("   "))
        assertNull(BookFormValidator.normalizeIsbn(null))
    }

    @Test
    fun normalize_invalidIsbn10_notConverted() {
        // 체크디지트 틀린 10자리는 변환하지 않고 그대로 반환(검증 단계에서 거부됨)
        assertEquals("8992632311", BookFormValidator.normalizeIsbn("8992632311"))
    }

    // ── validateIsbn ──────────────────────────────────────────────

    @Test
    fun validate_validIsbn10_ok() {
        assertNull(BookFormValidator.validateIsbn("8992632312"))
        assertNull(BookFormValidator.validateIsbn("097522980X"))
    }

    @Test
    fun validate_invalidIsbn10Checksum_error() {
        assertEquals(IsbnError.ISBN10_CHECKSUM, BookFormValidator.validateIsbn("8992632311"))
    }

    @Test
    fun validate_xInWrongPosition_error() {
        // X는 마지막 자리에만 허용 → 앞자리 X면 ISBN-10 체크디지트 오류
        assertEquals(IsbnError.ISBN10_CHECKSUM, BookFormValidator.validateIsbn("09X5229804"))
    }

    @Test
    fun validate_validIsbn13_ok() {
        assertNull(BookFormValidator.validateIsbn("9788992632317"))
    }

    @Test
    fun validate_invalidIsbn13Checksum_error() {
        assertEquals(IsbnError.CHECKSUM, BookFormValidator.validateIsbn("9788992632318"))
    }

    @Test
    fun validate_garbage_formatError() {
        assertEquals(IsbnError.FORMAT, BookFormValidator.validateIsbn("abc"))
        assertEquals(IsbnError.FORMAT, BookFormValidator.validateIsbn("12345678901")) // 11자리
    }

    @Test
    fun validate_blank_ok() {
        assertNull(BookFormValidator.validateIsbn(""))
        assertNull(BookFormValidator.validateIsbn(null))
    }

    // ── isbn13ToIsbn10: 카카오 폴백용 역변환 ──────────────────────

    @Test
    fun isbn13ToIsbn10_roundTrip() {
        assertEquals("8992632312", BookFormValidator.isbn13ToIsbn10("9788992632317"))
        assertEquals("097522980X", BookFormValidator.isbn13ToIsbn10("9780975229804"))
    }

    @Test
    fun isbn13ToIsbn10_979Prefix_returnsNull() {
        // 979 접두는 대응하는 ISBN-10이 없음
        assertNull(BookFormValidator.isbn13ToIsbn10("9791234567896"))
    }
}
