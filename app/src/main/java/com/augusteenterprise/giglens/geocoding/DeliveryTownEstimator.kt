package com.augusteenterprise.giglens.geocoding
// Author: Claude (Anthropic) - 2026-06-15
// Estimates delivery town from offer data without DoorDash revealing the address.
// Algorithm:
//   1. Geocode restaurant name near driver GPS → pickup coordinates
//   2. Compute pickup leg = straight-line distance(driver → restaurant)
//   3. Delivery leg = total distance - pickup leg
//   4. Project delivery leg from restaurant in driver's heading direction
//   5. Reverse geocode projected point → city/town name
//
// CORRECT: use driver GPS + heading to project delivery direction
// WRONG:   random direction guess — yields wrong town in most cases

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.math.*

private const val TAG = "DeliveryTownEstimator"
private const val NOMINATIM_SEARCH = "https://nominatim.openstreetmap.org/search"
private const val NOMINATIM_REVERSE = "https://nominatim.openstreetmap.org/reverse"
private const val USER_AGENT = "GigLens/1.0 (android; giglens@augusteenterprise.com)"
private const val TIMEOUT_MS = 5000
private const val EARTH_RADIUS_MILES = 3958.8

data class TownEstimate(
    val town: String?,           // e.g. "Cherry Hill" or null if unavailable
    val confidence: String,      // "high" | "medium" | "low"
    val method: String,          // "gps_bearing" | "city_fallback" | "unavailable"
    val displayName: String,     // e.g. "📍 ~Cherry Hill" or "📍 ---"
    // CORRECT: carry pickup/delivery leg distances out of estimateTown() so the
    //          caller can persist them to the DB and diagnose accuracy later
    // WRONG:   computing these internally and discarding them — confirmed as the
    //          root cause of null pickupDistance/deliveryDistance columns in the
    //          offer_captures DB, found during 2026-06-18 post-shift analysis
    val pickupLegMi: Double = 0.0,
    val deliveryLegMi: Double = 0.0
)

object DeliveryTownEstimator {

    /**
     * Main entry point. Estimates delivery town from offer data.
     *
     * @param restaurantName  Restaurant name from offer (e.g. "McDonald's")
     * @param totalDistanceMi Total trip distance from DoorDash offer (miles)
     * @param driverLocation  Current driver GPS location (lat, lon, bearing, speed)
     * @param useGpsMethod    true = Option 1 (nearest POI to GPS), false = Option 2 (city search)
     */
    suspend fun estimateTown(
        restaurantName: String,
        totalDistanceMi: Double,
        driverLocation: Location?,
        useGpsMethod: Boolean = true
    ): TownEstimate {
        if (restaurantName.isBlank() || totalDistanceMi <= 0) {
            return TownEstimate(null, "low", "unavailable", "📍 ---")
        }

        // Step 1: Geocode restaurant → pickup coordinates
        val restaurantCoords = if (useGpsMethod && driverLocation != null) {
            // Option 1: nearest POI to driver GPS (default)
            resolveNearestPOI(restaurantName, driverLocation.latitude, driverLocation.longitude)
        } else {
            // Option 2: search by city from reverse geocode of driver location
            val city = driverLocation?.let {
                reverseGeocodeCity(it.latitude, it.longitude)
            }
            resolveByCity(restaurantName, city)
        }

        if (restaurantCoords == null) {
            Log.w(TAG, "Could not geocode restaurant: $restaurantName")
            return TownEstimate(null, "low", "unavailable", "📍 ---")
        }

        Log.d(TAG, "Restaurant geocoded: ${restaurantCoords.first}, ${restaurantCoords.second}")

        // Step 2: Compute pickup leg distance
        val pickupLegMi = if (driverLocation != null) {
            GeocodingHelper.straightLineMiles(
                driverLocation.latitude, driverLocation.longitude,
                restaurantCoords.first, restaurantCoords.second
            )
        } else 1.0  // fallback estimate if no GPS

        // Step 3: Delivery leg = total - pickup leg
        val deliveryLegMi = (totalDistanceMi - pickupLegMi).coerceAtLeast(0.5)
        Log.d(TAG, "Total: ${totalDistanceMi}mi | Pickup leg: ${pickupLegMi}mi | Delivery leg: ${deliveryLegMi}mi")

        // Step 4: Project delivery leg from restaurant using driver bearing
        val bearing = driverLocation?.bearing?.toDouble() ?: 0.0
        val (dropoffLat, dropoffLon) = projectPoint(
            restaurantCoords.first, restaurantCoords.second,
            deliveryLegMi, bearing
        )

        Log.d(TAG, "Projected dropoff: $dropoffLat, $dropoffLon (bearing: $bearing°)")

        // Step 5: Reverse geocode projected point → town name
        val town = reverseGeocodeCity(dropoffLat, dropoffLon)

        return if (town != null) {
            val confidence = when {
                driverLocation != null && driverLocation.speed > 2.0 -> "high"   // moving = reliable bearing
                driverLocation != null -> "medium"                                // stationary = bearing unreliable
                else -> "low"
            }
            // CORRECT: log a warning when bearing is unreliable (speed <= 2.0) so
            //          post-shift analysis can distinguish bearing-error misses from
            //          geocoding misses — confirmed during 2026-06-18 analysis that
            //          speed/bearing state at offer time is critical diagnostic data
            // WRONG:   silently projecting with bearing=0.0 (north) when stationary
            //          with no indication in logs or DB that the result is unreliable
            if (confidence != "high") {
                Log.w(TAG, "Town estimate confidence=$confidence — bearing may be unreliable " +
                    "(speed=${driverLocation?.speed ?: 0f} m/s, bearing=${driverLocation?.bearing ?: 0f}°) " +
                    "pickupLeg=${pickupLegMi}mi deliveryLeg=${deliveryLegMi}mi")
            }
            TownEstimate(
                town          = town,
                confidence    = confidence,
                method        = if (useGpsMethod) "gps_bearing" else "city_fallback",
                displayName   = "📍 ~$town",
                pickupLegMi   = pickupLegMi,
                deliveryLegMi = deliveryLegMi
            )
        } else {
            TownEstimate(null, "low", "unavailable", "📍 ---",
                pickupLegMi = pickupLegMi, deliveryLegMi = deliveryLegMi)
        }
    }

