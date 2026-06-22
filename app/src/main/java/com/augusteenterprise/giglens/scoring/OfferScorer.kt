package com.augusteenterprise.giglens.scoring
// Author: Claude (Anthropic) - v5: profit-% scoring replaces composite score
// Verdict is driven by a single metric: what fraction of the offer pay the driver
// keeps after vehicle costs (gas + wear/tear). Driver configures GREEN/YELLOW/RED
// thresholds in Settings. Floors on min total pay and min $/mile are preserved.
//
// Formula: profitPct = (netValue / payAmount) × 100
//   where netValue = payAmount − (gasCost + wearTearCost)

import com.augusteenterprise.giglens.data.ScorerConfigDao
import com.augusteenterprise.giglens.data.ScorerConfigKeys

enum class Verdict { TAKE, BORDERLINE, SKIP }

data class ScoreResult(
    val score: Int,                // = profitPct.toInt() — stored in DB for history
    val profitPct: Double,         // profit as % of offer pay — the primary verdict signal
    val verdict: Verdict,
    // ── Gross metrics ──────────────────────────────────────────────────────────
    val payPerMile: Double,        // pay / delivery miles
    val truePayPerMile: Double,    // pay / total miles
    val totalDistance: Double,
    // ── Net value (the real money after costs) ─────────────────────────────────
    val vehicleCost: Double,       // gasCost + wearTearCost combined
    val gasCost: Double,           // (total_miles / mpg) × gas_price
    val wearTearCost: Double,      // total_miles × wear_tear_per_mile
    val timeCost: Double,          // always 0.0 — kept for API compat
    val totalCost: Double,         // vehicleCost (gas + wear/tear)
    val minutesOnJob: Double,      // estimated minutes for the full trip
    val netValue: Double,          // offer_pay - totalCost
    val costPerMileUsed: Double,   // rate applied ($0.90 default fallback)
    // ── Context ────────────────────────────────────────────────────────────────
    val vsPersonalAvg: Double?,    // (profitPct - avgProfitPct) / avgProfitPct × 100
    val failedFloor: Boolean
)

class OfferScorer(private val configDao: ScorerConfigDao) {

    private suspend fun cfg(key: String, default: Double): Double =
        configDao.getValue(key) ?: default

    suspend fun score(
        payAmount: Double?,
        deliveryDistance: Double?,
        personalAvgScore: Double? = null
    ): ScoreResult? {
        if (payAmount == null || deliveryDistance == null) return null
        if (payAmount <= 0 || deliveryDistance <= 0) return null

        // ── Load config ────────────────────────────────────────────────────────
        val costPerMile     = cfg(ScorerConfigKeys.COST_PER_MILE,          0.90)
        val mpg             = cfg(ScorerConfigKeys.MPG,                    30.0)
        val gasPrice        = cfg(ScorerConfigKeys.GAS_PRICE,               3.20)
        val wearTearPerMile = cfg(ScorerConfigKeys.WEAR_TEAR_PER_MILE,     0.13)
        val floorPayPerMile = cfg(ScorerConfigKeys.FLOOR_PAY_PER_MILE,     1.50)
        val floorTotalPay   = cfg(ScorerConfigKeys.FLOOR_TOTAL_PAY,        6.00)
        val thresholdGreen  = cfg(ScorerConfigKeys.THRESHOLD_GREEN_PCT,   75.0)
        val thresholdYellow = cfg(ScorerConfigKeys.THRESHOLD_YELLOW_PCT,  50.0)

        val totalDistance = deliveryDistance

        // ── Vehicle cost (gas + wear/tear) ─────────────────────────────────────
        val gasCost = if (mpg > 0) (totalDistance / mpg) * gasPrice
                      else totalDistance * costPerMile
        val wearTearCost = Math.round(totalDistance * wearTearPerMile * 100.0) / 100.0
        val vehicleCost  = gasCost + wearTearCost
        val totalCost    = vehicleCost
        val netValue     = payAmount - vehicleCost

        val minutesOnJob   = (totalDistance / 45.0) * 60.0 + 2.0
        val timeCost       = 0.0
        val payPerMile     = payAmount / deliveryDistance
        val truePayPerMile = payAmount / totalDistance

        // ── Profit % — the primary verdict signal ─────────────────────────────
        val profitPct = (netValue / payAmount) * 100.0

        // ── Floor check ────────────────────────────────────────────────────────
        val failedFloor = netValue <= 0.0 ||
                          payPerMile < floorPayPerMile ||
                          payAmount  < floorTotalPay

        // ── Verdict ────────────────────────────────────────────────────────────
        val verdict = when {
            failedFloor                  -> Verdict.SKIP
            profitPct >= thresholdGreen  -> Verdict.TAKE
            profitPct >= thresholdYellow -> Verdict.BORDERLINE
            else                         -> Verdict.SKIP
        }

        // Store profit% as score (0–100 clamped) for DB / historical compat
        val score = profitPct.toInt().coerceIn(0, 100)

        val vsPersonalAvg = personalAvgScore?.let {
            if (it > 0) ((profitPct - it) / it) * 100.0 else null
        }

        return ScoreResult(
            score          = score,
            profitPct      = profitPct,
            verdict        = verdict,
            payPerMile     = payPerMile,
            truePayPerMile = truePayPerMile,
            totalDistance  = totalDistance,
            vehicleCost    = vehicleCost,
            gasCost        = gasCost,
            wearTearCost   = wearTearCost,
            timeCost       = timeCost,
            totalCost      = totalCost,
            minutesOnJob   = minutesOnJob,
            netValue       = netValue,
            costPerMileUsed= costPerMile,
            vsPersonalAvg  = vsPersonalAvg,
            failedFloor    = failedFloor
        )
    }
}
