package com.home.library.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.home.library.data.local.entity.AppConfigEntity

@Dao
interface AppConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AppConfigEntity)

    @Query("SELECT config_value FROM app_config WHERE config_key = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM app_config WHERE config_key = :key LIMIT 1")
    suspend fun getByKey(key: String): AppConfigEntity?

    @Query("SELECT * FROM app_config")
    suspend fun getAll(): List<AppConfigEntity>
}
