package com.home.library.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "config_key")
    val configKey: String,

    @ColumnInfo(name = "config_value")
    val configValue: String,

    @ColumnInfo(name = "description")
    val description: String? = null,
)
