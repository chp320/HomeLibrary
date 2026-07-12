package com.home.library.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.home.library.data.local.converter.Converters
import com.home.library.data.local.dao.AppConfigDao
import com.home.library.data.local.dao.BookDao
import com.home.library.data.local.dao.LoanDao
import com.home.library.data.local.dao.LoanHistoryDao
import com.home.library.data.local.dao.UserDao
import com.home.library.data.local.entity.AppConfigEntity
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.entity.LoanEntity
import com.home.library.data.local.entity.LoanHistoryEntity
import com.home.library.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        BookEntity::class,
        LoanEntity::class,
        LoanHistoryEntity::class,
        AppConfigEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun bookDao(): BookDao
    abstract fun loanDao(): LoanDao
    abstract fun loanHistoryDao(): LoanHistoryDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        const val DB_NAME = "home_library.db"
    }
}
