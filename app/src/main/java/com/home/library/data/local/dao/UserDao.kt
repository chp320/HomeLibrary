package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.home.library.data.local.entity.UserEntity
import com.home.library.data.local.view.UserListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE login_id = :loginId LIMIT 1")
    suspend fun getByLoginId(loginId: String): UserEntity?

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    suspend fun getById(userId: Long): UserEntity?

    @Query("SELECT * FROM users ORDER BY created_at DESC")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    /**
     * 사용자 관리 목록. 이름/로그인ID 부분검색 + 권한/상태 필터 + 대출중 권수. USER-004.
     * role/status는 enum name(String) 또는 null(필터 미적용)로 넘긴다.
     */
    @Query(
        """
        SELECT u.user_id AS userId, u.login_id AS loginId, u.name AS name, u.phone AS phone,
               u.role AS role, u.status AS status,
               (SELECT COUNT(*) FROM loans l
                WHERE l.user_id = u.user_id AND l.status IN ('LOANED', 'OVERDUE')) AS activeCount
        FROM users u
        WHERE (:query = '' OR u.name LIKE '%' || :query || '%' OR u.login_id LIKE '%' || :query || '%')
          AND (:role IS NULL OR u.role = :role)
          AND (:status IS NULL OR u.status = :status)
        ORDER BY u.created_at DESC
        """,
    )
    fun searchUsers(query: String, role: String?, status: String?): Flow<List<UserListItem>>
}
