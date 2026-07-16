package com.home.library.data.remote

import com.home.library.data.remote.dto.KakaoBookResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 카카오 책 검색 API.
 * GET /v3/search/book?target=isbn&query={isbn}&size=1
 * 헤더 Authorization: KakaoAK {key} 는 OkHttp 인터셉터에서 주입.
 */
interface KakaoBookApi {

    @GET("v3/search/book")
    suspend fun searchByIsbn(
        @Query("target") target: String = "isbn",
        @Query("query") query: String,
        @Query("size") size: Int = 1,
    ): Response<KakaoBookResponse>
}
