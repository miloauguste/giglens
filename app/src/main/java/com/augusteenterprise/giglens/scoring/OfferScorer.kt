package com.augusteenterprise.giglens.scoring
// Author: Claude (Anthropic) - Added timeCost, totalCost, minutesOnJob to ScoreResult (Bug A/B fix)
// Predictive net value scorer.
// Estimates what an offer is truly worth after vehicle costs before driver accepts.
// Formula: net_value = offer_pay - (total_miles × cost_per_mile)
// Score weights: net_value (50%), pickup penalty (30%), true $/mile (20%)

import com.augusteenterprise.giglens.data.ScorerConfigDao
import com.augusteenterprise.giglens.data.ScorerConfigKeys

enum class Verdict { TAKE, BORDERLINE, SKIP }

data class ScoreResult(
    val score: Int,
    val verdict: Verdict,
    // ── Gross metrics ──────────────────────────────────────────────────────────
    val payPerMile: Double,           // pay / delivery miles only
    val truePayPerMile: Double,       // pay / total miles
    val totalDistance: Double,
    // ── Net value (the real money after costs) ─────────────────────────────────
    val vehicleCost: Double,          // gasCost + wearTearCost combined
    val gasCost: Double,              // (total_miles / mpg) × gas_price
    val wearTearCost: Double,         // total_miles × wear_tear_per_mile
    val timeCost: Double,             // always 0.0 in v4 — kept for API compat
    val totalCost: Double,            // vehicleCost (gas + wear/tear)
    val minutesOnJob: Double,         // estimated minutes for the full trip
    val netValue: Double,             // offer_pay - totalCost
    val costPerMileUsed: Double,      // what rate was applied ($0.90 default)
    // ── Context ────────────────────────────────────────────────────────────────
    val vsPersonalAvg: Double?,
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

        // ── Load config (v4 — gas + wear/tear only, no hourly rate) ─────────────
        // CORRECT: vehicle cost = gas + wear/tear only — no time cost for gig drivers
        // WRONG: including hourly rate — drivers are out regardless, time cost is sunk
        val costPerMile        = cfg(ScorerConfigKeys.COST_PER_MILE,             0.90)
        val mpg                = cfg(ScorerConfigKeys.MPG,                        30.0)
        val gasPrice           = cfg(ScorerConfigKeys.GAS_PRICE,                  3.20)
        val wearTearPerMile    = cfg(ScorerConfigKeys.WEAR_TEAR_PER_MILE,         0.13)

        // v4 simplified weights: net value 70%, true $/mile 30%
        val weightNetValue     = cfg(ScorerConfigKeys.WEIGHT_NET_VALUE,           0.70)
        val weightTruePerMile  = cfg(ScorerConfigKeys.WEIGHT_TRUE_PER_MILE_V4,   0.30)

        val truePerMileMin     = cfg(ScorerConfigKeys.TRUE_PAY_PER_MILE_MIN,     0.50)
        val truePerMileMax     = cfg(ScorerConfigKeys.TRUE_PAY_PER_MILE_MAX,     3.00)
        val floorPayPerMile    = cfg(ScorerConfigKeys.FLOOR_PAY_PER_MILE,        1.50)
        val floorTotalPay      = cfg(ScorerConfigKeys.FLOOR_TOTAL_PAY,           6.00)
        val thresholdTake      = cfg(ScorerConfigKeys.THRESHOLD_TAKE,            65.0)
        val thresholdBorder    = cfg(ScorerConfigKeys.THRESHOLD_BORDERLINE,      40.0)

        // ── Compute distances ─────────────────────────────────────────────────
        // CORRECT: DoorDash distance = full trip (driver → pickup → dropoff) — use directly
        // WRONG: adding estimated pickup leg — DoorDash already includes it
        val totalDistance = deliveryDistance

        // ── Net value calculation (v4 — gas + wear/tear only) ────────────────
        // CORRECT: gas_cost = (total_miles / mpg) × gas_price — actual fuel cost
        // CORRECT: wear_tear = total_miles × wear_tear_per_mile — mechanical depreciation
        // WRONG:   including time/hourly cost — gig drivers are out regardless
        val gasCost = if (mpg > 0) {
            (totalDistance / mpg) * gasPrice
        } else {
            totalDistance * costPerMile  // fallback to flat rate if MPG not set
        }
        val wearTearCost      = totalDistance * wearTearPerMile
        val vehicleCost       = gasCost + wearTearCost
        // Estimate minutes for display purposes only — not used in cost calc
        val minutesOnJob = (totalDistance / 45.0) * 60.0 + 2.0  // drive time + 2min prep
        val timeCost          = 0.0  // removed from v4 — not a real out-of-pocket cost
        val totalCost         = vehicleCost
        val netValue          = payAmount - vehicleCost
        val payPerMile     = payAmount / deliveryDistance
        val truePayPerMile = payAmount / totalDistance

        // ── v4 simplified scoring: net value (70%) + true $/mile (30%) ─────────
        // CORRECT: two signals only — net dollars + efficiency
        // WRONG: pickup penalty as separate signal — already captured in net value via gas cost
        val netValueMin = 0.0
        val netValueMax = 15.0
        val normNetValue = normalize(netValue, netValueMin, netValueMax)

        // True $/mile after full trip
        val normTruePerMile = normalize(truePayPerMile, truePerMileMin, truePerMileMax)

        // ── Weighted composite ────────────────────────────────────────────────
        val raw = (normNetValue    * weightNetValue)    +
                  (normTruePerMile * weightTruePerMile)
        val score = (raw * 100).toInt().coerceIn(0, 100)

        // ── Floor check (net value must be positive to pass) ──────────────────
        // Floor: net after ALL costs (vehicle + time) must be positive
        val failedFloor = netValue <= 0.0 ||
                          payPerMile < floorPayPerMile ||
                          payAmount < floorTotalPay

        // ── Verdict ───────────────────────────────────────────────────────────
        val verdict = when {
            failedFloor || score < thresholdBorder -> Verdict.SKIP
            score < thresholdTake                  -> Verdict.BORDERLINE
            else                                   -> Verdict.TAKE
        }

        val vsPersonalAvg = personalAvgScore?.let {
            if (it > 0) ((score - it) / it) * 100.0 else null
        }

        return ScoreResult(
            score          = score,
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

    private fun normalize(value: Double, min: Double, max: Double): Double {
        if (max <= min) return 0.0
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }
}
