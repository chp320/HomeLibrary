package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.home.library.data.local.entity.BookEntity

@Dao
interface BookDao {

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("SELECT * FROM books WHERE book_id = :bookId LIMIT 1")
    suspend fun getById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE isbn = :isbn AND status != 'DISCARDED' LIMIT 1")
    suspend fun getByIsbn(isbn: String): BookEntity?

    @Query(
        """
        SELECT * FROM books
        WHERE status != 'DISCARDED'
          AND (title LIKE '%' || :keyword || '%' OR author LIKE '%' || :keyword || '%')
        ORDER BY updated_at DESC
        """,
    )
    suspend fun search(keyword: String): List<BookEntity>

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
