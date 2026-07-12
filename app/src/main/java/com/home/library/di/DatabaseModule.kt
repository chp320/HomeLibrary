package com.home.library.di

import android.content.Context
import androidx.room.Room
import com.home.library.data.local.AppDatabase
import com.home.library.data.local.SeedCallback
import com.home.library.data.local.dao.AppConfigDao
import com.home.library.data.local.dao.BookDao
import com.home.library.data.local.dao.LoanDao
import com.home.library.data.local.dao.LoanHistoryDao
import com.home.library.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // fallbackToDestructiveMigration 금지 (설계 원칙 3). 스키마 변경 시 Migration 작성.
            .addCallback(SeedCallback())
            .build()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun provideLoanDao(db: AppDatabase): LoanDao = db.loanDao()

    @Provides
    fun provideLoanHistoryDao(db: AppDatabase): LoanHistoryDao = db.loanHistoryDao()

    @Provides
    fun provideAppConfigDao(db: AppDatabase): AppConfigDao = db.appConfigDao()
}
