package com.home.library.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 아이디 정규화(A-4) 검증.
 *
 * 핵심: 검증과 저장이 **같은 값**을 봐야 한다.
 * 정규화 전 값으로 검증하면 붙여넣기로 딸려온 공백 때문에 멀쩡한 아이디가 FORMAT 오류로 거부된다.
 */
class AuthValidatorTest {

    // ── normalizeLoginId ──────────────────────────────────────────

    @Test
    fun `앞뒤 공백을 제거한다`() {
        assertEquals("abc123", AuthValidator.normalizeLoginId("  abc123  "))
    }

    @Test
    fun `개행·탭도 제거한다`() {
        // HID 스캐너·붙여넣기 경로에서 섞여 들어올 수 있다.
        assertEquals("abc123", AuthValidator.normalizeLoginId("abc123\n"))
        assertEquals("abc123", AuthValidator.normalizeLoginId("\tabc123 "))
    }

    @Test
    fun `가운데 공백은 남긴다 - 정규식이 FORMAT 오류로 거부해야 한다`() {
        assertEquals("abc 123", AuthValidator.normalizeLoginId(" abc 123 "))
    }

    @Test
    fun `정규화는 멱등이다`() {
        val once = AuthValidator.normalizeLoginId("  abc123  ")
        assertEquals(once, AuthValidator.normalizeLoginId(once))
    }

    // ── validateLoginId: 정규화 후 검증 ────────────────────────────

    @Test
    fun `앞뒤 공백이 있어도 통과한다`() {
        assertNull(AuthValidator.validateLoginId("  abc123  "))
    }

    @Test
    fun `공백 포함 4자는 정규화 후 4자라 길이 검증을 통과한다`() {
        // trim 전 길이(6)로 재면 통과, 후(4)로 재도 통과 — 경계에서 규칙이 갈리지 않는지 고정.
        assertNull(AuthValidator.validateLoginId(" ab12 "))
    }

    @Test
    fun `공백을 걷어내면 3자인 아이디는 길이 미달로 거부한다`() {
        assertEquals(LoginIdError.LENGTH, AuthValidator.validateLoginId("  ab1  "))
    }

    @Test
    fun `가운데 공백은 형식 오류로 거부한다`() {
        assertEquals(LoginIdError.FORMAT, AuthValidator.validateLoginId("abc 123"))
    }

    @Test
    fun `공백만 있으면 빈 값으로 거부한다`() {
        assertEquals(LoginIdError.BLANK, AuthValidator.validateLoginId("   "))
    }

    @Test
    fun `대문자는 형식 오류로 거부한다`() {
        assertEquals(LoginIdError.FORMAT, AuthValidator.validateLoginId("Abc123"))
    }

    @Test
    fun `한글은 형식 오류로 거부한다`() {
        assertEquals(LoginIdError.FORMAT, AuthValidator.validateLoginId("홍길동12"))
    }

    @Test
    fun `21자는 길이 초과로 거부한다`() {
        assertEquals(LoginIdError.LENGTH, AuthValidator.validateLoginId("a".repeat(21)))
    }

    @Test
    fun `20자는 통과한다`() {
        assertNull(AuthValidator.validateLoginId("a".repeat(20)))
    }
}
