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
    private var lastShowCameraMs = 0L

    companion object {
        private const val TAG = "OfferDetector"
private const val SHOW_CAMERA_COOLDOWN_MS = 2000L
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

    // CORRECT: cache DB config values at connect time — read once, not on every accessibility event
    // WRONG: calling runBlocking inside onAccessibilityEvent() — blocks accessibility thread, ANR risk
    private var cachedAutoCapureMode = "button"
    private var cachedEnabledPlatforms = "doordash"

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

        // Populate config cache on connect — avoids runBlocking on every accessibility event
        CoroutineScope(Dispatchers.IO).launch {
            val dao = GigLensApp.instance.database.appConfigDao()
            cachedAutoCapureMode = dao.getValue(AppConfigKeys.AUTO_CAPTURE_MODE) ?: "button"
            cachedEnabledPlatforms = dao.getValue(AppConfigKeys.ENABLED_PLATFORMS) ?: "doordash"
            Log.d(TAG, "Config cache loaded: mode=$cachedAutoCapureMode platforms=$cachedEnabledPlatforms")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // CORRECT: gate on ScreenCaptureService.isRunning — driver enabled the toggle
        // WRONG:   reading AUTO_CAPTURE_MODE from DB — DB may be "off" on fresh install
        if (!ScreenCaptureService.isRunning) {
            // CORRECT: signal overlay to show CAPTURE_DEAD warning pill — driver sees it over DoorDash
            // WRONG: silently returning — driver thinks app is working, misses offers all shift
            val deadIntent = Intent(applicationContext, OfferOverlayService::class.java)
            startService(deadIntent)
            Log.w(TAG, "ScreenCaptureService dead — signaling overlay to show CAPTURE_DEAD state")
            return
        }
        android.util.Log.d("OfferDetector", "captureRunning=true — proceeding")
        // CORRECT: use cached values — populated once at onServiceConnected
        // WRONG: runBlocking here blocks accessibility thread on every event — ANR risk
        val mode = cachedAutoCapureMode

        // Check package is a supported + enabled platform
        val packageName = event.packageName?.toString() ?: return
        val enabledPlatforms = cachedEnabledPlatforms
        val platform = PlatformRegistry.byPackage(packageName) ?: return
        if (!platform.supported) return
        // CORRECT: also allow if enabledPlatforms is empty or DB not yet seeded
        // WRONG:   blocking all platforms if DB returns null/empty on fresh install
        val platformList = enabledPlatforms.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (platformList.isNotEmpty() && platform.id !in platformList) return
        android.util.Log.d("OfferDetector", "platform=${platform.id} enabled — proceeding to offer check")

        // Only process content/window changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

              // Show camera button when DoorDash foreground — driver can tap manually
        // CORRECT: send SHOW_CAMERA only when offer screen detected — not on every window event
        // WRONG: sending SHOW_CAMERA on every TYPE_WINDOW_CONTENT_CHANGED — camera blinks on
        //        map redraws, navigation updates, earnings screen, any DoorDash UI change

  // Cooldown check
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < CAPTURE_COOLDOWN_MS) return

        // Walk the accessibility tree looking for offer keywords
        val rootNode = rootInActiveWindow ?: return
        // CORRECT: try/finally guarantees recycle() even if looksLikeOfferScreen() throws
        // WRONG: relying on manual recycle() calls — any exception mid-walk leaks native node object
        try {
            if (looksLikeOfferScreen(rootNode)) {
                val fingerprint = buildOfferFingerprint(rootNode)
                if (fingerprint == lastOfferFingerprint) {
                    // Same offer still on screen — don't re-trigger
                    return
                }
                Log.i(TAG, "NEW offer screen detected — signaling capture")
                lastOfferFingerprint = fingerprint
                lastCaptureTime = now
                // CORRECT: send SHOW_CAMERA only on confirmed new offer screen
                // WRONG: sending SHOW_CAMERA on every window event — camera blinks randomly
                val now2 = System.currentTimeMillis()
                if (now2 - lastShowCameraMs >= SHOW_CAMERA_COOLDOWN_MS) {
                    lastShowCameraMs = now2
                    Log.d(TAG, "Offer screen confirmed — sending SHOW_CAMERA")
                    startService(Intent(applicationContext, OfferOverlayService::class.java).apply {
                        action = ACTION_SHOW_CAMERA
                    })
                }
                signalCapture()
            } else {
                // No offer screen detected — hide camera button if it was showing
                // CORRECT: send HIDE_CAMERA when offer screen gone — camera button clears
                // WRONG: leaving camera button visible after offer dismissed — confuses driver
                if (lastOfferFingerprint.isNotEmpty()) {
                    Log.d(TAG, "Offer screen gone — sending HIDE_CAMERA")
                    startService(Intent(applicationContext, OfferOverlayService::class.java).apply {
                        action = ACTION_HIDE_CAMERA
                    })
                    lastOfferFingerprint = ""
                }
            }
        } finally {
            rootNode.recycle()
        }
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
        var guaranteedFound = false
        var miFound = false
        val allTexts = mutableListOf<String>()

        fun walk(node: AccessibilityNodeInfo) {
            val text = node.text?.toString()?.lowercase() ?: ""
            if (text.isNotBlank()) allTexts.add(text)
            if (text.contains("accept")) acceptFound = true
            if (text.contains("decline")) declineFound = true
            if (text.contains("$")) dollarFound = true
            // CORRECT: "guaranteed" is a strong DoorDash offer signal — only on offer screen
            // WRONG: relying solely on $ — appears on home/earnings screen too
            if (text.contains("guaranteed")) guaranteedFound = true
            if (text == "mi" || text.endsWith(" mi") || text.contains(" mi ")) miFound = true

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }

        walk(root)

        // Primary signal: accept + decline + guaranteed = definitive offer screen
        // Fallback: accept + decline + dollar (original logic)
        val isOffer = (acceptFound && declineFound && guaranteedFound) ||
                      (acceptFound && declineFound && dollarFound && miFound)
        Log.d(TAG, "looksLikeOfferScreen: accept=$acceptFound decline=$declineFound dollar=$dollarFound guaranteed=$guaranteedFound mi=$miFound → isOffer=$isOffer")

        // CORRECT: dump all screen texts to file for post-shift diagnosis
        // WRONG: logging only — logcat buffer clears, no data after shift
        if (allTexts.isNotEmpty()) {
            try {
                val dir = java.io.File(
                    GigLensApp.instance.getExternalFilesDir(null), "debug"
                )
                dir.mkdirs()
                val file = java.io.File(dir, "screen_texts.log")
                // Cap at 2MB — rotate by clearing when exceeded
                if (file.exists() && file.length() > 2 * 1024 * 1024) {
                    file.delete()
                    Log.d(TAG, "screen_texts.log rotated — exceeded 2MB")
                }
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                val textsJoined = allTexts.joinToString(" | ")
                val line = timestamp + " isOffer=" + isOffer + " | " + textsJoined + System.lineSeparator()
                file.appendText(line)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write screen texts: ${e.message}")
            }
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
        Log.d(TAG, "signalCapture() called")
        // CORRECT: ACTION_SHOW_CAMERA already sent in onAccessibilityEvent() before signalCapture()
        // WRONG: sending SHOW_CAMERA again here — creates a second orphaned ConnectionRecord per event
        // Removed duplicate startService(ACTION_SHOW_CAMERA) — no-op that was leaking records

        // Auto-capture only if mode allows it
        // CORRECT: use cached mode — same value, no DB hit needed here
        // WRONG: second runBlocking in signalCapture — redundant DB read on already-blocked thread
        if (cachedAutoCapureMode == "accessibility" || cachedAutoCapureMode == "both") {
            Log.i(TAG, "Auto-capture mode=$cachedAutoCapureMode — triggering capture")
            val captureIntent = Intent(ACTION_OFFER_DETECTED)
            captureIntent.setPackage(packageName)
            sendBroadcast(captureIntent)
        } else {
            Log.d(TAG, "Mode=$cachedAutoCapureMode — waiting for driver to tap camera button")
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
