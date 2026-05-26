package com.augusteenterprise.giglens.service
// Author: Claude - DEBUG ONLY: lets ADB trigger widget states for testing
// without a live DoorDash order. Fires SHOW_CAMERA / HIDE_CAMERA / fake offer.
//
// Usage (debug build only):
//   adb shell am broadcast -a com.augusteenterprise.giglens.DEBUG_TRIGGER --es state camera
//   adb shell am broadcast -a com.augusteenterprise.giglens.DEBUG_TRIGGER --es state hide
//   adb shell am broadcast -a com.augusteenterprise.giglens.DEBUG_TRIGGER --es state offer
//
// CORRECT: only registered in debug builds via manifest (exported=true, debug only)
// WRONG:   shipping this in release — it would be a security hole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DebugTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugTrigger"
        const val ACTION_DEBUG_TRIGGER = "com.augusteenterprise.giglens.DEBUG_TRIGGER"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ACTION_DEBUG_TRIGGER) return

        val state = intent.getStringExtra("state") ?: "camera"
        Log.i(TAG, "Debug trigger received: state=$state")

        when (state) {
            "camera" -> {
                // Morph widget to camera button
                val i = Intent(context, OfferOverlayService::class.java).apply {
                    action = ACTION_SHOW_CAMERA
                }
                context.startService(i)
                Log.i(TAG, "Sent SHOW_CAMERA to overlay")
            }
            "hide" -> {
                val i = Intent(context, OfferOverlayService::class.java).apply {
                    action = ACTION_HIDE_CAMERA
                }
                context.startService(i)
                Log.i(TAG, "Sent HIDE_CAMERA to overlay")
            }
            "offer" -> {
                // Simulate a captured offer with fake result data
                val i = Intent(context, OfferOverlayService::class.java).apply {
                    putExtra(EXTRA_NET_VALUE,      -4.63)
                    putExtra(EXTRA_VERDICT,        "SKIP")
                    putExtra(EXTRA_PAY_AMOUNT,     5.00)
                    putExtra(EXTRA_RESTAURANT,     "Test Wawa")
                    putExtra(EXTRA_PICKUP_MILES,   7.4)
                    putExtra(EXTRA_TOTAL_MILES,    7.4)
                    putExtra(EXTRA_VEHICLE_COST,   6.66)
                    putExtra(EXTRA_TIME_COST,      2.97)
                    putExtra(EXTRA_TOTAL_COST,     9.63)
                    putExtra(EXTRA_MINUTES_ON_JOB, 12.0)
                    putExtra(EXTRA_SCORE,          8)
                    putExtra(EXTRA_COST_PER_MILE,  0.90)
                }
                context.startService(i)
                Log.i(TAG, "Sent fake offer result to overlay")
            }
            else -> Log.w(TAG, "Unknown debug state: $state")
        }
    }
}
