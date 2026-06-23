package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic) - 2026-06-08
// Receives ACTION_OFFER_EXTRACTED from OfferDetectorService (accessibility path)
// and runs the same scoring pipeline as ScreenCaptureService without OCR/screenshot.

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.geocoding.DeliveryTownEstimator
import com.augusteenterprise.giglens.location.LocationHelper
import com.augusteenterprise.giglens.data.OfferCapture
import com.augusteenterprise.giglens.scoring.OfferScorer
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_TOWN_CONFIRMED = "com.augusteenterprise.giglens.TOWN_CONFIRMED"
const val ACTION_TOWN_WRONG     = "com.augusteenterprise.giglens.TOWN_WRONG"
const val EXTRA_CAPTURE_ID      = "capture_id"
const val EXTRA_CONFIRMED_TOWN  = "confirmed_town"
const val TOWN_CONFIRM_CHANNEL  = "town_accuracy"

fun showTownConfirmationNotification(context: Context, captureId: Long, town: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Create channel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            TOWN_CONFIRM_CHANNEL,
            "Delivery Town Accuracy",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Confirms estimated delivery town accuracy" }
        manager.createNotificationChannel(channel)
    }

    // Yes intent
    val yesIntent = android.content.Intent(ACTION_TOWN_CONFIRMED).apply {
        setPackage(context.packageName)
        putExtra(EXTRA_CAPTURE_ID, captureId)
        putExtra(EXTRA_CONFIRMED_TOWN, town)
    }
    val yesPending = PendingIntent.getBroadcast(
        context, captureId.toInt(),
        yesIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // No intent
    val noIntent = android.content.Intent(ACTION_TOWN_WRONG).apply {
        setPackage(context.packageName)
        putExtra(EXTRA_CAPTURE_ID, captureId)
    }
    val noPending = PendingIntent.getBroadcast(
        context, (captureId + 10000).toInt(),
        noIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, TOWN_CONFIRM_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_map)
        .setContentTitle("Delivering to $town?")
        .setContentText("Help GigLens learn — was this estimate correct?")
        .addAction(0, "✅ Yes", yesPending)
        .addAction(0, "❌ No", noPending)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    manager.notify(captureId.toInt(), notification)
}

class AccessibilityOfferReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AccessibilityReceiver"
        // CORRECT: dedup at receiver level — survives OfferDetectorService restarts
        // WRONG:   dedup only in OfferDetectorService — resets on service restart, duplicates persist
        private const val DEDUP_WINDOW_MS = 120_000L  // 2 min — 30s let a 60s gap through
        @Volatile private var lastFingerprint: String = ""
        @Volatile private var lastInsertMs: Long = 0L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != OfferDetectorService.ACTION_OFFER_EXTRACTED) return

        val pay      = intent.getDoubleExtra(OfferDetectorService.EXTRA_PAY, 0.0).takeIf { it > 0 }
        val distance = intent.getDoubleExtra(OfferDetectorService.EXTRA_DISTANCE, 0.0).takeIf { it > 0 }
        val restaurant = intent.getStringExtra(OfferDetectorService.EXTRA_RESTAURANT) ?: ""
        val deliverBy  = intent.getStringExtra(OfferDetectorService.EXTRA_DELIVER_BY) ?: ""
        val source     = intent.getStringExtra(OfferDetectorService.EXTRA_SOURCE) ?: "accessibility"

        if (pay == null || distance == null) {
            Log.w(TAG, "Missing pay or distance -- ignoring broadcast")
            return
        }

        // CORRECT: dedup at receiver level — blocks duplicate DB inserts within 30s window
        // WRONG:   relying solely on OfferDetectorService dedup — resets on service restart
        val fingerprint = "$pay:$distance"
        val now = System.currentTimeMillis()
        if (fingerprint == lastFingerprint && (now - lastInsertMs) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Duplicate offer suppressed at receiver level — fingerprint=$fingerprint")
            return
        }
        lastFingerprint = fingerprint
        lastInsertMs = now

        Log.i(TAG, "ACTION_OFFER_EXTRACTED received: pay=$pay dist=$distance restaurant=$restaurant source=$source")
        FirebaseCrashlytics.getInstance().log("AccessibilityReceiver: pay=$pay dist=$distance restaurant=$restaurant")

        // CORRECT: use goAsync() -- BroadcastReceiver has 10s limit, coroutine may exceed it
        // WRONG:   launching coroutine without goAsync() -- receiver recycles before coroutine finishes
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db         = GigLensApp.instance.database
                val captureDao = db.offerCaptureDao()
                val configDao  = db.scorerConfigDao()
                val scorer     = OfferScorer(configDao)

                // CORRECT: get GPS fix before scoring — used for town estimation + DB record
                // WRONG:   skipping GPS — driverLat/driverLon stay null, town estimation unavailable
                val driverLocation = LocationHelper.getCurrentLocation(GigLensApp.instance)
                Log.d(TAG, "GPS fix: ${driverLocation?.latitude}, ${driverLocation?.longitude} bearing=${driverLocation?.bearing}")

                // Estimate delivery town from restaurant name + driver GPS + distance
                val townEstimate = if (restaurant.isNotBlank() && distance != null) {
                    DeliveryTownEstimator.estimateTown(
                        restaurantName  = restaurant,
                        totalDistanceMi = distance,
                        driverLocation  = driverLocation,
                        useGpsMethod    = true
                    )
                } else null
                Log.i(TAG, "Town estimate: ${townEstimate?.displayName} confidence=${townEstimate?.confidence}")

                val personalAvg = captureDao.getAverageScore()
                val result = scorer.score(
                    payAmount        = pay,
                    deliveryDistance = distance,
                    personalAvgScore = personalAvg
                )

                if (result != null) {
                    Log.i(TAG, "Score: ${result.score} | Verdict: ${result.verdict} | Net: ${result.netValue}")

                    // CORRECT: debug-only email, gated inside DebugOfferEmailer by BuildConfig.DEBUG --
                    //          safe to call unconditionally here since the function no-ops in release
                    // WRONG:   wrapping this call in its own BuildConfig.DEBUG check here too -- fine to
                    //          be redundant, but the real gate must live in DebugOfferEmailer itself
                    com.augusteenterprise.giglens.debug.DebugOfferEmailer.sendAsync(
                        payload = com.augusteenterprise.giglens.debug.OfferDebugPayload(
                            timestamp = now,
                            payAmount = pay,
                            distance = distance,
                            restaurant = restaurant,
                            deliverByRawText = deliverBy,
                            source = source,
                            score = result.score,
                            verdict = result.verdict.name,
                            netValue = result.netValue,
                            payPerMile = result.payPerMile,
                            truePayPerMile = result.truePayPerMile,
                            townDisplayName = townEstimate?.displayName,
                            townConfidence = townEstimate?.confidence?.toString()
                        ),
                        debugDir = java.io.File(GigLensApp.instance.getExternalFilesDir(null), "debug")
                    )

                    // Save to DB
                    val capture = OfferCapture(
                        payAmount      = pay,
                        distance       = distance,
                        restaurant     = restaurant.ifBlank { null },
                        screenshotPath = null,
                        rawOcrText     = "source=$source deliverBy=$deliverBy",
                        platform       = "DoorDash",
                        score          = result.score,
                        verdict        = result.verdict.name,
                        payPerMile     = result.payPerMile,
                        vsPersonalAvg  = result.vsPersonalAvg,
                        driverLat      = driverLocation?.latitude,
                        driverLon      = driverLocation?.longitude,
                        totalDistance    = result.totalDistance,
                        // CORRECT: persist the actual calculated pickup/delivery leg distances
                        //          from DeliveryTownEstimator so post-shift accuracy analysis
                        //          can see the real leg split, not nulls
                        // WRONG:   leaving these unset — confirmed root cause of null
                        //          pickupDistance/deliveryDistance in DB (2026-06-18 analysis)
                        pickupDistance   = townEstimate?.pickupLegMi,
                        deliveryDistance = townEstimate?.deliveryLegMi,
                        truePayPerMile   = result.truePayPerMile,
                        vehicleCost    = result.vehicleCost,
                        netValue            = result.netValue,
                        estimatedTown       = townEstimate?.town,
                        estimatedTownMethod = townEstimate?.method,
                        confirmedTown       = null,
                        townAccurate        = null
                    )
                    val captureId = captureDao.insert(capture)
                    Log.i(TAG, "Offer saved — id=$captureId estimatedTown=${townEstimate?.town}")

                    // Post confirmation notification if town was estimated
                    if (townEstimate?.town != null) {
                        showTownConfirmationNotification(
                            context   = GigLensApp.instance,
                            captureId = captureId,
                            town      = townEstimate.town
                        )
                    }

                    // Launch overlay pill with score result
                    // CORRECT: startService from application context -- receiver context may be short-lived
                    // WRONG:   startService from receiver context -- may be recycled before service starts
                    val serviceIntent = Intent(GigLensApp.instance, OfferOverlayService::class.java).apply {
                        putExtra(EXTRA_NET_VALUE,      result.netValue)
                        putExtra(EXTRA_VERDICT,        result.verdict.name)
                        putExtra(EXTRA_PAY_AMOUNT,     pay)
                        putExtra(EXTRA_RESTAURANT,     restaurant)
                        putExtra(EXTRA_PICKUP_MILES,   townEstimate?.pickupLegMi ?: 0.0)
                        putExtra(EXTRA_TOTAL_MILES,    distance)
                        putExtra(EXTRA_VEHICLE_COST,   result.gasCost)
                        putExtra(EXTRA_TIME_COST,      result.wearTearCost)
                        putExtra(EXTRA_TOTAL_COST,     result.totalCost)
                        putExtra(EXTRA_MINUTES_ON_JOB, result.minutesOnJob)
                        putExtra(EXTRA_SCORE,          result.score)
                        putExtra(EXTRA_PROFIT_PCT,     result.profitPct)
                        putExtra(EXTRA_COST_PER_MILE,  result.costPerMileUsed)
                        putExtra(EXTRA_DELIVERY_TOWN,  townEstimate?.displayName ?: "📍 ---")
                    }
                    GigLensApp.instance.startService(serviceIntent)
                    Log.d(TAG, "OfferOverlayService launched: verdict=${result.verdict} net=${result.netValue}")
                } else {
                    Log.w(TAG, "Scorer returned null -- pay=$pay distance=$distance")
                }
            } catch (e: Exception) {
                Log.e(TAG, "AccessibilityOfferReceiver error: ${e.message}", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}