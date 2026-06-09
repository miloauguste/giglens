package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic) - 2026-06-08
// Receives ACTION_OFFER_EXTRACTED from OfferDetectorService (accessibility path)
// and runs the same scoring pipeline as ScreenCaptureService without OCR/screenshot.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.OfferCapture
import com.augusteenterprise.giglens.scoring.OfferScorer
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccessibilityOfferReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AccessibilityReceiver"
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

                val personalAvg = captureDao.getAverageScore()
                val result = scorer.score(
                    payAmount        = pay,
                    deliveryDistance = distance,
                    personalAvgScore = personalAvg
                )

                if (result != null) {
                    Log.i(TAG, "Score: ${result.score} | Verdict: ${result.verdict} | Net: ${result.netValue}")

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
                        driverLat      = null,
                        driverLon      = null,
                        totalDistance  = result.totalDistance,
                        truePayPerMile = result.truePayPerMile,
                        vehicleCost    = result.vehicleCost,
                        netValue       = result.netValue
                    )
                    captureDao.insert(capture)

                    // Launch overlay pill with score result
                    // CORRECT: startService from application context -- receiver context may be short-lived
                    // WRONG:   startService from receiver context -- may be recycled before service starts
                    val serviceIntent = Intent(GigLensApp.instance, OfferOverlayService::class.java).apply {
                        putExtra(EXTRA_NET_VALUE,      result.netValue)
                        putExtra(EXTRA_VERDICT,        result.verdict.name)
                        putExtra(EXTRA_PAY_AMOUNT,     pay)
                        putExtra(EXTRA_RESTAURANT,     restaurant)
                        putExtra(EXTRA_PICKUP_MILES,   distance)
                        putExtra(EXTRA_TOTAL_MILES,    result.totalDistance ?: 0.0)
                        putExtra(EXTRA_VEHICLE_COST,   result.gasCost)
                        putExtra(EXTRA_TIME_COST,      result.wearTearCost)
                        putExtra(EXTRA_TOTAL_COST,     result.totalCost)
                        putExtra(EXTRA_MINUTES_ON_JOB, result.minutesOnJob)
                        putExtra(EXTRA_SCORE,          result.score)
                        putExtra(EXTRA_COST_PER_MILE,  result.costPerMileUsed)
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