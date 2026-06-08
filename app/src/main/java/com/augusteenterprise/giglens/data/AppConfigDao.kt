package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic)
// DAO for app_config string settings table

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppConfigDao {

    @Query("SELECT value FROM app_config WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM app_config")
    suspend fun getAll(): List<AppConfig>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<AppConfig>)

    @Query("INSERT INTO app_config (key, value, description) VALUES (:key, :value, '') ON CONFLICT(key) DO UPDATE SET value = :value")
    suspend fun setValue(key: String, value: String)

    @Query("SELECT COUNT(*) FROM app_config")
    suspend fun count(): Int
}
