package com.augusteenterprise.giglens.data

// Author: Claude (Anthropic)
// Last modified: DeepSeek (Ollama) - June 02 2026 - Added timeCost + minutesOnJob fields
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
    val score: Int? = null,
    val verdict: String? = null,
    val payPerMile: Double? = null,
    val vsPersonalAvg: Double? = null,
    val driverLat: Double? = null,
    val driverLon: Double? = null,
    val pickupDistance: Double? = null,
    val deliveryDistance: Double? = null,
    val totalDistance: Double? = null,
    val truePayPerMile: Double? = null,
    val vehicleCost: Double? = null,
    val netValue: Double? = null,
    val estimatedMinutes: Int? = null,
    val timeCost: Double? = null,
    val minutesOnJob: Double? = null
)
