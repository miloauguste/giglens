package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic)
// DAO for scorer_config table

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ScorerConfigDao {

    @Query("SELECT * FROM scorer_config")
    suspend fun getAll(): List<ScorerConfig>

    @Query("SELECT value FROM scorer_config WHERE `key` = :key")
    suspend fun getValue(key: String): Double?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<ScorerConfig>)

    @Update
    suspend fun update(config: ScorerConfig)

    @Query("UPDATE scorer_config SET value = :value WHERE `key` = :key")
    suspend fun updateValue(key: String, value: Double)

    @Query("SELECT COUNT(*) FROM scorer_config")
    suspend fun count(): Int
}