    /**
     * Option 1: Nominatim nearest POI search — finds closest matching restaurant to driver GPS.
     * CORRECT: use lat/lon viewbox centered on driver to find the right McDonald's
     * WRONG:   global search for "McDonald's" — returns random location
     */
    private suspend fun resolveNearestPOI(
        name: String,
        driverLat: Double,
        driverLon: Double
    ): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            // Search within ~10 mile box around driver
            val delta = 0.15  // ~10 miles in degrees
            val viewbox = "${driverLon - delta},${driverLat - delta},${driverLon + delta},${driverLat + delta}"
            val encoded = URLEncoder.encode(name, "UTF-8")
            val url = "$NOMINATIM_SEARCH?q=$encoded&format=json&limit=5&viewbox=$viewbox&bounded=1"

            val response = httpGet(url) ?: return@withContext null
            val json = JSONArray(response)
            if (json.length() == 0) return@withContext null

            // Find closest result to driver
            var bestDist = Double.MAX_VALUE
            var bestLat = 0.0
            var bestLon = 0.0

            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val lat = obj.getDouble("lat")
                val lon = obj.getDouble("lon")
                val dist = GeocodingHelper.straightLineMiles(driverLat, driverLon, lat, lon)
                if (dist < bestDist) {
                    bestDist = dist
                    bestLat = lat
                    bestLon = lon
                }
            }

            Log.d(TAG, "Nearest POI '$name' found ${bestDist}mi from driver")
            Pair(bestLat, bestLon)
        } catch (e: Exception) {
            Log.e(TAG, "resolveNearestPOI failed: ${e.message}")
            null
        }
    }

    /**
     * Option 2: Search restaurant by city name — fallback when GPS unavailable.
     */
    private suspend fun resolveByCity(
        name: String,
        city: String?
    ): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val query = if (city != null) "$name, $city" else name
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$NOMINATIM_SEARCH?q=$encoded&format=json&limit=1"

            val response = httpGet(url) ?: return@withContext null
            val json = JSONArray(response)
            if (json.length() == 0) return@withContext null

            val first = json.getJSONObject(0)
            Pair(first.getDouble("lat"), first.getDouble("lon"))
        } catch (e: Exception) {
            Log.e(TAG, "resolveByCity failed: ${e.message}")
            null
        }
    }

    /**
     * Reverse geocodes lat/lon → city/town name only (not full address).
     * CORRECT: extract "city" or "town" or "suburb" from address object
     * WRONG:   returning full display_name — too verbose for pill display
     */
    private suspend fun reverseGeocodeCity(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$NOMINATIM_REVERSE?lat=$lat&lon=$lon&format=json&zoom=10"
                val response = httpGet(url) ?: return@withContext null
                val json = JSONObject(response)
                val address = json.optJSONObject("address") ?: return@withContext null

                // Priority: city > town > village > suburb > county
                val town = address.optString("city").takeIf { it.isNotBlank() }
                    ?: address.optString("town").takeIf { it.isNotBlank() }
                    ?: address.optString("village").takeIf { it.isNotBlank() }
                    ?: address.optString("suburb").takeIf { it.isNotBlank() }
                    ?: address.optString("county").takeIf { it.isNotBlank() }

                Log.d(TAG, "Reverse geocode $lat,$lon → $town")
                town
            } catch (e: Exception) {
                Log.e(TAG, "reverseGeocodeCity failed: ${e.message}")
                null
            }
        }

    /**
     * Projects a point from origin in bearing direction by distance miles.
     * Uses spherical Earth model.
     * CORRECT: bearing in degrees clockwise from north — matches Android Location.bearing
     * WRONG:   bearing in radians or counter-clockwise — wrong direction
     */
    private fun projectPoint(
        originLat: Double,
        originLon: Double,
        distanceMi: Double,
        bearingDeg: Double
    ): Pair<Double, Double> {
        val bearingRad = Math.toRadians(bearingDeg)
        val distRatio = distanceMi / EARTH_RADIUS_MILES
        val originLatRad = Math.toRadians(originLat)
        val originLonRad = Math.toRadians(originLon)

        val destLatRad = asin(
            sin(originLatRad) * cos(distRatio) +
            cos(originLatRad) * sin(distRatio) * cos(bearingRad)
        )
        val destLonRad = originLonRad + atan2(
            sin(bearingRad) * sin(distRatio) * cos(originLatRad),
            cos(distRatio) - sin(originLatRad) * sin(destLatRad)
        )

        return Pair(Math.toDegrees(destLatRad), Math.toDegrees(destLonRad))
    }

    private fun httpGet(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            response
        } catch (e: Exception) {
            Log.e(TAG, "HTTP GET failed for $url: ${e.message}")
            null
        }
    }
}
