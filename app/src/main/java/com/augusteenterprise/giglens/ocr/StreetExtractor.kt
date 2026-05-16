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
     * Scans lines after a keyword for the first line that looks like a street address.
     */
    private fun extractNearKeyword(lines: List<String>, keyword: String): String? {
        val keywordIdx = lines.indexOfFirst {
            it.lowercase().contains(keyword)
        }
        if (keywordIdx < 0) return null

        // Scan up to 5 lines after the keyword
        val searchLines = lines.subList(
            (keywordIdx + 1).coerceAtMost(lines.size),
            (keywordIdx + 6).coerceAtMost(lines.size)
        )

        return searchLines.firstOrNull { isStreetLine(it) }
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
