package com.home.library.data.repository

import android.util.Log
import com.home.library.book.BookFormValidator
import com.home.library.data.remote.KakaoBookApi
import com.home.library.data.remote.mapper.KakaoBookMapper
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ISBN으로 카카오 책 API를 조회해 폼 자동채움용 초안을 얻는다.
 * 타임아웃/네트워크 예외는 1회 재시도.
 * 13자리 조회가 0건이고 978 접두면, 오래된 책 대비 원본 ISBN-10으로 1회 더 조회(폴백).
 * 저장 ISBN은 항상 13자리(canonicalIsbn)로 통일.
 */
@Singleton
class BookLookupRepository @Inject constructor(
    private val api: KakaoBookApi,
) {

    suspend fun lookupByIsbn(isbn: String): LookupResult {
        val primary = queryOnce(query = isbn, canonicalIsbn = isbn)
        if (primary !is LookupResult.NotFound) return primary
        // 13자리 0건 → ISBN-10 폴백(978 접두만, 979는 ISBN-10 없음)
        val isbn10 = BookFormValidator.isbn13ToIsbn10(isbn) ?: return primary
        val fallback = queryOnce(query = isbn10, canonicalIsbn = isbn)
        return if (fallback is LookupResult.Success) fallback else primary
    }

    private suspend fun queryOnce(query: String, canonicalIsbn: String): LookupResult {
        repeat(MAX_ATTEMPTS) {
            try {
                val response = api.searchByIsbn(query = query)
                if (!response.isSuccessful) {
                    return when (response.code()) {
                        401, 403 -> LookupResult.AuthError
                        429 -> LookupResult.RateLimited
                        else -> LookupResult.NetworkError
                    }
                }
                val doc = response.body()?.documents?.firstOrNull()
                    ?: return LookupResult.NotFound
                return LookupResult.Success(KakaoBookMapper.toDraft(doc, canonicalIsbn))
            } catch (e: IOException) {
                // 타임아웃/네트워크 → 다음 시도로 재시도(마지막 시도면 아래에서 NetworkError)
                Log.w(TAG, "queryOnce: IOException, 재시도", e)
            } catch (e: Exception) {
                Log.e(TAG, "queryOnce: 예외", e)
                return LookupResult.NetworkError
            }
        }
        return LookupResult.NetworkError
    }

    companion object {
        private const val MAX_ATTEMPTS = 2 // 최초 1회 + 재시도 1회
        private const val TAG = "HomeLib"
    }
}

/** 폼 자동채움 초안. 수량은 사용자가 폼에서 지정한다. */
data class BookDraft(
    val isbn: String,
    val title: String,
    val author: String?,
    val publisher: String?,
    val pubDate: String?,
    val coverUrl: String?,
)

sealed interface LookupResult {
    data class Success(val draft: BookDraft) : LookupResult
    data object NotFound : LookupResult
    data object AuthError : LookupResult
    data object RateLimited : LookupResult
    data object NetworkError : LookupResult
}
