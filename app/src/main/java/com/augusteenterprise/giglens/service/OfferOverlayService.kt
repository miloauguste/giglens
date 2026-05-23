package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic) - Fixed duplicate pill on repeated shares (isViewAdded flag)
// Persistent floating pill widget. Always visible when enabled in Settings.
// Pill + drawer render as one draggable unit via WindowManager.
// States: IDLE → PILL(result) → MINI(drawer) → FULL(detail) → PILL

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
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

private const val TAG = "OfferOverlayService"
private const val CHANNEL_ID = "giglens_overlay"

const val EXTRA_NET_VALUE      = "net_value"
const val EXTRA_VERDICT        = "verdict"
const val EXTRA_PAY_AMOUNT     = "pay_amount"
const val EXTRA_RESTAURANT     = "restaurant"
const val EXTRA_PICKUP_MILES   = "pickup_miles"
const val EXTRA_TOTAL_MILES    = "total_miles"
const val EXTRA_VEHICLE_COST   = "vehicle_cost"
const val EXTRA_TIME_COST      = "time_cost"
const val EXTRA_TOTAL_COST     = "total_cost"
const val EXTRA_MINUTES_ON_JOB = "minutes_on_job"
const val EXTRA_SCORE          = "score"
const val EXTRA_COST_PER_MILE  = "cost_per_mile"

private enum class SheetState { IDLE, PILL, MINI, FULL }

class OfferOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var sheetState = SheetState.IDLE
    private var isViewAdded = false

    // Position
    private var posX = 30
    private var posY = 200

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Offer data
    private var netValue     = 0.0
    private var verdict      = "UNKNOWN"
    private var payAmount    = 0.0
    private var restaurant   = ""
    private var pickupMiles  = 0.0
    private var totalMiles   = 0.0
    private var vehicleCost  = 0.0
    private var timeCost     = 0.0
    private var totalCost    = 0.0
    private var minutesOnJob = 0.0
    private var score        = 0
    private var costPerMile  = 0.90

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, buildNotification())
        showWidget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra(EXTRA_NET_VALUE)) {
                loadExtras(it)
                sheetState = SheetState.PILL
                if (isViewAdded) {
                    // Reuse existing pill — just update content
                    updateWidget()
                    Log.d(TAG, "Reusing existing pill for new offer")
                } else {
                    // First offer — build the view
                    showWidget()
                    updateWidget()
                }
            }
        }
        return START_STICKY  // restart if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        rootView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        isViewAdded = false
    }

    // ── Load result data ──────────────────────────────────────────────────────
    private fun loadExtras(intent: Intent) {
        netValue     = intent.getDoubleExtra(EXTRA_NET_VALUE,      0.0)
        verdict      = intent.getStringExtra(EXTRA_VERDICT)        ?: "UNKNOWN"
        payAmount    = intent.getDoubleExtra(EXTRA_PAY_AMOUNT,     0.0)
        restaurant   = intent.getStringExtra(EXTRA_RESTAURANT)     ?: ""
        pickupMiles  = intent.getDoubleExtra(EXTRA_PICKUP_MILES,   0.0)
        totalMiles   = intent.getDoubleExtra(EXTRA_TOTAL_MILES,    0.0)
        vehicleCost  = intent.getDoubleExtra(EXTRA_VEHICLE_COST,   0.0)
        timeCost     = intent.getDoubleExtra(EXTRA_TIME_COST,      0.0)
        totalCost    = intent.getDoubleExtra(EXTRA_TOTAL_COST,     0.0)
        minutesOnJob = intent.getDoubleExtra(EXTRA_MINUTES_ON_JOB, 0.0)
        score        = intent.getIntExtra(EXTRA_SCORE,             0)
        costPerMile  = intent.getDoubleExtra(EXTRA_COST_PER_MILE,  0.90)
        Log.d(TAG, "Result: verdict=$verdict net=$netValue restaurant=$restaurant")
    }

    // ── Colors ────────────────────────────────────────────────────────────────
    private fun verdictColor() = when (verdict) {
        "TAKE"       -> Color.parseColor("#4CAF50")
        "BORDERLINE" -> Color.parseColor("#FF9800")
        "SKIP"       -> Color.parseColor("#E24B4A")
        else         -> Color.parseColor("#666666")
    }

    private fun netLabel(): String {
        val sign = if (netValue >= 0) "+" else ""
        return "$sign$${"%.2f".format(netValue)}"
    }

    private fun pillBg(color: Int, topLeft: Boolean = true): GradientDrawable {
        val r = 50f
        // Square bottom-left corner when drawer is open (anchors to drawer)
        val radii = if (topLeft)
            floatArrayOf(r, r, r, r, r, r, 0f, 0f)  // bottom-left square
        else
            floatArrayOf(r, r, r, r, r, r, r, r)     // all round
        return GradientDrawable().apply {
            setColor(color)
            cornerRadii = radii
        }
    }

    private fun drawerBg(color: Int): GradientDrawable {
        val r = 16f
        // Top-left square to attach to pill
        return GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A1A"))
            cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, r, r)
            setStroke(3, color)
        }
    }

    // ── Build pill TextView ───────────────────────────────────────────────────
    private fun buildPill(color: Int, hasDrawer: Boolean): TextView {
        return TextView(this).apply {
            text = if (sheetState == SheetState.IDLE) "GL" else netLabel()
            setTextColor(Color.WHITE)
            textSize = if (sheetState == SheetState.IDLE) 12f else 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(40, 18, 40, 18)
            background = pillBg(color, hasDrawer)
            setOnTouchListener(makeTouchListener())
        }
    }

    // ── Show initial widget (idle state) ──────────────────────────────────────
    private fun showWidget() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        root.addView(buildPill(Color.parseColor("#666666"), false))

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

        rootView = root
        layoutParams = params
        if (!isViewAdded) {
            windowManager.addView(root, params)
            isViewAdded = true
            Log.d(TAG, "Widget added to WindowManager")
        } else {
            Log.d(TAG, "Widget already added — skipping addView")
        }
    }

    // ── Update widget in place ────────────────────────────────────────────────
    private fun updateWidget() {
        val root = rootView ?: return
        val params = layoutParams ?: return
        val color = if (sheetState == SheetState.IDLE)
            Color.parseColor("#666666") else verdictColor()

        root.removeAllViews()

        when (sheetState) {
            SheetState.IDLE -> {
                root.addView(buildPill(color, false))
            }

            SheetState.PILL -> {
                root.addView(buildPill(color, false))
            }

            SheetState.MINI -> {
                // Pill with square bottom-left corner
                root.addView(buildPill(color, true))

                // Mini drawer directly below
                val drawer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 16, 24, 16)
                    background = drawerBg(color)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    minimumWidth = 400
                }

                val tvRest = TextView(this).apply {
                    text = restaurant
                    setTextColor(Color.parseColor("#CCCCCC"))
                    textSize = 11f
                }
                val tvOneLiner = TextView(this).apply {
                    text = "$${"%.2f".format(payAmount)} · ${"%.1f".format(totalMiles)}mi est."
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 10f
                    setPadding(0, 4, 0, 0)
                }
                val tvCosts = TextView(this).apply {
                    text = "Vehicle \$${"%.2f".format(vehicleCost)} · Time \$${"%.2f".format(timeCost)}"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 9f
                    setPadding(0, 2, 0, 0)
                }
                val tvHint = TextView(this).apply {
                    text = "tap for full detail"
                    setTextColor(Color.parseColor("#444444"))
                    textSize = 8f
                    setPadding(0, 8, 0, 0)
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                drawer.addView(tvRest)
                drawer.addView(tvOneLiner)
                drawer.addView(tvCosts)
                drawer.addView(tvHint)
                drawer.setOnTouchListener(makeTouchListener())
                root.addView(drawer)
            }

            SheetState.FULL -> {
                // Pill with square bottom-left corner
                root.addView(buildPill(color, true))

                // Full detail drawer
                val drawer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 16, 24, 16)
                    background = drawerBg(color)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    minimumWidth = 420
                }

                fun tv(text: String, size: Float, color: Int,
                       bold: Boolean = false, padTop: Int = 0): TextView {
                    return TextView(this@OfferOverlayService).apply {
                        this.text = text
                        setTextColor(color)
                        textSize = size
                        if (bold) setTypeface(null, Typeface.BOLD)
                        if (padTop > 0) setPadding(0, padTop, 0, 0)
                    }
                }

                fun divider(): View = View(this).apply {
                    setBackgroundColor(Color.parseColor("#2A2A2A"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        setMargins(0, 8, 0, 8)
                    }
                }

                fun rowLayout(label: String, value: String, valColor: Int =
                    Color.parseColor("#DDDDDD")): LinearLayout {
                    return LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(TextView(this@OfferOverlayService).apply {
                            text = label
                            setTextColor(Color.parseColor("#888888"))
                            textSize = 10f
                            layoutParams = LinearLayout.LayoutParams(0,
                                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(TextView(this@OfferOverlayService).apply {
                            text = value
                            setTextColor(valColor)
                            textSize = 10f
                            gravity = Gravity.END
                            layoutParams = LinearLayout.LayoutParams(0,
                                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                    }
                }

                drawer.addView(tv(netLabel(), 18f, color, bold = true))
                drawer.addView(tv(restaurant, 11f, Color.parseColor("#CCCCCC"), padTop = 4))
                drawer.addView(divider())
                drawer.addView(rowLayout("Pay", "$${"%.2f".format(payAmount)}"))
                drawer.addView(rowLayout("To pickup", "${"%.1f".format(pickupMiles)} mi"))
                drawer.addView(rowLayout("Est. total", "${"%.1f".format(totalMiles)} mi"))
                drawer.addView(divider())
                drawer.addView(rowLayout("Vehicle (${"$%.2f".format(costPerMile)}/mi)", "$${"%.2f".format(vehicleCost)}"))
                drawer.addView(rowLayout("Time (${"%.0f".format(minutesOnJob)}min)",
                    "$${"%.2f".format(timeCost)}"))
                drawer.addView(rowLayout("Total cost", "$${"%.2f".format(totalCost)}"))
                drawer.addView(divider())
                drawer.addView(rowLayout("Net value", netLabel(), color))
                drawer.addView(rowLayout("Score", "$score / 100"))
                drawer.addView(tv("tap to close", 8f,
                    Color.parseColor("#444444"), padTop = 10))

                drawer.setOnTouchListener(makeTouchListener())
                root.addView(drawer)
            }
        }

        windowManager.updateViewLayout(root, params)
    }

    // ── Touch listener — drag + tap to cycle states ───────────────────────────
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
                    rootView?.let { windowManager.updateViewLayout(it, params) }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // Tap — cycle states
                    sheetState = when (sheetState) {
                        SheetState.IDLE -> SheetState.IDLE
                        SheetState.PILL -> SheetState.MINI
                        SheetState.MINI -> SheetState.FULL
                        SheetState.FULL -> SheetState.PILL
                    }
                    updateWidget()
                }
                true
            }
            else -> false
        }
    }

    // ── Foreground notification ───────────────────────────────────────────────
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "GigLens Widget",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "GigLens floating offer widget" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GigLens")
            .setContentText("Offer widget active")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
