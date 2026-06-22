package com.augusteenterprise.giglens.ocr

// Author: Claude (Anthropic)
// Last modified: DeepSeek (Ollama) - June 02 2026 - extractRestaurant() time pattern + digit ratio guard + min length 4
// Parses ML Kit OCR text output to extract offer details from gig app screenshots

import android.util.Log

data class DeliveryEstimate(
    val deliveryLegMiles: Double,   // pickup → dropoff estimated miles
    val totalMiles: Double,         // driver → pickup + pickup → dropoff
    val timeRemainingMinutes: Int,  // minutes until deliver-by deadline
    val status: String              // "OK", "EXPIRED", "IMPOSSIBLE"
)

data class ParsedOffer(
    val payAmount: Double? = null,
    val distance: Double? = null,
    val restaurant: String? = null,
    val isOfferScreen: Boolean = false,
    val rawText: String = ""
)

object OfferParser {

    private const val TAG = "OfferParser"

    // Pre-processing: fix fragmented single characters
    private fun fixFragmentedChars(text: String): String {
        // Match 3 or more single chars separated by single spaces
        return text.replace(Regex("""(\b[A-Za-z]\b ?){3,}""")) { match ->
            match.value.replace(" ", "")
        }
    }

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
     * Extracts current time from the status bar line (always first line of OCR).
     * Status bar format: "2:58 X oo •" or "4:40 VOO MO•" etc.
     * Returns time as total minutes since midnight for easy math.
     *
     * CORRECT: "2:58 X oo •" → 178 minutes (2*60+58)
     * WRONG:   parsing any line for time — must be status bar only (first non-empty line)
     */
    fun extractCurrentTime(text: String, deliverByMinutes: Int? = null): Int? {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: return null
        val timeRegex = Regex("""^(\d{1,2}):(\d{2})""")
        val match = timeRegex.find(firstLine.trim()) ?: return null
        var hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        if (hours > 12 || minutes > 59) return null

        // Status bar has no AM/PM — infer from deliver-by time
        // If deliver-by is PM (>=720 min) and current hour is small, assume PM
        // CORRECT: deliverBy=917(3PM), currentHour=2 → 2+12=14 → 14*60+58=898 min
        // WRONG:   leaving as 2*60+58=178 min → 739 min time remaining
        if (deliverByMinutes != null && deliverByMinutes >= 12 * 60 && hours < 8) {
            hours += 12
        }

        Log.d(TAG, "extractCurrentTime: '$firstLine' → ${hours}h${minutes}m = ${hours*60+minutes}min")
        return hours * 60 + minutes
    }

