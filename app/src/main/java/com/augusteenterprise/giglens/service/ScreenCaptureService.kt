package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic)
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
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.R
import com.augusteenterprise.giglens.data.OfferCapture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.augusteenterprise.giglens.ocr.OfferParser
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

        var isRunning = false
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
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

        // Set up ImageReader for screen capture
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GigLens",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        isRunning = true
        Log.i(TAG, "ScreenCaptureService started — ${screenWidth}x${screenHeight}")
        return START_STICKY
    }

    /**
     * Captures the current screen, saves the screenshot, and runs OCR.
     */
    private fun captureScreen() {
        val image = imageReader?.acquireLatestImage() ?: run {
            Log.w(TAG, "No image available from ImageReader")
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
            if (croppedBitmap != bitmap) bitmap.recycle()

            // Save screenshot and run OCR
            serviceScope.launch {
                val savedPath = saveScreenshot(croppedBitmap)
                runOcr(croppedBitmap, savedPath)
                croppedBitmap.recycle()
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
     * Runs ML Kit OCR on the bitmap and stores parsed results in Room DB.
     */
    private fun runOcr(bitmap: Bitmap, screenshotPath: String?) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                Log.d(TAG, "OCR result (${rawText.length} chars): ${rawText.take(200)}")

                val parsed = OfferParser.parse(rawText)

                if (parsed.isOfferScreen) {
                    Log.i(TAG, "Valid offer: \$${parsed.payAmount} | ${parsed.distance} mi | ${parsed.restaurant}")

                    serviceScope.launch {
                        val capture = OfferCapture(
                            payAmount = parsed.payAmount,
                            distance = parsed.distance,
                            restaurant = parsed.restaurant,
                            screenshotPath = screenshotPath,
                            rawOcrText = rawText
                        )
                        val id = GigLensApp.instance.database.offerCaptureDao().insert(capture)
                        Log.i(TAG, "Offer saved to DB with id=$id")
                    }
                } else {
                    Log.d(TAG, "OCR text did not match offer pattern — skipping")
                    // Delete the screenshot if it wasn't an offer
                    screenshotPath?.let { File(it).delete() }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed: ${e.message}", e)
            }
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
        isRunning = false
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        serviceJob.cancel()
        Log.i(TAG, "ScreenCaptureService destroyed")
        super.onDestroy()
    }
}
