package com.home.library.data.local.converter

import androidx.room.TypeConverter
import com.home.library.data.local.enums.BookStatus
import com.home.library.data.local.enums.LoanAction
import com.home.library.data.local.enums.LoanStatus
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus

/**
 * enum ↔ name(String) 변환 (설계 원칙 1: ordinal 저장 금지).
 * 날짜/시각은 Long(epoch millis)으로 엔티티에 직접 저장하므로 별도 컨버터가 필요 없다.
 */
class Converters {

    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromUserStatus(value: UserStatus): String = value.name

    @TypeConverter
    fun toUserStatus(value: String): UserStatus = UserStatus.valueOf(value)

    @TypeConverter
    fun fromBookStatus(value: BookStatus): String = value.name

    @TypeConverter
    fun toBookStatus(value: String): BookStatus = BookStatus.valueOf(value)

    @TypeConverter
    fun fromLoanStatus(value: LoanStatus): String = value.name

    @TypeConverter
    fun toLoanStatus(value: String): LoanStatus = LoanStatus.valueOf(value)

    @TypeConverter
    fun fromLoanAction(value: LoanAction): String = value.name

    @TypeConverter
    fun toLoanAction(value: String): LoanAction = LoanAction.valueOf(value)
}
