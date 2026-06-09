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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.augusteenterprise.giglens.ocr.ParsedOffer
import com.augusteenterprise.giglens.BuildConfig

class OfferDetectorService : AccessibilityService() {
    private var lastShowCameraMs = 0L

    companion object {
        private const val TAG = "OfferDetector"
private const val SHOW_CAMERA_COOLDOWN_MS = 2000L
        const val ACTION_OFFER_DETECTED = "com.augusteenterprise.giglens.OFFER_DETECTED"
        const val ACTION_OFFER_EXTRACTED = "com.augusteenterprise.giglens.OFFER_EXTRACTED"
        const val EXTRA_PAY = "extra_pay"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_RESTAURANT = "extra_restaurant"
        const val EXTRA_DELIVER_BY = "extra_deliver_by"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val EXTRA_SOURCE = "extra_source"

        // Cooldown to avoid duplicate captures (ms)
        private const val CAPTURE_COOLDOWN_MS = 3000L

        // DoorDash package
        private const val DOORDASH_PACKAGE = "com.doordash.driverapp"

        var isRunning = false
            private set
    }

    private var lastCaptureTime = 0L
    private var lastOfferFingerprint = ""  // detects new vs same offer screen
    private val accessibilityOfferReceiver = com.augusteenterprise.giglens.service.AccessibilityOfferReceiver()

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
            packageNames = arrayOf("com.doordash.driverapp") // CORRECT: filter to DoorDash only — pill must not appear on other apps
        }
        serviceInfo = info

        Log.i(TAG, "OfferDetectorService connected — serviceInfo set programmatically")

        // Register AccessibilityOfferReceiver for ACTION_OFFER_EXTRACTED
        // CORRECT: register here -- onServiceConnected() is the right lifecycle point
        // WRONG:   static manifest-only registration -- goAsync() needs a live receiver instance
        val extractedFilter = android.content.IntentFilter(ACTION_OFFER_EXTRACTED)
        androidx.core.content.ContextCompat.registerReceiver(
            this, accessibilityOfferReceiver, extractedFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.i(TAG, "AccessibilityOfferReceiver registered")

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

        // CORRECT: accessibility extraction runs regardless of ScreenCaptureService state
        // WRONG: hard gate on ScreenCaptureService.isRunning -- blocks all offer detection
        //        when MediaProjection token expires, entire shift is lost with no scoring
        // CORRECT: gate entire event on DoorDash package -- pill must never appear on other apps
        // WRONG: relying solely on packageNames filter -- Android 16 may still deliver stale events
        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage != DOORDASH_PACKAGE) {
            // Non-DoorDash event -- hide pill if it was showing
            if (lastOfferFingerprint.isNotEmpty()) {
                Log.d(TAG, "Non-DoorDash package=$eventPackage -- sending HIDE_CAMERA")
                startService(android.content.Intent(applicationContext, OfferOverlayService::class.java).apply {
                    action = ACTION_HIDE_CAMERA
                })
                lastOfferFingerprint = ""
            }
            return
        }
        val captureRunning = ScreenCaptureService.isRunning
        Log.d(TAG, "captureRunning=$captureRunning -- accessibility extraction proceeds regardless")
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
        // CORRECT: cooldown gates signalCapture() only — not SHOW_CAMERA or fingerprint check
        // WRONG: early return on cooldown before fingerprint check — SHOW_CAMERA never fires on real offers

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
                // Cooldown gates signalCapture() only — not SHOW_CAMERA
                if (now - lastCaptureTime < CAPTURE_COOLDOWN_MS) return
                lastCaptureTime = now
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

        // CORRECT: log to Crashlytics in release, file in debug — survives logcat buffer clear
        // WRONG: logcat only — buffer overwrites during long shifts
        if (allTexts.isNotEmpty()) {
            val textsJoined = allTexts.take(15).joinToString(" | ")
            val crashlyticsMsg = "looksLike: accept=$acceptFound decline=$declineFound " +
                "guaranteed=$guaranteedFound mi=$miFound isOffer=$isOffer | $textsJoined"
            FirebaseCrashlytics.getInstance().log(crashlyticsMsg)

            if (BuildConfig.DEBUG) {
                try {
                    val dir = java.io.File(
                        GigLensApp.instance.getExternalFilesDir(null), "debug"
                    )
                    dir.mkdirs()
                    val file = java.io.File(dir, "screen_texts.log")
                    if (file.exists() && file.length() > 2 * 1024 * 1024) {
                        file.delete()
                        Log.d(TAG, "screen_texts.log rotated — exceeded 2MB")
                    }
                    val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())
                    val line = timestamp + " isOffer=" + isOffer + " | " + textsJoined + System.lineSeparator()
                    file.appendText(line)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write screen texts: ${e.message}")
                }
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
        // CORRECT: try accessibility extraction first -- faster and more accurate
        // WRONG: always falling through to MediaProjection -- slower and error-prone
        val root = rootInActiveWindow
        if (root != null) {
            try {
                val extracted = extractOfferFromNodes(root)
                if (extracted != null) {
                    Log.i(TAG, "Accessibility extraction SUCCESS -- pay=${extracted.payAmount} distance=${extracted.distance}")
                    FirebaseCrashlytics.getInstance().log("EXTRACT:accessibility pay=${extracted.payAmount} dist=${extracted.distance}")
                    val broadcastIntent = Intent(ACTION_OFFER_EXTRACTED).apply {
                        setPackage(packageName)
                        putExtra(EXTRA_PAY, extracted.payAmount ?: 0.0)
                        putExtra(EXTRA_DISTANCE, extracted.distance ?: 0.0)
                        putExtra(EXTRA_RESTAURANT, extracted.restaurant ?: "")
                        putExtra(EXTRA_DELIVER_BY, extracted.rawText)
                        putExtra(EXTRA_SOURCE, "accessibility")
                    }
                    sendBroadcast(broadcastIntent)
                    return
                } else {
                    Log.w(TAG, "Accessibility extraction null -- falling back to OCR")
                    FirebaseCrashlytics.getInstance().log("EXTRACT:fallback_to_ocr")
                }
            } finally {
                root.recycle()
            }
        } else {
            Log.w(TAG, "rootInActiveWindow null -- falling back to OCR")
        }
        // Fallback: OCR via MediaProjection -- only if capture service is alive
        // CORRECT: skip OCR broadcast if ScreenCaptureService dead -- avoids CAPTURE_DEAD spiral
        // WRONG:   broadcasting ACTION_OFFER_DETECTED when capture dead -- triggers CAPTURE_DEAD overlay
        if (!ScreenCaptureService.isRunning) {
            Log.w(TAG, "OCR fallback skipped -- ScreenCaptureService dead, accessibility-only mode")
            return
        }
        if (cachedAutoCapureMode == "accessibility" || cachedAutoCapureMode == "both") {
            Log.i(TAG, "Auto-capture mode=$cachedAutoCapureMode -- triggering OCR capture fallback")
            val captureIntent = Intent(ACTION_OFFER_DETECTED)
            captureIntent.setPackage(packageName)
            sendBroadcast(captureIntent)
        } else {
            Log.d(TAG, "Mode=$cachedAutoCapureMode -- waiting for driver tap")
        }
    }

    /**
     * Extracts offer data directly from accessibility node tree.
     * Confirmed DoorDash node structure (screen_texts.log 2026-06-08):
     *   decline | uuid | $3.55  guaranteed (incl. tips) | 6.0 mi | deliver by 10:55 pm | pickup | mcdonald's | accept | 19
     * Returns null if pay or distance missing -- caller falls back to OCR.
     * CORRECT: return null when fields missing -- triggers OCR fallback
     * WRONG:   returning ParsedOffer with nulls -- scorer silently uses 0.0
     */
    private fun extractOfferFromNodes(root: AccessibilityNodeInfo): ParsedOffer? {
        val payRegex = Regex("""\$(\d{1,3}\.\d{2})""")
        val distanceRegex = Regex("""^(\d+\.?\d*)\s*mi$""", RegexOption.IGNORE_CASE)
        val deliverByRegex = Regex("""deliver\s+by\s+(\d{1,2}:\d{2}\s*[ap]m)""", RegexOption.IGNORE_CASE)
        val texts = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo) {
            val t = node.text?.toString()?.trim() ?: ""
            if (t.isNotBlank()) texts.add(t)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(root)
        var pay: Double? = null
        var distance: Double? = null
        var restaurant: String? = null
        var deliverBy: String? = null
        var countdownSeconds: Int? = null
        for ((i, text) in texts.withIndex()) {
            val lower = text.lowercase()
            // Pay: anchor to "guaranteed" node -- avoids $0.50 promo node
            // CORRECT: check "guaranteed" in lower before running payRegex
            // WRONG:   first $ found -- picks up promo amount instead of offer pay
            if (pay == null && "guaranteed" in lower) {
                payRegex.find(text)?.let { pay = it.groupValues[1].toDoubleOrNull() }
            }
            // Distance: exact match "X.X mi" node only
            // CORRECT: matchEntire -- avoids address lines containing mi
            // WRONG:   containsMatchIn -- matches "1.2 mi from you" in address text
            if (distance == null) {
                distanceRegex.matchEntire(text)?.let { distance = it.groupValues[1].toDoubleOrNull() }
            }
            // Deliver by time
            if (deliverBy == null) {
                deliverByRegex.find(text)?.let { deliverBy = it.groupValues[1].trim() }
            }
            // Restaurant: node immediately after exact "pickup" node
            // CORRECT: lower == "pickup" exact match
            // WRONG:   contains("pickup") -- matches "customer pickup instructions"
            if (restaurant == null && lower == "pickup" && i + 1 < texts.size) {
                val candidate = texts[i + 1].trim()
                if (candidate.length >= 2
                    && !deliverByRegex.containsMatchIn(candidate)
                    && !distanceRegex.containsMatchIn(candidate)
                    && candidate.lowercase() !in setOf("customer dropoff", "accept", "decline")
                ) { restaurant = candidate }
            }
            // Countdown: numeric-only node e.g. "19", "8"
            // CORRECT: matches Regex digit-only -- avoids UUID digit substrings
            // WRONG:   toIntOrNull() on any node -- UUIDs contain parseable digit runs
            if (countdownSeconds == null && text.trim().matches(Regex("""^\d{1,3}$"""))) {
                countdownSeconds = text.trim().toIntOrNull()
            }
        }
        if (pay == null || distance == null) {
            Log.w(TAG, "extractOfferFromNodes: pay=$pay distance=$distance -- null, OCR fallback")
            return null
        }
        Log.i(TAG, "extractOfferFromNodes: pay=$pay dist=$distance restaurant=$restaurant deliverBy=$deliverBy countdown=$countdownSeconds")
        return ParsedOffer(payAmount = pay, distance = distance, restaurant = restaurant, isOfferScreen = true, rawText = deliverBy ?: "")
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
        try { unregisterReceiver(accessibilityOfferReceiver) } catch (_: Exception) {}
        Log.i(TAG, "OfferDetectorService destroyed")
        super.onDestroy()
    }
}
