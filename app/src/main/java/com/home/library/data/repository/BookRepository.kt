package com.home.library.data.repository

import com.home.library.book.BookFormValidator
import com.home.library.data.local.dao.BookDao
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.enums.BookStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 도서 CRUD + 검색.
 * - 물리 삭제 금지: discard는 status=DISCARDED만 (설계 원칙 2).
 * - 삭제 전 대출중(available_qty < total_qty) 검증.
 * - ISBN 중복 판정은 blank가 아닐 때만 수행(빈 ISBN끼리 합쳐지지 않도록).
 * 권한(관리자) 검증은 ViewModel/UI에서 가드한다.
 */
@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
) {

    fun search(keyword: String, category: String?, availableOnly: Boolean): Flow<List<BookEntity>> =
        bookDao.search(keyword.trim(), category, if (availableOnly) 1 else 0)

    fun categories(): Flow<List<String>> = bookDao.getCategories()

    suspend fun getById(bookId: Long): BookEntity? = bookDao.getById(bookId)

    fun flowById(bookId: Long): Flow<BookEntity?> = bookDao.getFlowById(bookId)

    /**
     * ISBN 중복 확인. 하이픈/공백을 제거해 정규화한 뒤 조회한다.
     * blank(=ISBN 없음)면 검사를 건너뛰고 null 반환(신규 등록 유도).
     */
    suspend fun findActiveByIsbn(isbn: String?): BookEntity? {
        val normalized = BookFormValidator.normalizeIsbn(isbn) ?: return null
        return bookDao.getByIsbn(normalized)
    }

    /** 신규 등록. 반환 = 생성된 book_id. */
    suspend fun register(form: BookForm): Long {
        val now = System.currentTimeMillis()
        return bookDao.insert(form.toNewEntity(now))
    }

    /** 중복 ISBN 재등록 → 수량만 증가(서지정보는 수정 화면에서만 변경). */
    suspend fun addQuantity(bookId: Long, qty: Int) {
        bookDao.addQuantity(bookId, qty, System.currentTimeMillis())
    }

    /**
     * 수정. total_qty 변경분(delta)만큼 available_qty도 조정한다.
     * 새 total_qty가 대출중 수량(기존 total-available)보다 작으면 거부.
     */
    suspend fun updateBook(bookId: Long, form: BookForm): UpdateResult {
        val existing = bookDao.getById(bookId) ?: return UpdateResult.NotFound
        val loaned = existing.totalQty - existing.availableQty
        if (form.totalQty < loaned) return UpdateResult.QuantityBelowLoaned(loaned)
        val updated = existing.copy(
            isbn = BookFormValidator.normalizeIsbn(form.isbn),
            title = form.title.trim(),
            author = form.author.blankToNull(),
            publisher = form.publisher.blankToNull(),
            pubDate = form.pubDate.blankToNull(),
            category = form.category.blankToNull(),
            location = form.location.blankToNull(),
            totalQty = form.totalQty,
            availableQty = form.totalQty - loaned,
            updatedAt = System.currentTimeMillis(),
        )
        bookDao.update(updated)
        return UpdateResult.Success
    }

    /** 논리 삭제. 대출중 사본이 있으면(available<total) 거부. */
    suspend fun discard(bookId: Long): DiscardResult {
        val existing = bookDao.getById(bookId) ?: return DiscardResult.NotFound
        if (existing.availableQty < existing.totalQty) return DiscardResult.HasActiveLoans
        bookDao.discard(bookId, System.currentTimeMillis())
        return DiscardResult.Success
    }

    private fun BookForm.toNewEntity(now: Long): BookEntity = BookEntity(
        isbn = BookFormValidator.normalizeIsbn(isbn),
        title = title.trim(),
        author = author.blankToNull(),
        publisher = publisher.blankToNull(),
        pubDate = pubDate.blankToNull(),
        coverUrl = null, // 3단계: 표지 없음(Coil은 4단계)
        category = category.blankToNull(),
        location = location.blankToNull(),
        totalQty = totalQty,
        availableQty = totalQty, // 신규는 전량 가용
        status = BookStatus.AVAILABLE,
        createdAt = now,
        updatedAt = now,
    )

    private fun String?.blankToNull(): String? = this?.trim()?.ifBlank { null }
}

/** 도서 등록/수정 폼 값. 문자열 정규화(trim/blank→null)는 Repository에서 수행. */
data class BookForm(
    val isbn: String?,
    val title: String,
    val author: String?,
    val publisher: String?,
    val pubDate: String?,
    val category: String?,
    val location: String?,
    val totalQty: Int,
)

sealed interface UpdateResult {
    data object Success : UpdateResult
    data class QuantityBelowLoaned(val loaned: Int) : UpdateResult
    data object NotFound : UpdateResult
}

sealed interface DiscardResult {
    data object Success : DiscardResult
    data object HasActiveLoans : DiscardResult
    data object NotFound : DiscardResult
}
