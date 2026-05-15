package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic)
// Room entity for DB-driven scorer weights and thresholds.
// One row per key — operator adjusts values here to retune scoring without a code change.

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scorer_config")
data class ScorerConfig(
    @PrimaryKey val key: String,
    val value: Double,
    val description: String = ""
)

// ─── Default config keys ──────────────────────────────────────────────────────
object ScorerConfigKeys {
    // Weights (must sum to 1.0)
    const val WEIGHT_PAY_PER_MILE = "weight_pay_per_mile"
    const val WEIGHT_TOTAL_PAY    = "weight_total_pay"
    const val WEIGHT_DISTANCE     = "weight_distance"

    // Normalization ranges
    const val PAY_PER_MILE_MIN    = "pay_per_mile_min"
    const val PAY_PER_MILE_MAX    = "pay_per_mile_max"
    const val TOTAL_PAY_MIN       = "total_pay_min"
    const val TOTAL_PAY_MAX       = "total_pay_max"
    const val DISTANCE_MIN        = "distance_min"
    const val DISTANCE_MAX        = "distance_max"

    // Benchmark floors
    const val FLOOR_PAY_PER_MILE  = "floor_pay_per_mile"
    const val FLOOR_TOTAL_PAY     = "floor_total_pay"

    // Verdict thresholds
    const val THRESHOLD_TAKE      = "threshold_take"
    const val THRESHOLD_BORDERLINE= "threshold_borderline"
}

// ─── Factory: seed rows inserted on first launch ──────────────────────────────
fun defaultScorerConfig(): List<ScorerConfig> = listOf(
    ScorerConfig(ScorerConfigKeys.WEIGHT_PAY_PER_MILE, 0.40, "Weight: $/mile factor (0.0-1.0)"),
    ScorerConfig(ScorerConfigKeys.WEIGHT_TOTAL_PAY,    0.35, "Weight: total pay factor (0.0-1.0)"),
    ScorerConfig(ScorerConfigKeys.WEIGHT_DISTANCE,     0.25, "Weight: distance penalty factor (0.0-1.0)"),
    ScorerConfig(ScorerConfigKeys.PAY_PER_MILE_MIN,    0.50, "Normalization: worst $/mile (scores 0)"),
    ScorerConfig(ScorerConfigKeys.PAY_PER_MILE_MAX,    3.50, "Normalization: best $/mile (scores 100)"),
    ScorerConfig(ScorerConfigKeys.TOTAL_PAY_MIN,       2.00, "Normalization: lowest pay (scores 0)"),
    ScorerConfig(ScorerConfigKeys.TOTAL_PAY_MAX,       20.00,"Normalization: highest pay (scores 100)"),
    ScorerConfig(ScorerConfigKeys.DISTANCE_MIN,        0.50, "Normalization: shortest distance (scores 100)"),
    ScorerConfig(ScorerConfigKeys.DISTANCE_MAX,        15.00,"Normalization: longest distance (scores 0)"),
    ScorerConfig(ScorerConfigKeys.FLOOR_PAY_PER_MILE,  1.50, "Benchmark floor: min $/mile to pass"),
    ScorerConfig(ScorerConfigKeys.FLOOR_TOTAL_PAY,     6.00, "Benchmark floor: min total pay to pass"),
    ScorerConfig(ScorerConfigKeys.THRESHOLD_TAKE,      65.0, "Verdict: score >= this = TAKE"),
    ScorerConfig(ScorerConfigKeys.THRESHOLD_BORDERLINE,40.0, "Verdict: score >= this = BORDERLINE, below = SKIP")
)
