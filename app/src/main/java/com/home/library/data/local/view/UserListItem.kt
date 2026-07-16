package com.home.library.data.local.view

import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus

/** 사용자 관리 목록 항목(대출중 권수 포함). USER-004. */
data class UserListItem(
    val userId: Long,
    val loginId: String,
    val name: String,
    val phone: String?,
    val role: UserRole,
    val status: UserStatus,
    val activeCount: Int,
)
