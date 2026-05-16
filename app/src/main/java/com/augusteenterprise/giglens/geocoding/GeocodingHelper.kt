package com.augusteenterprise.giglens.geocoding
// Author: Claude (Anthropic)
// Geocodes street addresses using Nominatim (OpenStreetMap) — free, no API key.
// Rate limit: 1 request/second. GigLens makes max 2 calls per offer capture.
// Pro tier: swap resolveAddress() with Google Maps Geocoding API for higher accuracy.

import android.location.Location
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

object GeocodingHelper {

    /**
     * Geocodes pickup and dropoff streets and returns estimated road distance.
     * Returns null distances if geocoding fails — caller must handle gracefully.
     *
     * @param pickupStreet   Street name near restaurant (from OCR)
     * @param dropoffStreet  Street name near customer (from OCR)
     * @param regionHint     City/region hint to improve accuracy (from driver GPS)
     */
    suspend fun estimateDeliveryDistance(
        pickupStreet: String?,
        dropoffStreet: String?,
        regionHint: String? = null
    ): DistanceEstimate {
        if (pickupStreet == null && dropoffStreet == null) {
            return DistanceEstimate(null, null, null, null, "unavailable")
        }

        val pickup  = pickupStreet?.let  { resolveAddress(it, regionHint) }
        val dropoff = dropoffStreet?.let { resolveAddress(it, regionHint) }

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
     * Haversine straight-line distance in miles between two lat/lon points.
     */
    fun straightLineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1609.34)
    }
}