    /**
     * Extracts deliver-by time as total minutes since midnight.
     * Returns null if not found.
     *
     * CORRECT: "Deliver by 3:17 PM" → 15*60+17 = 917 minutes
     * WRONG:   returning raw string — caller needs minutes for math
     */
    fun extractDeliverByMinutes(text: String): Int? {
        val regex = Regex("""[Dd]eliver\s+by\s+(\d{1,2}):(\d{2})\s*(AM|PM)""", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null
        var hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        val ampm = match.groupValues[3].uppercase()
        if (ampm == "PM" && hours != 12) hours += 12
        if (ampm == "AM" && hours == 12) hours = 0
        Log.d(TAG, "extractDeliverByMinutes: → ${hours}h${minutes}m = ${hours*60+minutes} min")
        return hours * 60 + minutes
    }

    /**
     * Estimates delivery leg distance (pickup → dropoff) using time math.
     * Formula: (time_remaining - prep_time) × avg_speed_mph / 60
     *
     * CORRECT: 19 min remaining, 2 min prep, 45 mph → (19-2) × 45/60 = 12.75 mi
     * WRONG:   using 20 mph — unrealistic for suburban NJ gig driving
     *
     * @param currentTimeMinutes  from extractCurrentTime()
     * @param deliverByMinutes    from extractDeliverByMinutes()
     * @param avgSpeedMph         from scorer_config, default 45
     * @param prepTimeMinutes     from scorer_config, default 2
     */
    fun estimateDeliveryLegMiles(
        currentTimeMinutes: Int,
        deliverByMinutes: Int,
        pickupDistanceMiles: Double,
        avgSpeedMph: Double = 45.0,
        prepTimeMinutes: Int = 2
    ): DeliveryEstimate {
        val timeRemainingMin = deliverByMinutes - currentTimeMinutes
        if (timeRemainingMin <= 0) {
            Log.d(TAG, "estimateDeliveryLegMiles: offer already expired")
            return DeliveryEstimate(0.0, 0.0, timeRemainingMin, "EXPIRED")
        }

        val driveTimeToPickupMin = (pickupDistanceMiles / avgSpeedMph) * 60
        val timeForDeliveryMin = timeRemainingMin - driveTimeToPickupMin - prepTimeMinutes

        if (timeForDeliveryMin <= 0) {
            Log.d(TAG, "estimateDeliveryLegMiles: impossible — not enough time to reach pickup")
            return DeliveryEstimate(0.0, pickupDistanceMiles, timeRemainingMin, "IMPOSSIBLE")
        }

        val deliveryLegMiles = (timeForDeliveryMin / 60.0) * avgSpeedMph
        val totalMiles = pickupDistanceMiles + deliveryLegMiles

        Log.d(TAG, "estimateDeliveryLegMiles: ${timeRemainingMin}min remaining | " +
            "${driveTimeToPickupMin.toInt()}min to pickup | " +
            "${timeForDeliveryMin.toInt()}min delivery | " +
            "${deliveryLegMiles}mi delivery leg | ${totalMiles}mi total")

        return DeliveryEstimate(deliveryLegMiles, totalMiles, timeRemainingMin, "OK")
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
        // First, fix any fragmented characters (e.g., "G r i l l" -> "Grill")
        val fixedText = fixFragmentedChars(text)
        
        // Author: Claude (Anthropic) - May 25 2026: Bug E fix
        // Last modified: DeepSeek (Ollama) - June 02 2026 - extractRestaurant() time pattern + digit ratio guard + min length 4
        // Strip leading non-letter OCR noise (e.g. "| McDonald's" → "McDonald's")
        // Restaurant appears AFTER "@ Pickup" line in DoorDash OCR output
        val lines = fixedText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Cleans OCR prefix garbage: "| McDonald's" → "McDonald's", "@ Pickup" → "Pickup"
        fun cleanLine(line: String): String {
            return line.trimStart { !it.isLetter() }.trim()
        }

        // Status words that appear near restaurant but are NOT the name
        val statusWords = setOf(
            "busy", "popular", "trending", "closed", "unavailable",
            "accept", "decline", "total", "guaranteed", "incl",
            "delivery", "deliver", "doordash", "instructions",
            "items", "customer", "dropoff", "pickup", "mapbox",
            "center", "rd", "ave", "st", "blvd", "turnpike",
            "township", "borough", "county"
        )

        // Strategy 1: Line immediately after "@ Pickup", "Pickup", or "Retail pickup"
        // DoorDash OCR structure (food):   ... → "@ Pickup" → "| McDonald's" → "fy Customer dropoff"
        // DoorDash OCR structure (retail): ... → "Retail pickup" → "Wawa" → "Customer dropoff"
        for ((i, line) in lines.withIndex()) {
            val cleaned = cleanLine(line).lowercase()
            if (cleaned == "pickup" || cleaned == "pick up"
                || cleaned == "retail pickup"
                || cleaned.contains("delivery for")
                || cleaned.contains("pick up from")
            ) {
                for (j in 1..3) {
                    if (i + j >= lines.size) break
                    val raw = lines[i + j]
                    val candidate = cleanLine(raw)
                    if (candidate.length in 2..50
                        && candidate.isNotEmpty()
                        && (candidate[0].isUpperCase() || candidate[0].isDigit())
                        && candidate.any { it.isLetter() }
                        && !PAY_REGEX.containsMatchIn(candidate)
                        && !DISTANCE_REGEX.containsMatchIn(candidate)
                        && !Regex("""\d{1,2}:\d{2}""").containsMatchIn(candidate)
                        && candidate.count { it.isDigit() }.toFloat() / candidate.length.toFloat() <= 0.6f
                        && statusWords.none { candidate.lowercase().contains(it) }
                    ) {
                        Log.d(TAG, "extractRestaurant: Strategy 1 found '$candidate' (raw: '$raw')")
                        return candidate
                    }
                }
            }
        }

        // Strategy 2: Fallback — scan all lines for restaurant-like text
        for (line in lines) {
            val candidate = cleanLine(line)
            if (candidate.length in 2..40
                && candidate.isNotEmpty()
                && (candidate[0].isUpperCase() || candidate[0].isDigit())
                && !PAY_REGEX.containsMatchIn(candidate)
                && !DISTANCE_REGEX.containsMatchIn(candidate)
                && !Regex("""\d{1,2}:\d{2}""").containsMatchIn(candidate)
                && candidate.count { it.isDigit() }.toFloat() / candidate.length.toFloat() <= 0.6f
                && statusWords.none { candidate.lowercase().contains(it) }
            ) {
                Log.d(TAG, "extractRestaurant: Strategy 2 found '$candidate' (raw: '$line')")
                return candidate
            }
        }

        Log.w(TAG, "extractRestaurant: no restaurant found in ${lines.size} lines")
        return null
    }
}
