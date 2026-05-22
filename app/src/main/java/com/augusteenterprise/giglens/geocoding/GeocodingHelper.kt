package com.augusteenterprise.giglens.geocoding
// Author: Claude (Anthropic) - Added loadStateNameMap() + written-out state detection in extractRegionHint()
// Geocodes street addresses using Nominatim (OpenStreetMap) — free, no API key.
// Rate limit: 1 request/second. GigLens makes max 2 calls per offer capture.
// Pro tier: swap resolveAddress() with Google Maps Geocoding API for higher accuracy.

import android.location.Location
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

private const val TAG = "GeocodingHelper"
private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
private const val USER_AGENT = "GigLens/1.0 (android; giglens@augusteenterprise.com)"
private const val TIMEOUT_MS = 5000
// Road distance is ~1.3x straight-line distance on average
private const val ROAD_FACTOR = 1.3

data class GeoPoint(val lat: Double, val lon: Double, val displayName: String)

data class DistanceEstimate(
    val pickupPoint: GeoPoint?,
    val dropoffPoint: GeoPoint?,
    val straightLineMiles: Double?,   // raw straight-line distance
    val estimatedRoadMiles: Double?,  // × ROAD_FACTOR
    val method: String                // "geocoded" or "unavailable"
)

// Written-out state names to abbreviations — loaded from assets/state_name_map.json
// Edit that file to add new states — no hardcoded strings in source
private var STATE_NAME_MAP: Map<String, String> = emptyMap()

object GeocodingHelper {

    /**
     * Loads written-out state name -> abbreviation map from assets/state_name_map.json.
     * Call once from GigLensApp.onCreate() before any geocoding runs.
     *
     * CORRECT: called in GigLensApp.onCreate() with application context
     * WRONG:   called in Activity/Fragment (may not run before first offer share)
     */
    fun loadStateNameMap(context: Context) {
        try {
            val json = context.assets.open("state_name_map.json")
                .bufferedReader()
                .readText()
            val obj = org.json.JSONObject(json)
            STATE_NAME_MAP = obj.keys().asSequence().associateWith { obj.getString(it) }
            Log.i(TAG, "Loaded ${STATE_NAME_MAP.size} state name mappings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load state_name_map.json: ${e.message}", e)
            // Non-fatal — written-out state detection just won't work
        }
    }

    /**
     * Geocodes pickup and dropoff streets and returns estimated road distance.
     * Returns null distances if geocoding fails — caller must handle gracefully.
     * Auto-extracts region hint from raw OCR text if no hint provided.
     *
     * CORRECT: pass rawOcrText → region extracted from map labels → accurate geocode
     * WRONG:   always passing stored driver_region which may not match offer location
     *
     * @param pickupStreet   Street name near restaurant (from OCR)
     * @param dropoffStreet  Street name near customer (from OCR)
     * @param regionHint     Override region hint (from GPS reverse geocode if available)
     * @param rawOcrText     Full OCR text — used to extract region hint when GPS unavailable
     */
    suspend fun estimateDeliveryDistance(
        pickupStreet: String?,
        dropoffStreet: String?,
        regionHint: String? = null,
        rawOcrText: String? = null
    ): DistanceEstimate {
        if (pickupStreet == null && dropoffStreet == null) {
            return DistanceEstimate(null, null, null, null, "unavailable")
        }

        // Use provided hint, fall back to OCR-extracted region, then no hint
        val resolvedHint = regionHint
            ?: rawOcrText?.let { extractRegionHint(it) }

        Log.d(TAG, "Region hint: $resolvedHint (source: ${if (regionHint != null) "GPS" else if (resolvedHint != null) "OCR" else "none"})")

        val pickup  = pickupStreet?.let  { resolveAddress(it, resolvedHint) }
        val dropoff = dropoffStreet?.let { resolveAddress(it, resolvedHint) }

        if (pickup == null || dropoff == null) {
            Log.d(TAG, "Geocoding partial — pickup: $pickup, dropoff: $dropoff")
            return DistanceEstimate(pickup, dropoff, null, null, "partial")
        }

        val straightLine = straightLineMiles(pickup.lat, pickup.lon, dropoff.lat, dropoff.lon)
        val roadEstimate = straightLine * ROAD_FACTOR

        Log.d(TAG, "Distance: ${straightLine}mi straight → ${roadEstimate}mi road estimate")

        return DistanceEstimate(
            pickupPoint        = pickup,
            dropoffPoint       = dropoff,
            straightLineMiles  = straightLine,
            estimatedRoadMiles = roadEstimate,
            method             = "geocoded"
        )
    }

