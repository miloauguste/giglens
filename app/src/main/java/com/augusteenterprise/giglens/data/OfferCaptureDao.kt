package com.augusteenterprise.giglens.data

// Author: Claude (Anthropic)
// Last modified: DeepSeek (Ollama) - June 02 2026 - Added analytics query methods for MainActivity UI
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
    
    @Query("SELECT AVG(score) FROM (SELECT score FROM offer_captures WHERE score IS NOT NULL ORDER BY timestamp DESC LIMIT 30)")
    suspend fun getAverageScore(): Double?
    
    @Query("DELETE FROM offer_captures WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM offer_captures")
    suspend fun deleteAll()
    
    // ===== NEW METHODS ADDED FOR ANALYTICS =====
    
    // Today stats
    @Query("SELECT COUNT(*) FROM offer_captures WHERE date(timestamp/1000, 'unixepoch') = date('now')")
    suspend fun getTodayCount(): Int?

    @Query("SELECT AVG(payAmount) FROM offer_captures WHERE payAmount IS NOT NULL AND date(timestamp/1000, 'unixepoch') = date('now')")
    suspend fun getTodayAvgPay(): Double?

    @Query("SELECT AVG(CASE WHEN totalDistance > 0 THEN payAmount / totalDistance ELSE NULL END) FROM offer_captures WHERE payAmount IS NOT NULL AND totalDistance > 0 AND date(timestamp/1000, 'unixepoch') = date('now')")
    suspend fun getTodayAvgPayPerMile(): Double?

    // Weekly earnings
    @Query("SELECT SUM(netValue) FROM offer_captures WHERE netValue IS NOT NULL AND strftime('%W', timestamp/1000, 'unixepoch') = strftime('%W', 'now') AND strftime('%Y', timestamp/1000, 'unixepoch') = strftime('%Y', 'now')")
    suspend fun getWeeklyNetEarnings(): Double?

    @Query("SELECT AVG(netValue) FROM offer_captures WHERE netValue IS NOT NULL AND strftime('%W', timestamp/1000, 'unixepoch') = strftime('%W', 'now') AND strftime('%Y', timestamp/1000, 'unixepoch') = strftime('%Y', 'now')")
    suspend fun getWeeklyAvgNetPerOffer(): Double?

    @Query("SELECT AVG(vehicleCost) FROM offer_captures WHERE vehicleCost IS NOT NULL AND strftime('%W', timestamp/1000, 'unixepoch') = strftime('%W', 'now') AND strftime('%Y', timestamp/1000, 'unixepoch') = strftime('%Y', 'now')")
    suspend fun getWeeklyAvgGasCost(): Double?

    @Query("SELECT AVG(truePayPerMile) FROM offer_captures WHERE truePayPerMile IS NOT NULL AND strftime('%W', timestamp/1000, 'unixepoch') = strftime('%W', 'now') AND strftime('%Y', timestamp/1000, 'unixepoch') = strftime('%Y', 'now')")
    suspend fun getWeeklyAvgTruePayPerMile(): Double?

    @Query("SELECT AVG(totalDistance) FROM offer_captures WHERE totalDistance IS NOT NULL AND strftime('%W', timestamp/1000, 'unixepoch') = strftime('%W', 'now') AND strftime('%Y', timestamp/1000, 'unixepoch') = strftime('%Y', 'now')")
    suspend fun getWeeklyAvgDistance(): Double?

    // Verdict breakdown
    @Query("SELECT COUNT(*) FROM offer_captures WHERE verdict = :verdict AND timestamp > (strftime('%s','now') - :days * 86400) * 1000")
    suspend fun getVerdictCount(verdict: String, days: Int): Int?

    // Analytics chart data
    @Query("SELECT date(timestamp/1000, 'unixepoch') as date, AVG(netValue) as avgNet FROM offer_captures WHERE netValue IS NOT NULL AND timestamp > (strftime('%s','now') - :days * 86400) * 1000 GROUP BY date(timestamp/1000, 'unixepoch') ORDER BY date ASC")
    suspend fun getDailyAvgNetValue(days: Int): List<DailyNetValue>

    // Best / avg / worst net value
    @Query("SELECT MAX(netValue) FROM offer_captures WHERE netValue IS NOT NULL AND timestamp > (strftime('%s','now') - :days * 86400) * 1000")
    suspend fun getBestNetValue(days: Int): Double?

    @Query("SELECT AVG(netValue) FROM offer_captures WHERE netValue IS NOT NULL AND timestamp > (strftime('%s','now') - :days * 86400) * 1000")
    suspend fun getAvgNetValue(days: Int): Double?

    @Query("""
        UPDATE offer_captures
        SET confirmedTown = :confirmedTown,
            townAccurate  = :accurate
        WHERE id = :captureId
    """)
    suspend fun updateTownAccuracy(captureId: Long, confirmedTown: String?, accurate: Boolean)

    @Query("SELECT MIN(netValue) FROM offer_captures WHERE netValue IS NOT NULL AND timestamp > (strftime('%s','now') - :days * 86400) * 1000")
    suspend fun getWorstNetValue(days: Int): Double?

    // Recent offers (new method name)
    @Query("SELECT * FROM offer_captures ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentOffers(limit: Int): List<OfferCapture>
}
