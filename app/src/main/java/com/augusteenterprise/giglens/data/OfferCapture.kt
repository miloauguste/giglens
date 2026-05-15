package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic)
// Room entity representing a single captured delivery offer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offer_captures")
data class OfferCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val platform: String = "DoorDash",
    val payAmount: Double? = null,
    val distance: Double? = null,
    val distanceUnit: String = "mi",
    val restaurant: String? = null,
    val screenshotPath: String? = null,
    val rawOcrText: String? = null,
    val accepted: Boolean? = null,
    // ─── Scoring fields (added v2) ────────────────────────────────────────────
    val score: Int? = null,              // 0–100 composite score
    val verdict: String? = null,         // TAKE / BORDERLINE / SKIP
    val payPerMile: Double? = null,      // computed $/mile at capture time
    val vsPersonalAvg: Double? = null    // % above/below driver's rolling avg
)
