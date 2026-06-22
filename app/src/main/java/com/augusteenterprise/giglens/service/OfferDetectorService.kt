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
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.augusteenterprise.giglens.ocr.ParsedOffer
import com.augusteenterprise.giglens.BuildConfig
import com.augusteenterprise.giglens.town.PinDetector

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

        // Offer fingerprint dedup — suppresses re-broadcast of same offer within window
        // CORRECT: hash pay+distance, ignore re-fires while same offer is on screen
        // WRONG:   recording every accessibility event as a new offer entry
        private const val OFFER_DEDUP_WINDOW_MS = 120_000L  // 2 min — 30s let a 60s gap through

        // DoorDash package
        private const val DOORDASH_PACKAGE = "com.doordash.driverapp"

        var isRunning = false
            private set
    }

    private var lastCaptureTime = 0L
    private var lastOfferFingerprint = ""  // detects new vs same offer screen
    private var lastOfferBroadcastMs = 0L        // timestamp of last offer broadcast for dedup window
    private val accessibilityOfferReceiver = com.augusteenterprise.giglens.service.AccessibilityOfferReceiver()

    // CORRECT: cache DB config values at connect time — read once, not on every accessibility event
    // WRONG: calling runBlocking inside onAccessibilityEvent() — blocks accessibility thread, ANR risk
    private var cachedAutoCapureMode = "tap"
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

        // Populate config cache on connect — avoids runBlocking on every accessibility event.
        // Also seeds any keys added after initial install that the DB won't have yet.
        CoroutineScope(Dispatchers.IO).launch {
            val dao = GigLensApp.instance.database.appConfigDao()
            dao.seedIfAbsent(
                AppConfigKeys.PIN_DETECTION_ENABLED,
                "true",
                "Use map pin pixel detection for town estimate: true | false"
            )
            cachedAutoCapureMode = dao.getValue(AppConfigKeys.AUTO_CAPTURE_MODE) ?: "accessibility"
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

                // TEST ONLY — confirms AccessibilityService.takeScreenshot() works on this device
                // CORRECT: gated by same looksLikeOfferScreen() confirmation — only fires on real DD offer screen
                // WRONG:   calling on every accessibility event — would fire on every screen, not just offers
                // CORRECT: API 30+ check — takeScreenshot() unavailable below Android 11
                // WRONG:   calling unconditionally — crashes on minSdk 26 devices
                // CORRECT: 1500ms delay via Handler.postDelayed() — gives Mapbox tile rendering time
                //          to complete before screenshot fires. Without this delay, the screenshot
                //          captures the map before pins are rendered, producing images with only
                //          the route line and driver dot but no pickup/dropoff pin icons —
                //          confirmed from real shift screenshots (2026-06-18/19). 1500ms is the
                //          starting value; tune based on whether next shift screenshots show
                //          fully-rendered pins. If pins still missing, increase to 2000ms.
                //          If pins always present at 1500ms, try reducing to 1000ms.
                // WRONG:   calling testTakeScreenshot() synchronously — fires before Mapbox
                //          finishes rendering, produces incomplete screenshots
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    // CORRECT: read delay from DB so it can be tuned in Settings without rebuild
                    // WRONG:   hardcoding 1500L — forces a rebuild to adjust timing per device
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        val delayMs = try {
                            val dao = com.augusteenterprise.giglens.GigLensApp.instance.database.appConfigDao()
                            dao.getValue(com.augusteenterprise.giglens.data.AppConfigKeys.SCREENSHOT_DELAY_MS)
                                ?.toLongOrNull() ?: 1500L
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not read SCREENSHOT_DELAY_MS — using default 1500ms")
                            1500L
                        }
                        kotlinx.coroutines.delay(delayMs)
                        OfferOverlayService.hideForScreenshot()
                        kotlinx.coroutines.delay(50L)  // one frame for hide to render before screenshot fires
                        testTakeScreenshot()
                    }
                } else {
                    Log.w(TAG, "testTakeScreenshot skipped — requires Android 11+ (API 30), device is API ${android.os.Build.VERSION.SDK_INT}")
                }
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
    /**
     * TEST ONLY — confirms AccessibilityService.takeScreenshot() captures the DoorDash offer
     * screen with no MediaProjection dialog, no persistent notification. Only ever called from
     * inside the looksLikeOfferScreen() == true branch — guarantees this NEVER fires on any
     * screen other than a confirmed DoorDash offer screen.
     * Saves bitmap to app's debug folder for visual inspection. Remove once confirmed working
     * and replaced with real OpenCV pin-detection pipeline.
     */
    private fun testTakeScreenshot() {
        Log.i(TAG, "testTakeScreenshot() called — attempting silent screenshot via AccessibilityService")
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            android.content.Context::class.java.let { java.util.concurrent.Executors.newSingleThreadExecutor() },
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer, result.colorSpace
                        )
                        if (bitmap == null) {
                            Log.e(TAG, "testTakeScreenshot: wrapHardwareBuffer returned null")
                            result.hardwareBuffer.close()
                            return
                        }
                        // Hardware bitmaps can't be saved directly — copy to software bitmap first
                        val softwareBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()

                        val dir = java.io.File(GigLensApp.instance.getExternalFilesDir(null), "debug")
                        dir.mkdirs()
                        val timestamp = System.currentTimeMillis()
                        val file = java.io.File(dir, "offer_screenshot_$timestamp.png")
                        java.io.FileOutputStream(file).use { out ->
                            softwareBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                        Log.i(TAG, "testTakeScreenshot SUCCESS — saved to ${file.absolutePath} (${softwareBitmap.width}x${softwareBitmap.height})")
                        FirebaseCrashlytics.getInstance().log("testTakeScreenshot: saved ${file.name}")

                        // Crop to map region only — top 8% = status bar + notification banner,
                        // 24% height captures the full DoorDash map (typically 9–27% of screen).
                        // Keeps PinDetector away from offer-text characters (bottom) and the
                        // DoorDash heads-up notification (top), both of which produce false
                        // white blobs that can be misclassified as delivery pins.
                        val cropTopPx    = (softwareBitmap.height * 0.08).toInt()
                        val cropHeightPx = (softwareBitmap.height * 0.24).toInt()
                            .coerceAtMost(softwareBitmap.height - cropTopPx)
                        val mapBitmap = android.graphics.Bitmap.createBitmap(
                            softwareBitmap, 0, cropTopPx, softwareBitmap.width, cropHeightPx
                        )
                        Log.d(TAG, "Map crop: y=$cropTopPx..${cropTopPx + cropHeightPx} of ${softwareBitmap.height}px (${mapBitmap.width}x${mapBitmap.height})")

                        // Run pin detection on the cropped map frame
                        val pinResult = PinDetector.detect(mapBitmap)
                        mapBitmap.recycle()
                        OfferOverlayService.showAfterScreenshot()
                        Log.i(TAG, "PinDetector: driverDot=${pinResult.driverDot} " +
                            "briefcase=${pinResult.briefcasePins.size} " +
                            "house=${pinResult.housePins.size} success=${pinResult.success}")
                        FirebaseCrashlytics.getInstance().log(
                            "PinDetector: success=${pinResult.success} " +
                            "driverDot=${pinResult.driverDot} " +
                            "briefcase=${pinResult.briefcasePins.size} " +
                            "house=${pinResult.housePins.size}"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "testTakeScreenshot: error processing result: ${e.message}", e)
                        OfferOverlayService.showAfterScreenshot()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "testTakeScreenshot FAILED — errorCode=$errorCode")
                    FirebaseCrashlytics.getInstance().log("testTakeScreenshot failed: errorCode=$errorCode")
                    OfferOverlayService.showAfterScreenshot()
                }
            }
        )
    }

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
                    // CORRECT: fingerprint offer — skip if same offer re-fired within 30s
                    // WRONG: broadcasting every accessibility event — causes duplicate analytics
                    val fingerprint = "${extracted.payAmount}:${extracted.distance}"
                    val now = System.currentTimeMillis()
                    if (fingerprint == lastOfferFingerprint && (now - lastOfferBroadcastMs) < OFFER_DEDUP_WINDOW_MS) {
                        Log.d(TAG, "signalCapture: duplicate suppressed fingerprint=$fingerprint")
                        return
                    }
                    lastOfferFingerprint = fingerprint
                    lastOfferBroadcastMs = now
                    Log.i(TAG, "signalCapture: new offer fingerprint=$fingerprint — broadcasting")

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
            // CORRECT: "tap" mode waits for driver tap — only "accessibility"/"both" auto-trigger OCR
            // WRONG:   auto-triggering OCR in tap mode — defeats the purpose of tap-to-capture
            Log.i(TAG, "Auto-capture mode=$cachedAutoCapureMode -- triggering OCR capture fallback")
            val captureIntent = Intent(ACTION_OFFER_DETECTED)
            captureIntent.setPackage(packageName)
            sendBroadcast(captureIntent)
        } else {
            // CORRECT: tap/button/off modes wait for driver to tap camera button
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
        // Content descriptions — may expose map pin location or address fragments
        val contentDescs = mutableListOf<String>()
        val viewIds = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo) {
            val t = node.text?.toString()?.trim() ?: ""
            if (t.isNotBlank()) texts.add(t)
            // CORRECT: collect contentDescription — map views expose location here, not in text
            // WRONG:   text only — misses map pin descriptions and hidden address nodes
            val cd = node.contentDescription?.toString()?.trim() ?: ""
            if (cd.isNotBlank()) contentDescs.add(cd)
            val vid = node.viewIdResourceName?.toString()?.trim() ?: ""
            if (vid.isNotBlank()) viewIds.add(vid)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(root)

        // Log content descriptions and view IDs to Crashlytics for post-shift analysis
        // Goal: determine if map pin location or delivery town is exposed in accessibility tree
        if (BuildConfig.DEBUG) {
            val cdJoined = contentDescs.take(20).joinToString(" | ")
            val vidJoined = viewIds.take(20).joinToString(" | ")
            FirebaseCrashlytics.getInstance().log("MAP_DEBUG contentDesc: $cdJoined")
            FirebaseCrashlytics.getInstance().log("MAP_DEBUG viewIds: $vidJoined")
            Log.d(TAG, "MAP_DEBUG contentDesc: $cdJoined")
            Log.d(TAG, "MAP_DEBUG viewIds: $vidJoined")
            // Also write to debug log file for offline review
            try {
                val dir = java.io.File(
                    GigLensApp.instance.getExternalFilesDir(null), "debug"
                )
                dir.mkdirs()
                val file = java.io.File(dir, "map_debug.log")
                if (file.exists() && file.length() > 2 * 1024 * 1024) file.delete()
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                file.appendText("$timestamp CONTENT_DESCS: $cdJoined\n")
                file.appendText("$timestamp VIEW_IDS: $vidJoined\n")
            } catch (e: Exception) {
                Log.w(TAG, "MAP_DEBUG write failed: ${e.message}")
            }
        }
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
            // Restaurant: node immediately after "pickup" or "retail pickup" node
            // CORRECT: match both variants — DoorDash uses "Pickup" for food, "Retail pickup" for Wawa/7-Eleven
            // WRONG:   lower == "pickup" exact only — misses all retail delivery restaurants
            if (restaurant == null && (lower == "pickup" || lower == "retail pickup") && i + 1 < texts.size) {
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
