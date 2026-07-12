package com.home.library.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.home.library.data.local.enums.LoanAction

/** 대출 이력. append-only (설계 원칙 6). */
@Entity(
    tableName = "loan_history",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["loan_id"],
            childColumns = ["loan_id"],
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["actor_id"],
        ),
    ],
    indices = [Index("loan_id"), Index("actor_id")],
)
data class LoanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "history_id")
    val historyId: Long = 0,

    @ColumnInfo(name = "loan_id")
    val loanId: Long,

    @ColumnInfo(name = "action")
    val action: LoanAction,

    @ColumnInfo(name = "action_at")
    val actionAt: Long,

    @ColumnInfo(name = "actor_id")
    val actorId: Long,

    @ColumnInfo(name = "memo")
    val memo: String? = null,
)