    /**
     * Calls Nominatim to resolve a street name to lat/lon.
     * Returns null if not found or network error.
     */
    suspend fun resolveAddress(street: String, regionHint: String? = null): GeoPoint? =
        withContext(Dispatchers.IO) {
            try {
                val query = if (regionHint != null) "$street, $regionHint" else street
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "$NOMINATIM_URL?q=$encoded&format=json&limit=1"

                Log.d(TAG, "Geocoding: $query")

                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = TIMEOUT_MS
                    readTimeout    = TIMEOUT_MS
                }

                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = JSONArray(response)
                if (json.length() == 0) {
                    Log.d(TAG, "No results for: $query")
                    return@withContext null
                }

                val first = json.getJSONObject(0)
                val lat = first.getDouble("lat")
                val lon = first.getDouble("lon")
                val name = first.getString("display_name")

                Log.d(TAG, "Resolved '$query' → $lat, $lon")
                GeoPoint(lat, lon, name)

            } catch (e: Exception) {
                Log.e(TAG, "Geocoding failed for '$street': ${e.message}", e)
                null
            }
        }

    /**
     * Reverse geocodes a lat/lon to extract state/region for use as geocoding hint.
     * Returns format like "New Jersey, USA" or null if unavailable.
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"
                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = TIMEOUT_MS
                    readTimeout    = TIMEOUT_MS
                }
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                val json = org.json.JSONObject(response)
                val address = json.optJSONObject("address") ?: return@withContext null
                val state   = address.optString("state", "")
                val country = address.optString("country", "")
                if (state.isNotBlank() && country.isNotBlank()) "$state, $country" else null
            } catch (e: Exception) {
                Log.e(TAG, "Reverse geocode failed: ${e.message}", e)
                null
            }
        }

    /**
     * Extracts the best region hint from raw OCR text for use as a Nominatim anchor.
     * Scans for known state abbreviations and filters out map garbage.
     * Example: "Willingboro Township NJ" → "Willingboro, NJ"
     *
     * CORRECT: OCR has "Willingboro", "Township", "NJ" → returns "Willingboro, NJ"
     * WRONG:   returning the raw driver_region from app_config which may be stale
     */
    fun extractRegionHint(rawOcrText: String): String? {
        val lines = rawOcrText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // US state abbreviations — used to detect state line in OCR
        val stateAbbreviations = setOf(
            "AL","AK","AZ","AR","CA","CO","CT","DE","FL","GA","HI","ID","IL","IN","IA",
            "KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH","NJ",
            "NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT","VT",
            "VA","WA","WV","WI","WY","DC"
        )

        // Skip tokens — map garbage, UI elements, not place names
        val skipTokens = setOf(
            "mapbox", "omapbox", "accept", "decline", "pickup", "dropoff",
            "guaranteed", "deliver", "doordash", "turnpike", "township",
            "borough", "county", "pkwy", "blvd", "rd", "st", "ave"
        )

        // Find state abbreviation in OCR — strongest anchor signal
        var detectedState: String? = null
        for (line in lines) {
            val words = line.trim().split(" ")
            for (word in words) {
                if (word.uppercase() in stateAbbreviations) {
                    detectedState = word.uppercase()
                    break
                }
            }
            // Written-out state detection via STATE_NAME_MAP (loaded from assets)
            if (detectedState == null) {
                for ((fullName, abbr) in STATE_NAME_MAP) {
                    if (rawOcrText.contains(fullName, ignoreCase = true)) {
                        detectedState = abbr
                        break
                    }
                }
            }
        }

        // Find best town candidate — capitalized word, 4-20 chars, not a skip token
        val townCandidate = lines.firstOrNull { line ->
            val lower = line.lowercase()
            line.length in 4..20
                && line[0].isUpperCase()
                && line.none { it.isDigit() }
                && skipTokens.none { lower.contains(it) }
                && !lower.contains("guaranteed")
                && !lower.contains("deliver")
                && line.split(" ").size <= 2
        }

        return when {
            townCandidate != null && detectedState != null ->
                "$townCandidate, $detectedState"
            townCandidate != null ->
                townCandidate
            detectedState != null ->
                detectedState
            else -> null
        }
    }

    /**
     * Haversine straight-line distance in miles between two lat/lon points.
     */
    fun straightLineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1609.34)
    }
}
