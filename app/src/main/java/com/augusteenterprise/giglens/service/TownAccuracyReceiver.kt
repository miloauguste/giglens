package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic) - 2026-06-15
// Handles driver confirmation of estimated delivery town accuracy.
// Receives ACTION_TOWN_CONFIRMED or ACTION_TOWN_WRONG from notification buttons.
// Updates offer_captures.townAccurate + confirmedTown for accuracy tracking.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.augusteenterprise.giglens.GigLensApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TownAccuracyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TownAccuracyReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val captureId = intent.getLongExtra(EXTRA_CAPTURE_ID, -1L)
        if (captureId == -1L) {
            Log.w(TAG, "No capture ID in intent — ignoring")
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = GigLensApp.instance.database.offerCaptureDao()
                when (intent.action) {
                    ACTION_TOWN_CONFIRMED -> {
                        val town = intent.getStringExtra(EXTRA_CONFIRMED_TOWN) ?: ""
                        dao.updateTownAccuracy(captureId, confirmedTown = town, accurate = true)
                        Log.i(TAG, "Town confirmed correct — captureId=$captureId town=$town")
                    }
                    ACTION_TOWN_WRONG -> {
                        dao.updateTownAccuracy(captureId, confirmedTown = null, accurate = false)
                        Log.i(TAG, "Town marked incorrect — captureId=$captureId")
                    }
                    else -> Log.w(TAG, "Unknown action: ${intent.action}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "TownAccuracyReceiver error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
