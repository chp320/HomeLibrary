package com.home.library.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** 카카오 책 검색 응답. 필요한 필드만 취한다. */
@JsonClass(generateAdapter = true)
data class KakaoBookResponse(
    @Json(name = "documents") val documents: List<KakaoBookDocument> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class KakaoBookDocument(
    @Json(name = "title") val title: String? = null,
    @Json(name = "authors") val authors: List<String> = emptyList(),
    @Json(name = "publisher") val publisher: String? = null,
    /** ISO8601 datetime. 예: 2020-02-19T00:00:00.000+09:00 */
    @Json(name = "datetime") val datetime: String? = null,
    /** "ISBN10 ISBN13" 형태로 공백 구분되어 올 수 있음. */
    @Json(name = "isbn") val isbn: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
)
