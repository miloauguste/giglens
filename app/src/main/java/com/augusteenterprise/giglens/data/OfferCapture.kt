package com.augusteenterprise.giglens.data

// Author: Claude (Anthropic)
// Room entity representing a single captured delivery offer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offer_captures")
data class OfferCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val platform: String = "DoorDash",      // Phase 1: DoorDash only
    val payAmount: Double? = null,           // Extracted dollar amount
    val distance: Double? = null,            // Extracted mileage
    val distanceUnit: String = "mi",         // mi or km
    val restaurant: String? = null,          // Extracted restaurant name
    val screenshotPath: String? = null,      // Path to saved screenshot
    val rawOcrText: String? = null,          // Full OCR output for debugging
    val accepted: Boolean? = null            // null = unknown, true/false if detected
)
