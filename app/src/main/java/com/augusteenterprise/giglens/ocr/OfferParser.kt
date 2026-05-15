package com.augusteenterprise.giglens.ocr

// Author: Claude (Anthropic)
// Parses ML Kit OCR text output to extract offer details from gig app screenshots

import android.util.Log

data class ParsedOffer(
    val payAmount: Double? = null,
    val distance: Double? = null,
    val restaurant: String? = null,
    val isOfferScreen: Boolean = false,
    val rawText: String = ""
)

object OfferParser {

    private const val TAG = "OfferParser"

    // Keywords that indicate this is a delivery offer screen
    private val OFFER_KEYWORDS = listOf(
        "accept", "decline",
        "delivery for", "deliver by",
        "total may be higher",
        "includes doordash pay",
        "guaranteed",
        "pickup", "pick up",
        "customer dropoff"
    )

    // Dollar amounts: $7.50, S7.50 (OCR often reads $ as S)
    private val PAY_REGEX = Regex("\\\$\\s?(\\d{1,3}(?:\\.\\d{2})?)")
    private val PAY_REGEX_OCR = Regex("(?<![A-Za-z])S(\\d{1,3}\\.\\d{2})")

    // Distance: 3.2 mi, 5 mi, 1.8 miles
    private val DISTANCE_REGEX = Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*(?:mi(?:les?)?)""", RegexOption.IGNORE_CASE)

    /**
     * Determines if the OCR text looks like a delivery offer screen.
     * Requires at least 2 offer keywords AND a dollar amount.
     */
    fun isOfferScreen(ocrText: String): Boolean {
        val lower = ocrText.lowercase()
        val matchCount = OFFER_KEYWORDS.count { lower.contains(it) }
        val hasDollar = PAY_REGEX.containsMatchIn(ocrText) || PAY_REGEX_OCR.containsMatchIn(ocrText)
        val isOffer = matchCount >= 2 && hasDollar
        Log.d(TAG, "isOfferScreen: matchCount=$matchCount hasDollar=$hasDollar result=$isOffer")
        return isOffer
    }

    /**
     * Extracts structured offer data from raw OCR text.
     */
    fun parse(ocrText: String): ParsedOffer {
        if (!isOfferScreen(ocrText)) {
            return ParsedOffer(isOfferScreen = false, rawText = ocrText)
        }

        val payAmount = extractPay(ocrText)
        val distance = extractDistance(ocrText)
        val restaurant = extractRestaurant(ocrText)

        Log.d(TAG, "Parsed offer: pay=$payAmount distance=$distance restaurant=$restaurant")

        return ParsedOffer(
            payAmount = payAmount,
            distance = distance,
            restaurant = restaurant,
            isOfferScreen = true,
            rawText = ocrText
        )
    }

    /**
     * Extracts the primary pay amount.
     * Takes the largest dollar value as the offer pay.
     * Handles OCR misreading $ as S.
     */
    private fun extractPay(text: String): Double? {
        val realDollar = PAY_REGEX.findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 1.0..999.0 }
            .toList()
        val ocrDollar = PAY_REGEX_OCR.findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 1.0..999.0 }
            .toList()
        val all = realDollar + ocrDollar
        return all.maxOrNull()
    }

    /**
     * Extracts distance in miles.
     */
    private fun extractDistance(text: String): Double? {
        val match = DISTANCE_REGEX.find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /**
     * Extracts restaurant name.
     * Looks for the line after "Pickup" or "Pick up from",
     * or a capitalized line that isn't a known UI element.
     */
    private fun extractRestaurant(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Strategy 1: Line immediately after "Pickup"
        for ((i, line) in lines.withIndex()) {
            val lineLower = line.lowercase()
            if (lineLower == "pickup" || lineLower == "pick up"
                || lineLower.contains("delivery for")
                || lineLower.contains("pick up from")
            ) {
                // Check next few lines — OCR may insert garbage between Pickup and restaurant name
                for (j in 1..3) {
                    if (i + j >= lines.size) break
                    val candidate = lines[i + j].trim()
                    if (candidate.length in 3..50
                        && candidate[0].isUpperCase()
                        && candidate.any { it.isLetter() }
                        && !PAY_REGEX.containsMatchIn(candidate)
                        && !DISTANCE_REGEX.containsMatchIn(candidate)
                        && !candidate.lowercase().let { c ->
                            c.contains("accept") || c.contains("decline")
                            || c.contains("customer") || c.contains("dropoff")
                            || c.contains("mapbox") || c.contains("deliver")
                        }
                    ) {
                        return candidate
                    }
                }
            }
        }

        // Strategy 2: Look for known restaurant-like lines
        val skipWords = setOf(
            "accept", "decline", "total", "guaranteed",
            "delivery", "deliver", "doordash", "instructions",
            "items", "customer", "dropoff", "pickup", "mapbox",
            "center", "rd", "ave", "st", "blvd", "turnpike"
        )
        for (line in lines) {
            if (line.length in 3..40
                && line[0].isUpperCase()
                && !PAY_REGEX.containsMatchIn(line)
                && !DISTANCE_REGEX.containsMatchIn(line)
                && skipWords.none { line.lowercase().contains(it) }
            ) {
                return line
            }
        }

        return null
    }
}
