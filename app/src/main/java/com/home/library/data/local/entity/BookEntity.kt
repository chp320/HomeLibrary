package com.home.library.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.home.library.data.local.enums.BookStatus

@Entity(
    tableName = "books",
    indices = [Index(value = ["isbn"])],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "book_id")
    val bookId: Long = 0,

    @ColumnInfo(name = "isbn")
    val isbn: String? = null,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String? = null,

    @ColumnInfo(name = "publisher")
    val publisher: String? = null,

    @ColumnInfo(name = "pub_date")
    val pubDate: String? = null,

    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "location")
    val location: String? = null,

    @ColumnInfo(name = "total_qty")
    val totalQty: Int,

    @ColumnInfo(name = "available_qty")
    val availableQty: Int,

    @ColumnInfo(name = "status")
    val status: BookStatus,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
