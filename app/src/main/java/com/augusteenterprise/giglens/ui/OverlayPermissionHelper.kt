package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic)
// Checks and requests SYSTEM_ALERT_WINDOW permission for the floating pill widget.
// Called from both onboarding and Settings.

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

private const val TAG = "OverlayPermHelper"

object OverlayPermissionHelper {

    /**
     * Returns true if the app can draw over other apps.
     * CORRECT: Settings.canDrawOverlays(context) → true means granted
     * WRONG:   checking PackageManager permissions — SYSTEM_ALERT_WINDOW is special
     */
    fun hasPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /**
     * Opens Android Settings to the "Draw over other apps" screen for this app.
     * Driver must manually toggle ON and return to GigLens.
     */
    fun requestPermission(activity: Activity) {
        Log.d(TAG, "Opening overlay permission settings")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, REQUEST_CODE)
    }

    const val REQUEST_CODE = 1001
}
