package com.augusteenterprise.giglens.service
// Author: Claude - Feature #8: Floating camera button for manual capture trigger
// Shows a small draggable GigLens button over supported gig apps.
// Driver taps it to trigger ScreenCaptureService without sharing a screenshot.
// Only visible when auto_capture_mode = "button" or "both" AND a supported
// platform is in the foreground.

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.AppConfigKeys
import com.augusteenterprise.giglens.data.PlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "CaptureButtonService"
private const val CHANNEL_ID = "giglens_capture_btn"

class CaptureButtonService : Service() {

    companion object {
        var isRunning = false
            private set
        const val ACTION_SHOW = "com.augusteenterprise.giglens.CAPTURE_BTN_SHOW"
        const val ACTION_HIDE = "com.augusteenterprise.giglens.CAPTURE_BTN_HIDE"
    }

    private lateinit var windowManager: WindowManager
    private var btnView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAdded = false

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Position
    private var posX = 20
    private var posY = 600

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(2, buildNotification())
        showButton()
        isRunning = true
        Log.i(TAG, "CaptureButtonService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> { if (!isViewAdded) showButton() }
            ACTION_HIDE -> { hideButton() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        hideButton()
        Log.i(TAG, "CaptureButtonService destroyed")
        super.onDestroy()
    }

    // ── Build floating camera button ──────────────────────────────────────────
    private fun showButton() {
        if (isViewAdded) return

        val btn = TextView(this).apply {
            text = "📷"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC00BFA5"))  // brand teal, semi-transparent
                cornerRadius = 50f
            }
            elevation = 8f
            setOnTouchListener(makeTouchListener())
            contentDescription = "GigLens capture button"
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
        }

        btnView = btn
        layoutParams = params
        windowManager.addView(btn, params)
        isViewAdded = true
        Log.d(TAG, "Capture button shown at $posX,$posY")
    }

    private fun hideButton() {
        btnView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {
                Log.e(TAG, "Error removing button: ${e.message}", e)
            }
        }
        btnView = null
        isViewAdded = false
        Log.d(TAG, "Capture button hidden")
    }

    // ── Touch listener — drag + tap ───────────────────────────────────────────
    private fun makeTouchListener() = View.OnTouchListener { _, event ->
        val params = layoutParams ?: return@OnTouchListener false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                    isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    posX = params.x
                    posY = params.y
                    btnView?.let { windowManager.updateViewLayout(it, params) }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // Tap — trigger capture
                    onCaptureTapped()
                }
                true
            }
            else -> false
        }
    }

    // ── Trigger capture on tap ────────────────────────────────────────────────
    // CORRECT: check ScreenCaptureService is running before broadcasting — show warning if dead
    // WRONG:   blindly broadcasting with no receiver alive — tap appears to do nothing
    private fun onCaptureTapped() {
        Log.i(TAG, "Capture button tapped — signaling ScreenCaptureService")

        if (!ScreenCaptureService.isRunning) {
            Log.w(TAG, "ScreenCaptureService not running — showing warning")
            android.widget.Toast.makeText(
                this,
                "Screen capture stopped. Re-enable in GigLens.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            // Flash red to signal failure
            btnView?.apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CCF44336"))  // flash red
                    cornerRadius = 50f
                }
                postDelayed({
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#CC00BFA5"))  // back to teal
                        cornerRadius = 50f
                    }
                }, 600)
            }
            return
        }

        // Flash button green to confirm tap
        btnView?.apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC4CAF50"))  // flash green
                cornerRadius = 50f
            }
            postDelayed({
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC00BFA5"))  // back to teal
                    cornerRadius = 50f
                }
            }, 300)
        }

        // Send capture signal to ScreenCaptureService
        val intent = Intent(OfferDetectorService.ACTION_OFFER_DETECTED)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "GigLens Capture Button",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "GigLens floating capture button" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GigLens")
            .setContentText("Tap 📷 button to capture an offer")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
