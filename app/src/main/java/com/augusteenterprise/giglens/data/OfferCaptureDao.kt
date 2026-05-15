package com.augusteenterprise.giglens.data

// Author: Claude (Anthropic)
// Data Access Object for offer capture CRUD operations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OfferCaptureDao {

    @Insert
    suspend fun insert(capture: OfferCapture): Long

    @Query("SELECT * FROM offer_captures ORDER BY timestamp DESC")
    suspend fun getAll(): List<OfferCapture>

    @Query("SELECT * FROM offer_captures ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<OfferCapture>

    @Query("SELECT * FROM offer_captures WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByDateRange(startTime: Long, endTime: Long): List<OfferCapture>

    @Query("SELECT COUNT(*) FROM offer_captures")
    suspend fun getCount(): Int

    @Query("SELECT AVG(payAmount) FROM offer_captures WHERE payAmount IS NOT NULL")
    suspend fun getAveragePay(): Double?

    @Query("SELECT AVG(CASE WHEN distance > 0 THEN payAmount / distance ELSE NULL END) FROM offer_captures WHERE payAmount IS NOT NULL AND distance IS NOT NULL AND distance > 0")
    suspend fun getAveragePayPerMile(): Double?

    @Query("DELETE FROM offer_captures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM offer_captures")
    suspend fun deleteAll()
}
