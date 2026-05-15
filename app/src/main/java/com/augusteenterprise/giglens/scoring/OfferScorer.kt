package com.augusteenterprise.giglens.scoring
// Author: Claude (Anthropic)
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
    val truePayPerMile: Double?,      // pay / total miles (pickup + delivery)
    val pickupDistance: Double?,
    val totalDistance: Double?,
    // ── Net value (the real money after costs) ─────────────────────────────────
    val vehicleCost: Double,          // total_miles × cost_per_mile
    val netValue: Double,             // offer_pay - vehicle_cost
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
        pickupDistance: Double? = null,
        personalAvgScore: Double? = null
    ): ScoreResult? {
        if (payAmount == null || deliveryDistance == null) return null
        if (payAmount <= 0 || deliveryDistance <= 0) return null

        // ── Load config ───────────────────────────────────────────────────────
        val costPerMile        = cfg(ScorerConfigKeys.COST_PER_MILE,             0.90)

        val weightNetValue     = cfg(ScorerConfigKeys.WEIGHT_PICKUP_PENALTY,     0.50) // reused key
        val weightPickup       = cfg(ScorerConfigKeys.WEIGHT_DELIVERY_LEG,       0.30) // reused key
        val weightTruePerMile  = cfg(ScorerConfigKeys.WEIGHT_TRUE_PAY_PER_MILE,  0.20)

        val pickupMin          = cfg(ScorerConfigKeys.PICKUP_DISTANCE_MIN,       0.0)
        val pickupMax          = cfg(ScorerConfigKeys.PICKUP_DISTANCE_MAX,       8.0)
        val truePerMileMin     = cfg(ScorerConfigKeys.TRUE_PAY_PER_MILE_MIN,     0.50)
        val truePerMileMax     = cfg(ScorerConfigKeys.TRUE_PAY_PER_MILE_MAX,     3.00)
        val totalPayMin        = cfg(ScorerConfigKeys.TOTAL_PAY_MIN,             2.00)
        val totalPayMax        = cfg(ScorerConfigKeys.TOTAL_PAY_MAX,             20.00)

        val floorPayPerMile    = cfg(ScorerConfigKeys.FLOOR_PAY_PER_MILE,        1.50)
        val floorTotalPay      = cfg(ScorerConfigKeys.FLOOR_TOTAL_PAY,           6.00)
        val thresholdTake      = cfg(ScorerConfigKeys.THRESHOLD_TAKE,            65.0)
        val thresholdBorder    = cfg(ScorerConfigKeys.THRESHOLD_BORDERLINE,      40.0)

        // ── Compute distances ─────────────────────────────────────────────────
        val totalDistance = if (pickupDistance != null) {
            pickupDistance + deliveryDistance
        } else deliveryDistance  // fall back to delivery only

        // ── Net value calculation ─────────────────────────────────────────────
        val vehicleCost    = totalDistance * costPerMile
        val netValue       = payAmount - vehicleCost
        val payPerMile     = payAmount / deliveryDistance
        val truePayPerMile = payAmount / totalDistance

        // Net value normalized: $0 net = 0, $15+ net = 100
        val netValueMin = 0.0
        val netValueMax = 15.0
        val normNetValue = normalize(netValue, netValueMin, netValueMax)

        // Pickup penalty: shorter = better (invert)
        val normPickup = if (pickupDistance != null) {
            1.0 - normalize(pickupDistance, pickupMin, pickupMax)
        } else 0.75  // neutral assumption if GPS unavailable

        // True $/mile after full trip
        val normTruePerMile = normalize(truePayPerMile, truePerMileMin, truePerMileMax)

        // ── Weighted composite ────────────────────────────────────────────────
        val raw = (normNetValue    * weightNetValue)    +
                  (normPickup      * weightPickup)      +
                  (normTruePerMile * weightTruePerMile)
        val score = (raw * 100).toInt().coerceIn(0, 100)

        // ── Floor check (net value must be positive to pass) ──────────────────
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
            pickupDistance = pickupDistance,
            totalDistance  = totalDistance,
            vehicleCost    = vehicleCost,
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
