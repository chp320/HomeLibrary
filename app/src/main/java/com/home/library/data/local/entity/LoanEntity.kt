package com.home.library.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.home.library.data.local.enums.LoanStatus

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
        ),
    ],
    indices = [Index("book_id"), Index("user_id")],
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "loan_id")
    val loanId: Long = 0,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "loan_date")
    val loanDate: Long,

    @ColumnInfo(name = "due_date")
    val dueDate: Long,

    @ColumnInfo(name = "return_date")
    val returnDate: Long? = null,

    @ColumnInfo(name = "status")
    val status: LoanStatus,

    @ColumnInfo(name = "extend_count")
    val extendCount: Int = 0,
)
