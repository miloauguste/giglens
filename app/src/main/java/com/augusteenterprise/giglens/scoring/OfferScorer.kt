package com.augusteenterprise.giglens.scoring
// Author: Claude (Anthropic)
// Composite offer scoring engine — reads weights and thresholds from DB via ScorerConfig.
// To retune scoring: update scorer_config rows in the DB, no code change needed.

import com.augusteenterprise.giglens.data.ScorerConfigDao
import com.augusteenterprise.giglens.data.ScorerConfigKeys

enum class Verdict { TAKE, BORDERLINE, SKIP }

data class ScoreResult(
    val score: Int,
    val verdict: Verdict,
    val payPerMile: Double,
    val vsPersonalAvg: Double?,
    val failedFloor: Boolean
)

class OfferScorer(private val configDao: ScorerConfigDao) {

    // ─── Load config from DB, fall back to safe defaults if key missing ────────
    private suspend fun cfg(key: String, default: Double): Double =
        configDao.getValue(key) ?: default

    suspend fun score(
        payAmount: Double?,
        distance: Double?,
        personalAvgScore: Double? = null
    ): ScoreResult? {
        if (payAmount == null || distance == null) return null
        if (payAmount <= 0 || distance <= 0) return null

        // ── Load config from DB ────────────────────────────────────────────────
        val weightPayPerMile = cfg(ScorerConfigKeys.WEIGHT_PAY_PER_MILE, 0.40)
        val weightTotalPay   = cfg(ScorerConfigKeys.WEIGHT_TOTAL_PAY,    0.35)
        val weightDistance   = cfg(ScorerConfigKeys.WEIGHT_DISTANCE,     0.25)

        val payPerMileMin    = cfg(ScorerConfigKeys.PAY_PER_MILE_MIN,    0.50)
        val payPerMileMax    = cfg(ScorerConfigKeys.PAY_PER_MILE_MAX,    3.50)
        val totalPayMin      = cfg(ScorerConfigKeys.TOTAL_PAY_MIN,       2.00)
        val totalPayMax      = cfg(ScorerConfigKeys.TOTAL_PAY_MAX,       20.00)
        val distanceMin      = cfg(ScorerConfigKeys.DISTANCE_MIN,        0.50)
        val distanceMax      = cfg(ScorerConfigKeys.DISTANCE_MAX,        15.00)

        val floorPayPerMile  = cfg(ScorerConfigKeys.FLOOR_PAY_PER_MILE,  1.50)
        val floorTotalPay    = cfg(ScorerConfigKeys.FLOOR_TOTAL_PAY,     6.00)

        val thresholdTake    = cfg(ScorerConfigKeys.THRESHOLD_TAKE,      65.0)
        val thresholdBorder  = cfg(ScorerConfigKeys.THRESHOLD_BORDERLINE,40.0)

        // ── Compute ───────────────────────────────────────────────────────────
        val payPerMile = payAmount / distance

        val normPayPerMile = normalize(payPerMile, payPerMileMin, payPerMileMax)
        val normTotalPay   = normalize(payAmount,  totalPayMin,   totalPayMax)
        val normDistance   = 1.0 - normalize(distance, distanceMin, distanceMax)

        val raw = (normPayPerMile * weightPayPerMile) +
                  (normTotalPay   * weightTotalPay)   +
                  (normDistance   * weightDistance)
        val score = (raw * 100).toInt().coerceIn(0, 100)

        val failedFloor = payPerMile < floorPayPerMile || payAmount < floorTotalPay

        val verdict = when {
            failedFloor || score < thresholdBorder -> Verdict.SKIP
            score < thresholdTake                  -> Verdict.BORDERLINE
            else                                   -> Verdict.TAKE
        }

        val vsPersonalAvg = personalAvgScore?.let {
            if (it > 0) ((score - it) / it) * 100.0 else null
        }

        return ScoreResult(
            score         = score,
            verdict       = verdict,
            payPerMile    = payPerMile,
            vsPersonalAvg = vsPersonalAvg,
            failedFloor   = failedFloor
        )
    }

    private fun normalize(value: Double, min: Double, max: Double): Double {
        if (max <= min) return 0.0
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }
}
