package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.home.library.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("SELECT * FROM books WHERE book_id = :bookId LIMIT 1")
    suspend fun getById(bookId: Long): BookEntity?

    /** 상세 화면용. 수정 시 자동 갱신되도록 Flow 반환. */
    @Query("SELECT * FROM books WHERE book_id = :bookId LIMIT 1")
    fun getFlowById(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE isbn = :isbn AND status != 'DISCARDED' LIMIT 1")
    suspend fun getByIsbn(isbn: String): BookEntity?

    /**
     * 도서 검색 (BOOK-07).
     * - keyword: 제목/저자/출판사/ISBN 부분일치. 빈 문자열이면 전체.
     * - category: null이면 분류 필터 미적용.
     * - availableOnly: 1이면 available_qty > 0 만.
     * DISCARDED 제외. Flow 반환이라 books 테이블 변경 시 자동 갱신된다.
     */
    @Query(
        """
        SELECT * FROM books
        WHERE status != 'DISCARDED'
          AND (
            :keyword = ''
            OR title LIKE '%' || :keyword || '%'
            OR author LIKE '%' || :keyword || '%'
            OR publisher LIKE '%' || :keyword || '%'
            OR isbn LIKE '%' || :keyword || '%'
          )
          AND (:category IS NULL OR category = :category)
          AND (:availableOnly = 0 OR available_qty > 0)
        ORDER BY updated_at DESC
        """,
    )
    fun search(keyword: String, category: String?, availableOnly: Int): Flow<List<BookEntity>>

    /** 분류 필터 후보. NULL/빈 값 제외, DISCARDED 제외. */
    @Query(
        """
        SELECT DISTINCT category FROM books
        WHERE status != 'DISCARDED' AND category IS NOT NULL AND category != ''
        ORDER BY category
        """,
    )
    fun getCategories(): Flow<List<String>>

    /** 논리 삭제 (설계 원칙 2). status=DISCARDED 로만 전환. */
    @Query("UPDATE books SET status = 'DISCARDED', updated_at = :now WHERE book_id = :bookId")
    suspend fun discard(bookId: Long, now: Long): Int

    /** 중복 ISBN 재등록 시 수량 증가. total_qty/available_qty를 함께 늘린다. */
    @Query(
        """
        UPDATE books
        SET total_qty = total_qty + :qty,
            available_qty = available_qty + :qty,
            updated_at = :now
        WHERE book_id = :bookId
        """,
    )
    suspend fun addQuantity(bookId: Long, qty: Int, now: Long): Int

    /**
     * 가용수량 감소. available_qty > 0 일 때만 반영 (설계 원칙 5).
     * @return 영향받은 행 수. 0이면 실패로 판정하고 롤백한다.
     */
    @Query(
        """
        UPDATE books SET available_qty = available_qty - 1, updated_at = :now
        WHERE book_id = :bookId AND available_qty > 0
        """,
    )
    suspend fun decreaseAvailable(bookId: Long, now: Long): Int

    /**
     * 가용수량 증가. available_qty < total_qty 일 때만 반영 (설계 원칙 5).
     * @return 영향받은 행 수. 0이면 실패로 판정하고 롤백한다.
     */
    @Query(
        """
        UPDATE books SET available_qty = available_qty + 1, updated_at = :now
        WHERE book_id = :bookId AND available_qty < total_qty
        """,
    )
    suspend fun increaseAvailable(bookId: Long, now: Long): Int
}
