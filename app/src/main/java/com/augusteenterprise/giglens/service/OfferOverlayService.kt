package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic) - Feature #8: Removed blink — static countdown timer
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
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.ScorerConfigKeys
import kotlinx.coroutines.runBlocking

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
const val EXTRA_DELIVERY_TOWN  = "delivery_town"   // estimated delivery city/town
const val ACTION_SHOW_CAMERA   = "com.augusteenterprise.giglens.SHOW_CAMERA"
const val ACTION_HIDE_CAMERA   = "com.augusteenterprise.giglens.HIDE_CAMERA"

private enum class SheetState { IDLE, CAMERA, PROCESSING, PILL, MINI, FULL, CAPTURE_DEAD }

class OfferOverlayService : Service() {

    companion object {
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var sheetState = SheetState.IDLE
    private var isViewAdded = false
    private var captureWasEverRunning = false  // tracks if capture ran at least once this session

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
    private var deliveryTown = "📍 ---"   // estimated delivery town
    private var verdict      = "UNKNOWN"
    private var payAmount    = 0.0
    private var restaurant   = ""
    private var pickupMiles  = 0.0
    private var totalMiles   = 0.0
    private var gasCost      = 0.0
    private var wearTearCost = 0.0
    private var totalCost    = 0.0
    private var minutesOnJob = 0.0
    private var score        = 0
    private var costPerMile  = 0.90

    // Configurable auto-revert timer — result pill reverts to IDLE if no new offer
    private val revertHandler = Handler(Looper.getMainLooper())
    private var revertRunnable: Runnable? = null
    private var tickRunnable: Runnable? = null
    private var secondsRemaining = 60
    private var blinkVisible = true  // toggles each tick when countdown < 10s
    private fun revertDelaySeconds(): Int = runBlocking {
        (GigLensApp.instance.database.scorerConfigDao()
            .getValue(ScorerConfigKeys.RESULT_DISPLAY_SECONDS) ?: 60.0).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        // CORRECT: always call startForeground() — service is always started from MainActivity
        //          (foreground context) so ForegroundServiceStartNotAllowedException never fires
        // WRONG: running as background service on Android 12+ — Android kills it mid-shift
        //        when memory is needed, taking the overlay pill with it
        startForeground(1, buildNotification())
        // CORRECT: showWidget() called from onStartCommand via widget_enabled intent extra
        // WRONG: calling showWidget() in onCreate — no intent available, runBlocking deadlocks Room
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CORRECT: verify overlay window is still attached on every onStartCommand — re-add silently if lost
        // WRONG: assuming isViewAdded means window is valid — process restart loses window without clearing flag
        // CORRECT: check WIDGET_ENABLED before re-adding — don't show pill if toggle is off
        // WRONG: calling showWidget() unconditionally — pill appears even when driver disabled it
        // CORRECT: read widgetEnabled from intent extra — set by caller after DB write confirms
        // WRONG: runBlocking DB read on main thread — deadlocks with Room dispatcher, reads stale value
        val widgetEnabled = intent?.getBooleanExtra("widget_enabled", true) ?: true
        Log.d(TAG, "onStartCommand: widgetEnabled=$widgetEnabled isViewAdded=$isViewAdded action=${intent?.action}")
        if (widgetEnabled) {
            if (isViewAdded && rootView != null && layoutParams != null) {
                try {
                    windowManager.updateViewLayout(rootView!!, layoutParams!!)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Overlay window lost — re-adding silently: ${e.message}")
                    isViewAdded = false
                    // CORRECT: reset sheetState to IDLE on a silent re-attach -- the window was
                    //          lost (e.g. service process restart) but sheetState in memory still
                    //          holds whatever offer state was last rendered, which would otherwise
                    //          get drawn again on the NEXT SHOW_CAMERA/HIDE_CAMERA broadcast, showing
                    //          stale offer data on a screen where no current offer exists
                    // WRONG:   leaving sheetState untouched -- a stray broadcast arriving after this
                    //          silent re-attach (e.g. from OfferDetectorService restarting mid-cycle)
                    //          calls updateWidget() against the OLD sheetState, rendering a past
                    //          offer's pay/restaurant/verdict on an unrelated foreground app
                    sheetState = SheetState.IDLE
                    showWidget()
                }
            } else if (!isViewAdded) {
                Log.d(TAG, "onStartCommand: isViewAdded=false — calling showWidget()")
                // CORRECT: same reset as above -- a fresh attach (service just created/recreated)
                //          should never inherit stale in-memory sheetState from before
                sheetState = SheetState.IDLE
                showWidget()
            }
        } else {
            Log.w(TAG, "onStartCommand: widgetEnabled=false — skipping showWidget()")
        }
        when (intent?.action) {
            ACTION_SHOW_CAMERA -> {
                // CORRECT: guard duplicate -- if already CAMERA, ignore repeat signal
                // WRONG: unconditional updateWidget() on every SHOW_CAMERA -- re-init corrupts view mid-morph
                captureWasEverRunning = true
                cancelRevertTimer()
                if (sheetState != SheetState.CAMERA) {
                    sheetState = SheetState.CAMERA
                    if (isViewAdded) updateWidget() else { showWidget(); updateWidget() }
                    Log.d(TAG, "Widget morphed to CAMERA state")
                } else {
                    Log.d(TAG, "SHOW_CAMERA ignored -- already in CAMERA state")
                }
                // CORRECT: do NOT stopSelf() -- overlay must stay alive entire shift
                // WRONG: stopSelf(startId) after SHOW_CAMERA -- kills service, orphans window,
                //        next SHOW_CAMERA restarts fresh with isViewAdded=false, pill disappears
            }
            ACTION_HIDE_CAMERA -> {
                // Offer screen gone -- morph back to pill or idle
                // CORRECT: do NOT stopSelf() -- overlay must stay alive entire shift
                // WRONG: stopSelf(startId) -- kills service between offers, pill disappears
                if (sheetState == SheetState.CAMERA || sheetState == SheetState.PROCESSING) {
                    sheetState = if (verdict == "UNKNOWN") SheetState.IDLE else SheetState.PILL
                    if (isViewAdded) updateWidget() else { showWidget(); updateWidget() }
                    Log.d(TAG, "Widget morphed back from CAMERA to $sheetState")
                }
            }
            else -> {
                // CORRECT: only show CAPTURE_DEAD when explicitly signaled via ACTION_CAPTURE_DEAD intent
                // WRONG: auto-triggering CAPTURE_DEAD on null-action intent when ScreenCaptureService dead
                //        accessibility extraction works without ScreenCaptureService -- no reason to block
                if (intent?.hasExtra(EXTRA_NET_VALUE) == true) {
                    captureWasEverRunning = true
                    loadExtras(intent)
                    sheetState = SheetState.PILL
                    startRevertTimer()
                    if (isViewAdded) {
                        updateWidget()
                        Log.d(TAG, "Result pill shown -- revert timer started (${secondsRemaining}s)")
                    } else {
                        showWidget()
                        updateWidget()
                    }
                }
            }
        }
        return START_STICKY  // restart if killed
    }

    // ── Configurable auto-revert timer with countdown tick ────────────────────
    private fun startRevertTimer() {
        cancelRevertTimer()
        // CORRECT: read from DB via revertDelaySeconds() -- configurable in Settings
        // WRONG: hardcoding 30 -- operator cannot adjust without a rebuild
        secondsRemaining = revertDelaySeconds().coerceAtMost(30)
        Log.d(TAG, "Revert timer started (${secondsRemaining}s)")
        // Tick every second -- update countdown on pill, revert to IDLE at 0
        tickRunnable = object : Runnable {
            override fun run() {
                secondsRemaining--
                if (secondsRemaining <= 0) {
                    sheetState = SheetState.IDLE
                    verdict = "UNKNOWN"
                    if (isViewAdded) updateWidget()
                    Log.d(TAG, "Revert timer expired -- pill reverted to IDLE")
                    return
                }
                if (isViewAdded) updateWidget()
                revertHandler.postDelayed(this, 1000L)
            }
        }
        revertHandler.postDelayed(tickRunnable!!, 1000L)
    }
    private fun cancelRevertTimer() {
        revertRunnable?.let { revertHandler.removeCallbacks(it) }
        tickRunnable?.let { revertHandler.removeCallbacks(it) }
        revertRunnable = null
        tickRunnable = null
    }


    // ── Screen flash feedback on camera tap ───────────────────────────────────
    private fun flashScreen() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val flashView = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            alpha = 0.85f
        }
        val flashParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(flashView, flashParams)
            android.animation.ObjectAnimator.ofFloat(flashView, "alpha", 0.85f, 0f).apply {
                duration = 180L
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        try { windowManager.removeView(flashView) } catch (e: Exception) {}
                    }
                })
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "flashScreen failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cancelRevertTimer()
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
        gasCost      = intent.getDoubleExtra(EXTRA_VEHICLE_COST,   0.0)
        wearTearCost = intent.getDoubleExtra(EXTRA_TIME_COST,      0.0)
        totalCost    = intent.getDoubleExtra(EXTRA_TOTAL_COST,     0.0)
        minutesOnJob = intent.getDoubleExtra(EXTRA_MINUTES_ON_JOB, 0.0)
        score        = intent.getIntExtra(EXTRA_SCORE,             0)
        costPerMile  = intent.getDoubleExtra(EXTRA_COST_PER_MILE,  0.90)
        deliveryTown = intent.getStringExtra(EXTRA_DELIVERY_TOWN) ?: "📍 ---"
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
        // CORRECT: show net value only — no countdown during shift
        // WRONG: showing countdown timer — distracting and meaningless if pill never reverts
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
            text = if (sheetState == SheetState.IDLE)
                SpannableString("GL")
            else
                netLabelSpannable()  // net value white, timer blinks when <10s
            setTextColor(Color.WHITE)
            textSize = if (sheetState == SheetState.IDLE) 12f else 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(40, 18, 40, 18)
            background = pillBg(color, hasDrawer)
            setOnTouchListener(makeTouchListener())
        }
    }

    // ── Build pill text with ONLY the timer segment blinking ──────────────────
    private fun netLabelSpannable(): SpannableString {
        // CORRECT: show net value + countdown -- driver knows verdict AND time remaining
        // WRONG: plain net value only -- no urgency signal for driver
        val sign = if (netValue >= 0) "+" else ""
        val net = "$sign$${"%.2f".format(netValue)}"
        return if (secondsRemaining > 0) {
            SpannableString("$net ${secondsRemaining}s")
        } else {
            SpannableString(net)
        }
    }

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
            try {
                windowManager.addView(root, params)
                isViewAdded = true
                Log.d(TAG, "Widget added to WindowManager")
            } catch (e: android.view.WindowManager.BadTokenException) {
                Log.e(TAG, "Overlay permission not granted — cannot add widget: ${e.message}")
                stopSelf()
            }
        } else {
            Log.d(TAG, "Widget already added — skipping addView")
        }
    }

    // ── Update widget in place ────────────────────────────────────────────────
    private fun updateWidget() {
        val root = rootView ?: run { Log.w(TAG, "updateWidget: rootView is null!"); return }
        val params = layoutParams ?: run { Log.w(TAG, "updateWidget: layoutParams is null!"); return }
        Log.d(TAG, "updateWidget rendering state=$sheetState")
        val color = if (sheetState == SheetState.IDLE)
            Color.parseColor("#666666") else verdictColor()

        root.removeAllViews()

        when (sheetState) {
            SheetState.CAPTURE_DEAD -> {
                // CORRECT: show warning pill — driver knows capture stopped and needs to restart
                // WRONG: disappearing entirely — driver thinks app is working when it isn't
                val deadColor = Color.parseColor("#E24B4A")
                val btn = TextView(this).apply {
                    text = "⚠️ Tap"
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(24, 18, 24, 18)
                    background = GradientDrawable().apply {
                        setColor(deadColor)
                        cornerRadius = 50f
                    }
                    // CORRECT: use touch listener only — handles both drag and tap in one place
                    // WRONG: setOnClickListener + setOnTouchListener — touch consumes event, click never fires
                    setOnTouchListener { _, event ->
                        val params = layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                initialX = params.x
                                initialY = params.y
                                initialTouchX = event.rawX
                                initialTouchY = event.rawY
                                isDragging = false
                                true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
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
                            android.view.MotionEvent.ACTION_UP -> {
                                if (!isDragging) {
                                    // Tap — relaunch MainActivity to re-request MediaProjection
                                    val intent = android.content.Intent(
                                        this@OfferOverlayService,
                                        com.augusteenterprise.giglens.ui.MainActivity::class.java
                                    ).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra("restart_capture", true)
                                    }
                                    startActivity(intent)
                                    Log.i(TAG, "Driver tapped CAPTURE_DEAD pill — launching MainActivity for restart")
                                }
                                true
                            }
                            else -> false
                        }
                    }
                }
                root.addView(btn)
            }

            SheetState.IDLE -> {
                root.addView(buildPill(color, false))
            }

            SheetState.CAMERA -> {
                // Teal camera button — offer screen detected, waiting for capture
                val camColor = Color.parseColor("#00BFA5")
                val btn = TextView(this).apply {
                    text = "📷"
                    textSize = 20f
                    setPadding(24, 18, 24, 18)
                    background = GradientDrawable().apply {
                        setColor(camColor)
                        cornerRadius = 50f
                    }
                    setOnTouchListener(makeCameraTouchListener())
                }
                root.addView(btn)
            }

            SheetState.PROCESSING -> {
                // Processing indicator — capture triggered
                val camColor = Color.parseColor("#00BFA5")
                val btn = TextView(this).apply {
                    text = "⏳"
                    textSize = 20f
                    setPadding(24, 18, 24, 18)
                    background = GradientDrawable().apply {
                        setColor(camColor)
                        cornerRadius = 50f
                    }
                }
                root.addView(btn)
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
                val tvTownMini = TextView(this).apply {
                    text = deliveryTown
                    setTextColor(Color.parseColor("#00C9A7"))
                    textSize = 11f
                    setPadding(0, 2, 0, 0)
                }
                val tvOneLiner = TextView(this).apply {
                    text = "$${"%.2f".format(payAmount)} · ${"%.1f".format(totalMiles)}mi est."
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 10f
                    setPadding(0, 4, 0, 0)
                }
                val tvCosts = TextView(this).apply {
                    text = "Gas \$${"%.2f".format(gasCost)} · W&T \$${"%.2f".format(wearTearCost)}"
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
                drawer.addView(tvTownMini)
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
                drawer.addView(tv(deliveryTown, 11f, Color.parseColor("#00C9A7"), padTop = 2))
                drawer.addView(divider())
                drawer.addView(rowLayout("Pay", "$${"%.2f".format(payAmount)}"))
                drawer.addView(rowLayout("To pickup", "${"%.1f".format(pickupMiles)} mi"))
                drawer.addView(rowLayout("Est. total", "${"%.1f".format(totalMiles)} mi"))
                drawer.addView(divider())
                drawer.addView(rowLayout("Gas cost", "$${"%.2f".format(gasCost)}"))
                drawer.addView(rowLayout("Wear & tear", "$${"%.2f".format(wearTearCost)}"))
                drawer.addView(rowLayout("Total cost", "$${"%.2f".format(gasCost + wearTearCost)}"))
                drawer.addView(divider())
                drawer.addView(rowLayout("Delivery town", deliveryTown, Color.parseColor("#00C9A7")))
                drawer.addView(rowLayout("Net value", netLabel(), color))
                drawer.addView(rowLayout("Score", "$score / 100"))
                drawer.addView(tv("tap to close", 8f,
                    Color.parseColor("#444444"), padTop = 10))

                drawer.setOnTouchListener(makeTouchListener())
                root.addView(drawer)
            }
        }

        windowManager.updateViewLayout(root, params)
        root.invalidate()
        root.requestLayout()
    }

    // ── Camera touch listener — drag + tap to trigger capture ──────────────────
    private fun makeCameraTouchListener() = View.OnTouchListener { _, event ->
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
                    // Tap — flash screen then trigger capture
                    flashScreen()
                    sheetState = SheetState.PROCESSING
                    updateWidget()
                    Log.i(TAG, "Camera button tapped — triggering capture")
                    val intent = Intent(OfferDetectorService.ACTION_OFFER_DETECTED)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
                true
            }
            else -> false
        }
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
                        SheetState.IDLE         -> SheetState.IDLE
                        SheetState.CAMERA       -> SheetState.CAMERA     // handled by camera listener
                        SheetState.PROCESSING   -> SheetState.PROCESSING // wait for result
                        SheetState.PILL         -> SheetState.MINI
                        SheetState.MINI         -> SheetState.FULL
                        SheetState.FULL         -> SheetState.PILL
                        SheetState.CAPTURE_DEAD -> SheetState.CAPTURE_DEAD // tap handled by button listener
                    }
                    // Pill persists — no revert timer on interaction
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
