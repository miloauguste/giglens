package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic) - Added HOURLY_RATE key and default value (Issue #3)
// Room entity for DB-driven scorer weights and thresholds.

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scorer_config")
data class ScorerConfig(
    @PrimaryKey val key: String,
    val value: Double,
    val description: String = ""
)

object ScorerConfigKeys {
    // Legacy weights (v2 — kept for reference)
    const val WEIGHT_PAY_PER_MILE     = "weight_pay_per_mile"
    const val WEIGHT_TOTAL_PAY        = "weight_total_pay"
    const val WEIGHT_DISTANCE         = "weight_distance"

    // v3 weights (4-factor)
    const val WEIGHT_PICKUP_PENALTY   = "weight_pickup_penalty"
    const val WEIGHT_TRUE_PAY_PER_MILE= "weight_true_pay_per_mile"
    const val WEIGHT_TOTAL_PAY_V2     = "weight_total_pay_v2"
    const val WEIGHT_DELIVERY_LEG     = "weight_delivery_leg"

    // Normalization ranges
    const val PAY_PER_MILE_MIN        = "pay_per_mile_min"
    const val PAY_PER_MILE_MAX        = "pay_per_mile_max"
    const val TOTAL_PAY_MIN           = "total_pay_min"
    const val TOTAL_PAY_MAX           = "total_pay_max"
    const val DISTANCE_MIN            = "distance_min"
    const val DISTANCE_MAX            = "distance_max"
    const val PICKUP_DISTANCE_MIN     = "pickup_distance_min"
    const val PICKUP_DISTANCE_MAX     = "pickup_distance_max"
    const val TRUE_PAY_PER_MILE_MIN   = "true_pay_per_mile_min"
    const val TRUE_PAY_PER_MILE_MAX   = "true_pay_per_mile_max"

    // Floors and thresholds
    const val FLOOR_PAY_PER_MILE      = "floor_pay_per_mile"
    const val FLOOR_TOTAL_PAY         = "floor_total_pay"
    const val THRESHOLD_TAKE          = "threshold_take"
    const val THRESHOLD_BORDERLINE    = "threshold_borderline"
    const val COST_PER_MILE           = "cost_per_mile"
    const val HOURLY_RATE             = "hourly_rate"
    const val RESULT_DISPLAY_SECONDS  = "result_display_seconds"
    // Gas-based vehicle cost (replaces flat cost_per_mile)
    const val MPG                     = "mpg"
    const val GAS_PRICE               = "gas_price_per_gallon"
}

fun defaultScorerConfig(): List<ScorerConfig> = listOf(
    // v2 legacy weights
    ScorerConfig(ScorerConfigKeys.WEIGHT_PAY_PER_MILE,      0.40, "Legacy weight: $/mile"),
    ScorerConfig(ScorerConfigKeys.WEIGHT_TOTAL_PAY,         0.35, "Legacy weight: total pay"),
    ScorerConfig(ScorerConfigKeys.WEIGHT_DISTANCE,          0.25, "Legacy weight: distance penalty"),
    // v3 4-factor weights
    ScorerConfig(ScorerConfigKeys.WEIGHT_PICKUP_PENALTY,    0.50, "Weight: pickup leg (shorter = better)"),
    ScorerConfig(ScorerConfigKeys.WEIGHT_TRUE_PAY_PER_MILE, 0.20, "Weight: pay / total miles"),
    ScorerConfig(ScorerConfigKeys.WEIGHT_TOTAL_PAY_V2,      0.20, "Weight: total pay amount"),
    ScorerConfig(ScorerConfigKeys.WEIGHT_DELIVERY_LEG,      0.10, "Weight: delivery leg (shorter = slightly better)"),
    // Normalization ranges
    ScorerConfig(ScorerConfigKeys.PAY_PER_MILE_MIN,         0.50, "Worst $/mile delivery-only"),
    ScorerConfig(ScorerConfigKeys.PAY_PER_MILE_MAX,         3.50, "Best $/mile delivery-only"),
    ScorerConfig(ScorerConfigKeys.TOTAL_PAY_MIN,            2.00, "Lowest pay (scores 0)"),
    ScorerConfig(ScorerConfigKeys.TOTAL_PAY_MAX,            20.00,"Highest pay (scores 100)"),
    ScorerConfig(ScorerConfigKeys.DISTANCE_MIN,             0.50, "Shortest delivery leg (scores 100)"),
    ScorerConfig(ScorerConfigKeys.DISTANCE_MAX,             15.00,"Longest delivery leg (scores 0)"),
    ScorerConfig(ScorerConfigKeys.PICKUP_DISTANCE_MIN,      0.0,  "Shortest pickup (scores 100)"),
    ScorerConfig(ScorerConfigKeys.PICKUP_DISTANCE_MAX,      8.0,  "Longest pickup (scores 0)"),
    ScorerConfig(ScorerConfigKeys.TRUE_PAY_PER_MILE_MIN,    0.50, "Worst true $/mile (total trip)"),
    ScorerConfig(ScorerConfigKeys.TRUE_PAY_PER_MILE_MAX,    3.00, "Best true $/mile (total trip)"),
    // Floors and thresholds
    ScorerConfig(ScorerConfigKeys.FLOOR_PAY_PER_MILE,       1.50, "Min $/mile to pass floor"),
    ScorerConfig(ScorerConfigKeys.FLOOR_TOTAL_PAY,          6.00, "Min total pay to pass floor"),
    ScorerConfig(ScorerConfigKeys.THRESHOLD_TAKE,           65.0, "Score >= this = TAKE"),
    ScorerConfig(ScorerConfigKeys.THRESHOLD_BORDERLINE,     40.0, "Score >= this = BORDERLINE"),
    ScorerConfig(ScorerConfigKeys.COST_PER_MILE,            0.90, "Vehicle cost per mile driven (IRS standard $0.90) — fallback only"),
    ScorerConfig(ScorerConfigKeys.MPG,                      30.0, "Vehicle fuel economy in MPG — look up at fueleconomy.gov"),
    ScorerConfig(ScorerConfigKeys.GAS_PRICE,                3.20, "Current gas price per gallon in your area"),
    ScorerConfig(ScorerConfigKeys.HOURLY_RATE,              15.00, "Driver hourly rate for time cost calculation"),
    ScorerConfig(ScorerConfigKeys.RESULT_DISPLAY_SECONDS,   60.0,  "Seconds to show result pill before reverting to idle")
)
