package com.home.library.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus

@Entity(
    tableName = "users",
    indices = [Index(value = ["login_id"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "user_id")
    val userId: Long = 0,

    /** 로그인 ID. unique, 수정 불가. */
    @ColumnInfo(name = "login_id")
    val loginId: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "phone")
    val phone: String? = null,

    @ColumnInfo(name = "role")
    val role: UserRole,

    @ColumnInfo(name = "status")
    val status: UserStatus,

    @ColumnInfo(name = "fail_count")
    val failCount: Int = 0,

    @ColumnInfo(name = "locked_until")
    val lockedUntil: Long? = null,

    @ColumnInfo(name = "pwd_change_required")
    val pwdChangeRequired: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
