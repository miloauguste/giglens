package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic) - Feature #8: Checks auto_capture_mode + enabled_platforms before triggering
// Accessibility Service that monitors supported gig app offer screens.
// When an offer screen is detected, it signals the ScreenCaptureService
// to take a screenshot and run OCR.

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.AppConfigKeys
import com.augusteenterprise.giglens.data.PlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class OfferDetectorService : AccessibilityService() {

    companion object {
        private const val TAG = "OfferDetector"
        const val ACTION_OFFER_DETECTED = "com.augusteenterprise.giglens.OFFER_DETECTED"

        // Cooldown to avoid duplicate captures (ms)
        private const val CAPTURE_COOLDOWN_MS = 3000L

        // DoorDash package
        private const val DOORDASH_PACKAGE = "com.dd.doordash"

        var isRunning = false
            private set
    }

    private var lastCaptureTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "OfferDetectorService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Check auto capture mode is enabled
        val mode = runBlocking {
            GigLensApp.instance.database.appConfigDao()
                .getValue(AppConfigKeys.AUTO_CAPTURE_MODE) ?: "off"
        }
        if (mode == "off" || mode == "button") return

        // Check package is a supported + enabled platform
        val packageName = event.packageName?.toString() ?: return
        val enabledPlatforms = runBlocking {
            GigLensApp.instance.database.appConfigDao()
                .getValue(AppConfigKeys.ENABLED_PLATFORMS) ?: "doordash"
        }
        val platform = PlatformRegistry.byPackage(packageName) ?: return
        if (!platform.supported) return
        if (platform.id !in enabledPlatforms.split(",").map { it.trim() }) return

        // Only process content/window changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        // Cooldown check
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < CAPTURE_COOLDOWN_MS) return

        // Walk the accessibility tree looking for offer keywords
        val rootNode = rootInActiveWindow ?: return
        if (looksLikeOfferScreen(rootNode)) {
            Log.i(TAG, "Offer screen detected — signaling capture")
            lastCaptureTime = now
            signalCapture()
        }

        rootNode.recycle()
    }

    /**
     * Walks the accessibility node tree and checks for offer-related keywords.
     * Returns true if enough signals are found to indicate a delivery offer.
     */
    private fun looksLikeOfferScreen(root: AccessibilityNodeInfo): Boolean {
        var acceptFound = false
        var declineFound = false
        var dollarFound = false

        fun walk(node: AccessibilityNodeInfo) {
            val text = node.text?.toString()?.lowercase() ?: ""
            if (text.contains("accept")) acceptFound = true
            if (text.contains("decline")) declineFound = true
            if (text.contains("$")) dollarFound = true

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }

        walk(root)

        // Require at least Accept + Decline + a dollar amount
        val isOffer = acceptFound && declineFound && dollarFound
        if (isOffer) {
            Log.d(TAG, "Offer signals: accept=$acceptFound decline=$declineFound dollar=$dollarFound")
        }
        return isOffer
    }

    /**
     * Sends a broadcast to ScreenCaptureService to trigger a screenshot.
     */
    private fun signalCapture() {
        val intent = Intent(ACTION_OFFER_DETECTED)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "OfferDetectorService interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        Log.i(TAG, "OfferDetectorService destroyed")
        super.onDestroy()
    }
}
