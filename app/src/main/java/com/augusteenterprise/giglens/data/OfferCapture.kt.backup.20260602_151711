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
    val distance: Double? = null,           // delivery leg: restaurant → customer (from OCR)
    val distanceUnit: String = "mi",
    val restaurant: String? = null,
    val screenshotPath: String? = null,
    val rawOcrText: String? = null,
    val accepted: Boolean? = null,
    // ─── Scoring fields (v2) ──────────────────────────────────────────────────
    val score: Int? = null,
    val verdict: String? = null,
    val payPerMile: Double? = null,
    val vsPersonalAvg: Double? = null,
    // ─── Location/distance fields (v3) ────────────────────────────────────────
    val driverLat: Double? = null,          // driver GPS at capture time
    val driverLon: Double? = null,
    val pickupDistance: Double? = null,     // driver → restaurant (miles)
    val deliveryDistance: Double? = null,   // restaurant → customer (miles, renamed from distance)
    val totalDistance: Double? = null,      // pickup + delivery combined
    val truePayPerMile: Double? = null,     // pay / totalDistance
    val vehicleCost: Double? = null,        // totalDistance × cost_per_mile
    val netValue: Double? = null,           // offer_pay - vehicle_cost
    val estimatedMinutes: Int? = null       // estimated trip duration
)
