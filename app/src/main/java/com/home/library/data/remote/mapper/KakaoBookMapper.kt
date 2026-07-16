package com.home.library.data.remote.mapper

import com.home.library.data.remote.dto.KakaoBookDocument
import com.home.library.data.repository.BookDraft
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * 카카오 응답 문서를 폼 자동채움용 [BookDraft]로 변환한다.
 * - datetime(ISO8601) → YYYY-MM-DD 로 변환하여 BookFormValidator의 날짜 검증을 통과시킨다.
 * - isbn 필드에 ISBN-10/13이 공백 구분으로 올 수 있어 13자리만 선택.
 */
object KakaoBookMapper {

    private val DATE_PREFIX_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun toDraft(doc: KakaoBookDocument, queriedIsbn: String): BookDraft = BookDraft(
        isbn = pickIsbn13(doc.isbn) ?: queriedIsbn,
        title = doc.title.orEmpty().trim(),
        author = doc.authors.joinToString(", ").ifBlank { null },
        publisher = doc.publisher?.trim()?.ifBlank { null },
        pubDate = toDate(doc.datetime),
        coverUrl = doc.thumbnail?.trim()?.ifBlank { null },
    )

    private fun pickIsbn13(raw: String?): String? =
        raw?.split(" ", ",")
            ?.map { it.trim() }
            ?.firstOrNull { it.length == 13 && it.all(Char::isDigit) }

    /** ISO8601 datetime → YYYY-MM-DD. 파싱 실패 시 앞 10자리가 날짜 형식이면 사용, 아니면 null. */
    fun toDate(datetime: String?): String? {
        if (datetime.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(datetime).toLocalDate().toString()
        } catch (e: DateTimeParseException) {
            val head = datetime.take(10)
            if (DATE_PREFIX_REGEX.matches(head)) head else null
        }
    }
}
