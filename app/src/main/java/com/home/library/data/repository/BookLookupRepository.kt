package com.home.library.data.repository

import android.util.Log
import com.home.library.data.remote.KakaoBookApi
import com.home.library.data.remote.mapper.KakaoBookMapper
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ISBN으로 카카오 책 API를 조회해 폼 자동채움용 초안을 얻는다.
 * 타임아웃/네트워크 예외는 1회 재시도. 결과 코드별로 [LookupResult] 반환.
 */
@Singleton
class BookLookupRepository @Inject constructor(
    private val api: KakaoBookApi,
) {

    suspend fun lookupByIsbn(isbn: String): LookupResult {
        repeat(MAX_ATTEMPTS) {
            try {
                val response = api.searchByIsbn(query = isbn)
                if (!response.isSuccessful) {
                    return when (response.code()) {
                        401, 403 -> LookupResult.AuthError
                        429 -> LookupResult.RateLimited
                        else -> LookupResult.NetworkError
                    }
                }
                val doc = response.body()?.documents?.firstOrNull()
                    ?: return LookupResult.NotFound
                return LookupResult.Success(KakaoBookMapper.toDraft(doc, isbn))
            } catch (e: IOException) {
                // 타임아웃/네트워크 → 다음 시도로 재시도(마지막 시도면 아래에서 NetworkError)
                Log.w(TAG, "lookupByIsbn: IOException, 재시도", e)
            } catch (e: Exception) {
                Log.e(TAG, "lookupByIsbn: 예외", e)
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
