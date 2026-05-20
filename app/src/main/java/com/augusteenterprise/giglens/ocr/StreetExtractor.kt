package com.augusteenterprise.giglens.ocr
// Author: Claude (Anthropic)
// Extracts pickup and dropoff street addresses from DoorDash OCR text.
// Uses positional clues: pickup street appears near "Pickup" keyword,
// dropoff street appears near "Customer dropoff" keyword.

import android.util.Log

private const val TAG = "StreetExtractor"

data class ExtractedAddresses(
    val pickupStreet: String?,   // street near restaurant/pickup marker
    val dropoffStreet: String?,  // street near customer dropoff marker
    val deliverByTime: String?   // deadline extracted from OCR
)

// Street suffix tokens — lines containing these are likely addresses
private val STREET_SUFFIXES = setOf(
    "rd", "road", "st", "street", "ave", "avenue", "blvd", "boulevard",
    "dr", "drive", "ln", "lane", "ct", "court", "pl", "place", "way",
    "pkwy", "parkway", "tpke", "turnpike", "hwy", "highway", "cir", "circle"
)

// Lines to skip — UI elements, map artifacts, known garbage
private val SKIP_TOKENS = setOf(
    "accept", "decline", "pickup", "dropoff", "customer", "delivery",
    "deliver", "doordash", "dasher", "guaranteed", "total", "mapbox",
    "center", "attach", "instructions", "items", "pickup", "map"
)

object StreetExtractor {

    fun extract(rawOcrText: String): ExtractedAddresses {
        val lines = rawOcrText.lines()
            .map { it.trim() }
            .filter { it.length >= 4 }

        val pickupStreet  = extractNearKeyword(lines, "pickup")
        val dropoffStreet = extractNearKeyword(lines, "dropoff")
        val deliverBy     = extractDeliverBy(lines)

        Log.d(TAG, "Extracted — pickup: $pickupStreet | dropoff: $dropoffStreet | by: $deliverBy")

        return ExtractedAddresses(
            pickupStreet  = pickupStreet,
            dropoffStreet = dropoffStreet,
            deliverByTime = deliverBy
        )
    }

    /**
     * Scans lines BEFORE and AFTER a keyword for the first line that looks like a street address.
     * DoorDash renders the street ABOVE the Pickup/Dropoff label on screen,
     * so ML Kit OCR delivers it BEFORE the keyword in the text stream.
     * Scans 10 lines before (nearest first), then falls back to lines after.
     * Skips idx+1 after keyword — that line is always the restaurant name, not a street.
     */
    private fun extractNearKeyword(lines: List<String>, keyword: String): String? {
        val keywordIdx = lines.indexOfFirst {
            it.lowercase().contains(keyword)
        }
        if (keywordIdx < 0) return null

        // Scan lines BEFORE keyword (reversed so nearest line wins)
        val beforeLines = lines.subList(
            (keywordIdx - 10).coerceAtLeast(0),
            keywordIdx
        ).reversed()

        val beforeResult = beforeLines.firstOrNull { isStreetLine(it) }
        if (beforeResult != null) return beforeResult

        // Fallback: scan lines AFTER keyword — skip idx+1 (restaurant name)
        val afterLines = lines.subList(
            (keywordIdx + 2).coerceAtMost(lines.size),
            (keywordIdx + 7).coerceAtMost(lines.size)
        )
        return afterLines.firstOrNull { isStreetLine(it) }
    }

    /**
     * Returns true if line looks like a street address.
     * Must contain a street suffix and not be a skip token.
     */
    private fun isStreetLine(line: String): Boolean {
        val lower = line.lowercase()

        // Skip known UI/garbage tokens
        if (SKIP_TOKENS.any { lower.contains(it) }) return false

        // Must contain a street suffix word
        val words = lower.split(" ", "-")
        if (!words.any { it in STREET_SUFFIXES }) return false

        // Must start with a number or capital letter (address pattern)
        if (!line[0].isDigit() && !line[0].isUpperCase()) return false

        return true
    }

    /**
     * Extracts "Deliver by X:XX PM" time from OCR text.
     */
    private fun extractDeliverBy(lines: List<String>): String? {
        val deliverLine = lines.firstOrNull {
            it.lowercase().contains("deliver by")
        } ?: return null

        // Extract time portion after "by"
        val timeRegex = Regex("""(\d{1,2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE)
        return timeRegex.find(deliverLine)?.value
    }
}
