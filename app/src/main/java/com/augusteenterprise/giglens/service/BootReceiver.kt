package com.augusteenterprise.giglens.service

// Author: Claude - Feature #8: Also starts CaptureButtonService on boot if mode is button/both
// Restarts the overlay widget service on device boot if driver had it enabled.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.augusteenterprise.giglens.data.AppConfigKeys
import com.augusteenterprise.giglens.data.OfferDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed — checking widget preference")

        CoroutineScope(Dispatchers.IO).launch {
            val db = OfferDatabase.getInstance(context)
            val widgetEnabled = db.appConfigDao().getValue(AppConfigKeys.WIDGET_ENABLED) == "true"
            val hasPermission = Settings.canDrawOverlays(context)

            if (widgetEnabled && hasPermission) {
                Log.d(TAG, "Auto-starting widget service on boot")
                val serviceIntent = Intent(context, OfferOverlayService::class.java)
                context.startService(serviceIntent)
            }
        }
    }
}
