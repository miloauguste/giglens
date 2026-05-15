package com.augusteenterprise.giglens.location
// Author: Claude (Anthropic)
// Gets a single GPS fix at offer capture time.
// Returns null if permission denied or fix times out.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "LocationHelper"
private const val TIMEOUT_MS = 5000L

object LocationHelper {

    suspend fun getCurrentLocation(context: Context): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.d(TAG, "Location permission not granted — skipping pickup distance")
            return null
        }

        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                getLocationFix(context)
            } ?: run {
                Log.d(TAG, "GPS fix timed out after ${TIMEOUT_MS}ms")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}", e)
            null
        }
    }

    private suspend fun getLocationFix(context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)

            try {
                fusedClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null && !cont.isCompleted) {
                        Log.d(TAG, "Using last known location: ${location.latitude}, ${location.longitude}")
                        cont.resume(location)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception on lastLocation: ${e.message}", e)
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMaxUpdates(1)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedClient.removeLocationUpdates(this)
                    val location = result.lastLocation
                    if (location != null && !cont.isCompleted) {
                        Log.d(TAG, "Fresh GPS fix: ${location.latitude}, ${location.longitude}")
                        cont.resume(location)
                    }
                }
            }

            try {
                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                cont.invokeOnCancellation {
                    fusedClient.removeLocationUpdates(callback)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception requesting updates: ${e.message}", e)
                if (!cont.isCompleted) cont.resume(null)
            }
        }

    fun straightLineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1609.34)
    }
}
