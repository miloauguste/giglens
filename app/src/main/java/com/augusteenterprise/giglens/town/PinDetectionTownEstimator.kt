package com.augusteenterprise.giglens.town
// Author: Claude (Anthropic) - 2026-06-20
// Estimates delivery town using map pin pixel positions instead of GPS bearing.
// More accurate than gps_bearing when driver is stationary (parked, at a light) because
// the pin positions encode the actual route direction shown on screen.
//
// Algorithm:
//   1. Pixel calibration: milesPerPixel = totalDistanceMi / (driver→pickup + pickup→dropoff) in px
//   2. Delivery leg miles = pixelDist(pickup→dropoff) * milesPerPixel
//   3. Bearing = atan2(dropoff.x - pickup.x, -(dropoff.y - pickup.y)) — screen north-up
//   4. Project deliveryMi from restaurant GPS at bearing → dropoff lat/lng
//   5. Reverse geocode projected point via Nominatim → town name

import android.graphics.PointF
import android.util.Log
import com.augusteenterprise.giglens.geocoding.TownEstimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.*

private const val TAG = "PinDetectionTownEstimator"
private const val NOMINATIM_REVERSE = "https://nominatim.openstreetmap.org/reverse"
private const val USER_AGENT = "GigLens/1.0 (android; giglens@augusteenterprise.com)"
private const val TIMEOUT_MS = 5000
private const val EARTH_RADIUS_MILES = 3958.8

object PinDetectionTownEstimator {

    suspend fun estimate(
        result: PinDetectionResult,
        totalDistanceMi: Double,
        restaurantLat: Double,
        restaurantLng: Double
    ): TownEstimate {
        val driverDot = result.driverDot
        val pickup    = result.briefcasePins.firstOrNull()
        val dropoff   = result.housePins.firstOrNull()

        if (driverDot == null || pickup == null || dropoff == null) {
            Log.w(TAG, "estimate: missing pins — driverDot=$driverDot pickup=$pickup dropoff=$dropoff")
            return TownEstimate(null, "low", "unavailable", "📍 ---")
        }

        // Step 1: Pixel calibration
        val driverToPickupPx  = pixelDist(driverDot, pickup)
        val pickupToDropoffPx = pixelDist(pickup, dropoff)
        val totalPx           = driverToPickupPx + pickupToDropoffPx

        if (totalPx < 1f) {
            Log.w(TAG, "estimate: pins too close — totalPx=$totalPx, cannot calibrate")
            return TownEstimate(null, "low", "unavailable", "📍 ---")
        }

        val milesPerPixel = totalDistanceMi / totalPx
        val pickupLegMi   = driverToPickupPx  * milesPerPixel
        val deliveryMi    = pickupToDropoffPx * milesPerPixel

        Log.d(TAG, "estimate: driver→pickup=${driverToPickupPx}px pickup→dropoff=${pickupToDropoffPx}px " +
            "milesPerPixel=$milesPerPixel pickupLeg=${pickupLegMi}mi deliveryLeg=${deliveryMi}mi")

        // Step 2: Bearing from pickup pin to dropoff pin.
        // Screen x increases east, screen y increases south — negate dy for north-up bearing.
        val dx         = (dropoff.x - pickup.x).toDouble()
        val dy         = (dropoff.y - pickup.y).toDouble()
        val bearingDeg = (Math.toDegrees(atan2(dx, -dy)) + 360.0) % 360.0

        Log.d(TAG, "estimate: bearing=$bearingDeg° (dx=$dx dy=$dy)")

        // Step 3: Project deliveryMi from restaurant GPS at bearing
        val (dropoffLat, dropoffLon) = projectPoint(restaurantLat, restaurantLng, deliveryMi, bearingDeg)
        Log.d(TAG, "estimate: projected dropoff → $dropoffLat, $dropoffLon")

        // Step 4: Reverse geocode
        val town = reverseGeocodeCity(dropoffLat, dropoffLon)

        return if (town != null) {
            Log.i(TAG, "estimate: town=$town confidence=high method=pin_detection")
            TownEstimate(
                town          = town,
                confidence    = "high",
                method        = "pin_detection",
                displayName   = "📍 ~$town",
                pickupLegMi   = pickupLegMi,
                deliveryLegMi = deliveryMi
            )
        } else {
            Log.w(TAG, "estimate: reverse geocode returned null")
            TownEstimate(null, "low", "unavailable", "📍 ---",
                pickupLegMi = pickupLegMi, deliveryLegMi = deliveryMi)
        }
    }

    private fun pixelDist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    // Spherical Earth forward projection — identical to DeliveryTownEstimator.projectPoint()
    private fun projectPoint(
        originLat: Double,
        originLon: Double,
        distanceMi: Double,
        bearingDeg: Double
    ): Pair<Double, Double> {
        val bearingRad   = Math.toRadians(bearingDeg)
        val distRatio    = distanceMi / EARTH_RADIUS_MILES
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

    // Same Nominatim city-priority chain as DeliveryTownEstimator.reverseGeocodeCity()
    private suspend fun reverseGeocodeCity(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$NOMINATIM_REVERSE?lat=$lat&lon=$lon&format=json&zoom=10"
                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = TIMEOUT_MS
                    readTimeout    = TIMEOUT_MS
                }
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val address = JSONObject(response).optJSONObject("address")
                    ?: return@withContext null

                val town = address.optString("city").takeIf    { it.isNotBlank() }
                    ?: address.optString("town").takeIf         { it.isNotBlank() }
                    ?: address.optString("village").takeIf      { it.isNotBlank() }
                    ?: address.optString("suburb").takeIf       { it.isNotBlank() }
                    ?: address.optString("county").takeIf       { it.isNotBlank() }

                Log.d(TAG, "reverseGeocodeCity $lat,$lon → $town")
                town
            } catch (e: Exception) {
                Log.e(TAG, "reverseGeocodeCity failed: ${e.message}")
                null
            }
        }
}
