package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic)
// Last modified: DeepSeek (Ollama) - June 02 2026 - showRestartNotification() wired to onStop() + session health watchdog
// Foreground service that captures screenshots via MediaProjection API
// and runs ML Kit OCR to extract offer details.

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.R
import com.augusteenterprise.giglens.data.OfferCapture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.augusteenterprise.giglens.geocoding.GeocodingHelper
import com.augusteenterprise.giglens.location.LocationHelper
import com.augusteenterprise.giglens.ocr.OfferParser
import com.augusteenterprise.giglens.ocr.StreetExtractor
import com.augusteenterprise.giglens.scoring.OfferScorer
import com.augusteenterprise.giglens.service.EXTRA_NET_VALUE
import com.augusteenterprise.giglens.service.EXTRA_VERDICT
import com.augusteenterprise.giglens.service.EXTRA_PAY_AMOUNT
import com.augusteenterprise.giglens.service.EXTRA_RESTAURANT
import com.augusteenterprise.giglens.service.EXTRA_PICKUP_MILES
import com.augusteenterprise.giglens.service.EXTRA_TOTAL_MILES
import com.augusteenterprise.giglens.service.EXTRA_VEHICLE_COST
import com.augusteenterprise.giglens.service.EXTRA_TIME_COST
import com.augusteenterprise.giglens.service.EXTRA_TOTAL_COST
import com.augusteenterprise.giglens.service.EXTRA_MINUTES_ON_JOB
import com.augusteenterprise.giglens.service.EXTRA_SCORE
import com.augusteenterprise.giglens.service.EXTRA_COST_PER_MILE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile var isRunning = false
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    // Health watchdog — counts consecutive null frames from ImageReader
    // CORRECT: increment on null, reset on valid frame, trigger restart at 3
    // WRONG:   resetting counter inside captureScreen() success path only — misses silent death
    private var nullFrameCount = 0
    private val watchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            // CORRECT: check virtualDisplay/mediaProjection state — no need to acquire a real frame
            // WRONG: acquireLatestImage() just for a health check — wastes pixel buffer allocation every 30s
            // CORRECT: check mediaProjection only — VirtualDisplay is intentionally null between captures
            // WRONG: checking virtualDisplay != null — it's always null between offers now
            val sessionAlive = mediaProjection != null
            if (!sessionAlive) {
                nullFrameCount++
                Log.w(TAG, "Watchdog: session check failed $nullFrameCount/3")
                if (nullFrameCount >= 3) {
                    Log.e(TAG, "Watchdog: session appears dead — triggering restart notification")
                    showRestartNotification()
                    stopSelf()
                    return
                }
            } else {
                nullFrameCount = 0
            }
            watchdogHandler.postDelayed(this, 30_000L)
        }
    }
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Listens for capture signals from OfferDetectorService
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == OfferDetectorService.ACTION_OFFER_DETECTED) {
                Log.i(TAG, "Capture signal received — taking screenshot")
                captureScreen()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // CORRECT: flush pending crash reports on restart — START_STICKY restarts services not MainActivity
        // WRONG: relying on next MainActivity launch — never happens if only services restart
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().sendUnsentReports()

        // Get screen metrics
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        // Register broadcast receiver for offer detection signals
        val filter = IntentFilter(OfferDetectorService.ACTION_OFFER_DETECTED)
        androidx.core.content.ContextCompat.registerReceiver(
            this, captureReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Invalid MediaProjection result — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        // CORRECT: register callback before createVirtualDisplay() — required on Android 14+
        // WRONG:   calling createVirtualDisplay() without callback — crashes with IllegalStateException
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // CORRECT: null references before stopSelf() to prevent double-release in onDestroy()
                // WRONG:   releasing here AND in onDestroy() — double-stop corrupts session
                Log.i(TAG, "MediaProjection stopped by system")
                isRunning = false
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
                mediaProjection = null
                showRestartNotification()
                stopSelf()
            }
        }, null)

        // Set up ImageReader for screen capture
        // CORRECT: 1 frame buffer — we only ever need the latest frame
        // WRONG: 2 frame buffer — doubles memory footprint (~8MB extra at 1440p) constantly
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 1
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GigLens",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        watchdogHandler.postDelayed(watchdogRunnable, 30_000L)
        isRunning = true
        Log.i(TAG, "ScreenCaptureService started — ${screenWidth}x${screenHeight}")
        return START_STICKY
    }

    /**
     * Releases VirtualDisplay and ImageReader — called after each capture and on service stop.
     * CORRECT: release immediately after capture — frees ~8MB screen buffer between offers
     * WRONG: holding VirtualDisplay open entire shift — constant memory pressure
     */
    private fun releaseDisplayResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        Log.d(TAG, "VirtualDisplay + ImageReader released")
    }

    /**
     * Creates VirtualDisplay and ImageReader on demand for a single capture.
     * Called when offer is detected, destroyed immediately after capture.
     */
    private fun createDisplayResources(): Boolean {
        if (mediaProjection == null) {
            Log.e(TAG, "createDisplayResources: mediaProjection is null — cannot capture")
            return false
        }
        return try {
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 1
            )
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "GigLens",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )
            Log.d(TAG, "VirtualDisplay + ImageReader created for capture")
            virtualDisplay != null
        } catch (e: Exception) {
            // CORRECT: catch expired/revoked MediaProjection token — log and signal CAPTURE_DEAD
            // WRONG: letting exception propagate — crashes entire app process mid-shift
            Log.e(TAG, "createDisplayResources failed — MediaProjection token likely expired: ${e.message}")
            // CORRECT: record exception + flush immediately — broadcast receiver crashes kill process too fast
            // WRONG: relying on next app launch to flush — START_STICKY restarts services, not MainActivity
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().sendUnsentReports()
            mediaProjection = null
            isRunning = false
            // CORRECT: show persistent notification — survives full process death, one tap restarts capture
            // WRONG: CAPTURE_DEAD pill only — dies with process, driver has nothing to tap
            showRestartNotification()
            val deadIntent = android.content.Intent(
                applicationContext,
                com.augusteenterprise.giglens.service.OfferOverlayService::class.java
            )
            startService(deadIntent)
            false
        }
    }

    /**
     * Captures the current screen, saves the screenshot, and runs OCR.
     */
    private fun captureScreen() {
        // CORRECT: create VirtualDisplay just-in-time, release immediately after capture
        // WRONG: assuming VirtualDisplay is already open — it's deferred until offer detected
        if (!createDisplayResources()) {
            Log.e(TAG, "captureScreen: failed to create display resources — skipping")
            return
        }

        // Small delay to let VirtualDisplay render first frame
        android.os.SystemClock.sleep(200)

        val image = imageReader?.acquireLatestImage() ?: run {
            Log.w(TAG, "No image available from ImageReader")
            releaseDisplayResources()
            return
        }

        try {
            // Convert to bitmap
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to actual screen size (remove padding)
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            bitmap.recycle()

            // CORRECT: downsample to 50% before OCR — ML Kit reads text at any resolution
            // WRONG: passing full 1440p bitmap — 4x the memory for zero OCR accuracy gain
            val scale = 0.5f
            val ocrBitmap = Bitmap.createScaledBitmap(
                croppedBitmap,
                (screenWidth * scale).toInt(),
                (screenHeight * scale).toInt(),
                true
            )
            croppedBitmap.recycle()

            // CORRECT: release VirtualDisplay immediately after bitmap extracted — frees screen mirror
            // WRONG: holding VirtualDisplay open while OCR runs — unnecessary memory held during DB/network ops
            releaseDisplayResources()

            serviceScope.launch {
                val savedPath = saveScreenshot(ocrBitmap)
                runOcr(ocrBitmap, savedPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen: ${e.message}", e)
        } finally {
            image.close()
        }
    }

    /**
     * Saves bitmap to app-specific storage.
     */
    private fun saveScreenshot(bitmap: Bitmap): String? {
        return try {
            val dir = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "GigLens"
            )
            dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "offer_$timestamp.png")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving screenshot: ${e.message}", e)
            null
        }
    }

    /**
     * Runs ML Kit OCR on the bitmap, scores the offer, saves to DB, and shows overlay pill.
     * Author: Claude (Anthropic) - May 25 2026: Wired full scoring + overlay pipeline (was DB-only)
     *
     * CORRECT: parse → score → save to DB → launch OfferOverlayService
     * WRONG:   parse → save to DB (no score, no overlay — old broken behavior)
     */
    private fun runOcr(bitmap: Bitmap, screenshotPath: String?) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                // CORRECT: bitmap is still alive here — recycle after OCR completes
                // WRONG:   recycling before addOnSuccessListener fires
                val rawText = visionText.text
                Log.d(TAG, "OCR result (${rawText.length} chars)")

                val parsed = OfferParser.parse(rawText)

                if (parsed.isOfferScreen) {
                    Log.i(TAG, "Valid offer: \$${parsed.payAmount} | ${parsed.distance} mi | ${parsed.restaurant}")
                        FirebaseCrashlytics.getInstance().log("Offer detected: pay=${parsed.payAmount} dist=${parsed.distance} restaurant=${parsed.restaurant}")

                    serviceScope.launch {
                        val db = GigLensApp.instance.database
                        val captureDao = db.offerCaptureDao()
                        val configDao  = db.scorerConfigDao()
                        val scorer     = OfferScorer(configDao)

                        // Get driver location + estimate delivery distance
                        val location  = LocationHelper.getCurrentLocation(applicationContext)
                        val addresses = StreetExtractor.extract(rawText)
                        val distanceEstimate = GeocodingHelper.estimateDeliveryDistance(
                            pickupStreet  = addresses.pickupStreet,
                            dropoffStreet = addresses.dropoffStreet,
                            regionHint    = null
                        )

                        val pickupDistance: Double? =
                            if (location != null && distanceEstimate.pickupPoint != null) {
                                LocationHelper.straightLineDistance(
                                    location.latitude, location.longitude,
                                    distanceEstimate.pickupPoint.lat,
                                    distanceEstimate.pickupPoint.lon
                                ) * 1.3
                            } else null

                        val personalAvg = captureDao.getAverageScore()
                        val result = scorer.score(
                            payAmount        = parsed.payAmount,
                            deliveryDistance = parsed.distance,
                            personalAvgScore = personalAvg
                        )

                        if (result != null) {
                            Log.i(TAG, "Score: ${result.score} | Verdict: ${result.verdict} | Net: ${result.netValue}")

                            // Save full result to DB
                            val capture = OfferCapture(
                                payAmount      = parsed.payAmount,
                                distance       = parsed.distance,
                                restaurant     = parsed.restaurant,
                                screenshotPath = screenshotPath,
                                rawOcrText     = rawText,
                                platform       = detectPlatform(rawText),
                                score          = result.score,
                                verdict        = result.verdict.name,
                                payPerMile     = result.payPerMile,
                                vsPersonalAvg  = result.vsPersonalAvg,
                                driverLat      = location?.latitude,
                                driverLon      = location?.longitude,

                                totalDistance  = result.totalDistance,
                                truePayPerMile = result.truePayPerMile,
                                vehicleCost    = result.vehicleCost,
                                netValue       = result.netValue
                            )
                            captureDao.insert(capture)

                            // Launch floating pill overlay
                            val serviceIntent = Intent(applicationContext, OfferOverlayService::class.java).apply {
                                putExtra(EXTRA_NET_VALUE,      result.netValue)
                                putExtra(EXTRA_VERDICT,        result.verdict.name)
                                putExtra(EXTRA_PAY_AMOUNT,     parsed.payAmount)
                                putExtra(EXTRA_RESTAURANT,     parsed.restaurant ?: "")
                                putExtra(EXTRA_PICKUP_MILES,   parsed.distance ?: 0.0)
                                putExtra(EXTRA_TOTAL_MILES,    result.totalDistance ?: 0.0)
                                putExtra(EXTRA_VEHICLE_COST,   result.gasCost)
                                putExtra(EXTRA_TIME_COST,      result.wearTearCost)
                                putExtra(EXTRA_TOTAL_COST,     result.totalCost)
                                putExtra(EXTRA_MINUTES_ON_JOB, result.minutesOnJob)
                                putExtra(EXTRA_SCORE,          result.score)
                                putExtra(EXTRA_COST_PER_MILE,  result.costPerMileUsed)
                            }
                            Log.d(TAG, "Starting OfferOverlayService: verdict=${result.verdict} net=${result.netValue}")
                            startService(serviceIntent)
                        } else {
                            Log.w(TAG, "Scorer returned null — payAmount or distance missing")
                            screenshotPath?.let { File(it).delete() }
                        }
                    }
                } else {
                    Log.d(TAG, "OCR did not match offer pattern — skipping")
                    screenshotPath?.let { File(it).delete() }
                }
                // CORRECT: recycle after OCR success listener completes
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed: ${e.message}", e)
                if (!bitmap.isRecycled) bitmap.recycle()
            }
    }

    /**
     * Detects gig platform from OCR text.
     * CORRECT: check for platform-specific keywords
     * WRONG:   hardcoding "DoorDash" always
     */
    private fun detectPlatform(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("doordash") || lower.contains("dasher") -> "DoorDash"
            lower.contains("uber eats") || lower.contains("ubereats") -> "Uber Eats"
            lower.contains("grubhub") -> "Grubhub"
            lower.contains("instacart") -> "Instacart"
            else -> "Unknown"
        }
    }

    private fun showRestartNotification() {
        val intent = android.content.Intent(this, com.augusteenterprise.giglens.ui.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            // CORRECT: pass restart_capture flag — MainActivity auto-triggers MediaProjection re-grant
            // WRONG: launching MainActivity without flag — driver has to manually find the capture button
            putExtra("restart_capture", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, GigLensApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("GigLens — Screen capture stopped")
            .setContentText("Tap to re-enable screen capture")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(1002, notification)
        Log.i(TAG, "Restart notification shown")
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, GigLensApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // CORRECT: only stop mediaProjection if it hasn't already stopped via callback
        // WRONG:   always calling mediaProjection?.stop() — causes double-stop crash
        isRunning = false
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        releaseDisplayResources()
        if (mediaProjection != null) {
            mediaProjection?.stop()
            mediaProjection = null
        }
        nullFrameCount = 0
        watchdogHandler.removeCallbacks(watchdogRunnable)
        serviceJob.cancel()
        // CORRECT: close textRecognizer to release ML Kit native resources
        // WRONG: omitting close() — TextRecognizer holds native handles that won't be GC'd
        try { textRecognizer.close() } catch (e: Exception) {
            Log.w(TAG, "textRecognizer.close() failed: ${e.message}")
        }
        Log.i(TAG, "ScreenCaptureService destroyed")
        super.onDestroy()
    }
}
