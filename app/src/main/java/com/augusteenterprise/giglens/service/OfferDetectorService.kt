package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic) - Feature #8: Offer fingerprint prevents re-triggering camera on same offer
// Accessibility Service that monitors supported gig app offer screens.
// When an offer screen is detected, it signals the ScreenCaptureService
// to take a screenshot and run OCR.

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.app.ActivityManager
import com.augusteenterprise.giglens.service.ACTION_SHOW_CAMERA
import com.augusteenterprise.giglens.service.ACTION_HIDE_CAMERA
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
        private const val DOORDASH_PACKAGE = "com.doordash.driverapp"

        var isRunning = false
            private set
    }

    private var lastCaptureTime = 0L
    private var lastOfferFingerprint = ""  // detects new vs same offer screen

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        // Author: Claude (Anthropic) - May 26 2026: Android 16 requires runtime setServiceInfo()
        // XML config alone is insufficient on Android 16 (SDK 36) — must set programmatically
        // CORRECT: set ServiceInfo in onServiceConnected so OS delivers events
        // WRONG:   relying only on accessibility_service_config.xml — events never delivered on API 36
        val info = android.accessibilityservice.AccessibilityServiceInfo().apply {
            eventTypes = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 500
            flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            // packageNames = arrayOf("com.doordash.driverapp") // TEMP: removed for Android 16 testing
        }
        serviceInfo = info

        Log.i(TAG, "OfferDetectorService connected — serviceInfo set programmatically")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Check auto capture mode is enabled
        val mode = runBlocking {
            GigLensApp.instance.database.appConfigDao()
                .getValue(AppConfigKeys.AUTO_CAPTURE_MODE) ?: "off"
        }
        // CORRECT: only return on "off" — "button" mode still needs SHOW_CAMERA
        // WRONG:   returning on "button" — camera button never appears in button mode
        if (mode == "off") return

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
            // Build a fingerprint from offer text to detect NEW vs SAME offer
            val fingerprint = buildOfferFingerprint(rootNode)
            if (fingerprint == lastOfferFingerprint) {
                // Same offer still on screen — don't re-trigger
                rootNode.recycle()
                return
            }
            Log.i(TAG, "NEW offer screen detected — signaling capture")
            lastOfferFingerprint = fingerprint
            lastCaptureTime = now
            signalCapture()
        }

        rootNode.recycle()
    }

    /**
     * Builds a lightweight fingerprint of the offer screen from visible text.
     * Used to detect whether this is a NEW offer or the same one still showing.
     *
     * CORRECT: hash the dollar amounts + distances → unique per offer
     * WRONG:   re-triggering camera every 500ms on the same static offer screen
     */
    private fun buildOfferFingerprint(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun walk(node: AccessibilityNodeInfo) {
            val text = node.text?.toString() ?: ""
            // Only include text with $ or mi — the parts unique to an offer
            if (text.contains("$") || text.contains("mi")) {
                sb.append(text).append("|")
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(root)
        return sb.toString().hashCode().toString()
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
     * Signals the overlay widget to morph to camera button.
     * If mode is accessibility or both, also auto-triggers capture.
     *
     * CORRECT: morph widget first, then conditionally auto-capture
     * WRONG:   always triggering capture regardless of mode setting
     */
    private fun signalCapture() {
        // Always morph overlay widget to camera button
        val overlayIntent = Intent(this, OfferOverlayService::class.java).apply {
            action = ACTION_SHOW_CAMERA
        }
        startService(overlayIntent)
        Log.d(TAG, "Sent SHOW_CAMERA to overlay")

        // Auto-capture only if mode allows it
        val mode = runBlocking {
            GigLensApp.instance.database.appConfigDao()
                .getValue(AppConfigKeys.AUTO_CAPTURE_MODE) ?: "off"
        }
        if (mode == "accessibility" || mode == "both") {
            Log.i(TAG, "Auto-capture mode=$mode — triggering capture")
            val captureIntent = Intent(ACTION_OFFER_DETECTED)
            captureIntent.setPackage(packageName)
            sendBroadcast(captureIntent)
        } else {
            Log.d(TAG, "Mode=$mode — waiting for driver to tap camera button")
        }
    }

    /**
     * Signals the overlay widget to morph back to pill when offer screen is gone.
     */
    fun signalOfferGone() {
        val overlayIntent = Intent(this, OfferOverlayService::class.java).apply {
            action = ACTION_HIDE_CAMERA
        }
        startService(overlayIntent)
        Log.d(TAG, "Sent HIDE_CAMERA to overlay")
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
