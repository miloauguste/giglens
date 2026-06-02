package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic)
// Last modified: DeepSeek (Ollama) - June 02 2026 - Full analytics UI rebuild
// Main activity: analytics dashboard with controls, earnings, chart, verdict breakdown

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.DailyNetValue
import com.augusteenterprise.giglens.data.OfferCapture
import com.augusteenterprise.giglens.databinding.ActivityMainBinding
import com.augusteenterprise.giglens.service.ScreenCaptureService
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    // LIVE badge animator
    // CORRECT: ObjectAnimator smooth ease-in-out — never setVisibility flash
    // WRONG:   Handler + postDelayed toggling visibility — causes harsh flash
    private var liveBadgeAnimator: ObjectAnimator? = null
    
    // Active chart tab — preserved across onResume calls
    // CORRECT: track tab so re-renders don't reset selection
    // WRONG:   always defaulting to 7d on every onResume
    private var activeChartTab = "7d"

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun showAutoModeDialog() {
        if (ScreenCaptureService.isRunning) {
            Toast.makeText(this, "Screen capture already active", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Enable Offer Capture?")
            .setMessage(
                "GigLens will capture offer screens automatically.\n\n" +
                "Tap the floating camera button over DoorDash to capture an offer.\n\n" +
                "Screen recording permission is required. " +
                "GigLens only reads DoorDash offer screens."
            )
            .setPositiveButton("Enable") { _, _ -> requestScreenCapturePermission() }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE)
            as android.media.projection.MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Floating button toggle
        // CORRECT: show dialog if not running, stop service if turning off
        // WRONG:   directly starting service without MediaProjection permission
        binding.switchFloatingButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ScreenCaptureService.isRunning) showAutoModeDialog()
            else if (!isChecked && ScreenCaptureService.isRunning)
                stopService(Intent(this, ScreenCaptureService::class.java))
        }

        // Auto capture — blocked on Android 16, show tooltip
        // CORRECT: disable + reset to off when tapped
        // WRONG:   leaving enabled — confuses driver when nothing happens
        binding.switchAutoCapture.isEnabled = false
        binding.switchAutoCapture.alpha = 0.5f
        binding.switchAutoCapture.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.switchAutoCapture.isChecked = false
                Toast.makeText(this,
                    "Auto capture requires Play Store approval on Android 16",
                    Toast.LENGTH_LONG).show()
            }
        }

        binding.rowOfferHistory.setOnClickListener {
            Toast.makeText(this, "Offer history coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.rowSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.tvSeeAll.setOnClickListener {
            Toast.makeText(this, "Offer history coming soon", Toast.LENGTH_SHORT).show()
        }

        // Chart tabs
        binding.btnTab7d.setOnClickListener { setChartTab("7d") }
        binding.btnTab30d.setOnClickListener { setChartTab("30d") }
        binding.btnTabAll.setOnClickListener { setChartTab("all") }

        // WebView for Chart.js
        // CORRECT: enable JavaScript — Chart.js requires it
        // WRONG:   leaving JavaScript disabled — blank chart
        binding.webViewChart.settings.javaScriptEnabled = true
        binding.webViewChart.settings.domStorageEnabled = true
        binding.webViewChart.setBackgroundColor(android.graphics.Color.WHITE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        loadStats()
    }

    override fun onDestroy() {
        stopLiveBadgeAnimation()
        super.onDestroy()
    }

    private fun updateUI() {
        val captureActive = ScreenCaptureService.isRunning

        // CORRECT: set background color on View — viewCaptureDot is a View not a TextView
        // WRONG:   calling setTextColor on viewCaptureDot — View has no setTextColor
        binding.viewCaptureDot.setBackgroundColor(
            if (captureActive) android.graphics.Color.parseColor("#00C9A7")
            else android.graphics.Color.parseColor("#4B5563")
        )
        binding.tvCaptureStatusTitle.text =
            if (captureActive) "Screen capture active" else "Screen capture off"
        binding.tvCaptureStatusSub.text =
            if (captureActive) "Tap 📷 button over DoorDash"
            else "Enable in Settings → Floating button"

        if (captureActive) {
            binding.tvLiveBadge.text = "LIVE"
            startLiveBadgeAnimation()
        } else {
            binding.tvLiveBadge.text = "OFF"
            stopLiveBadgeAnimation()
            binding.tvLiveBadge.alpha = 1f
        }

        // Sync toggle without triggering listener
        // CORRECT: null listener → set value → restore listener
        // WRONG:   setting isChecked with listener active — triggers recursive loop
        binding.switchFloatingButton.setOnCheckedChangeListener(null)
        binding.switchFloatingButton.isChecked = captureActive
        binding.switchFloatingButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ScreenCaptureService.isRunning) showAutoModeDialog()
            else if (!isChecked && ScreenCaptureService.isRunning)
                stopService(Intent(this, ScreenCaptureService::class.java))
        }
    }

    private fun startLiveBadgeAnimation() {
        liveBadgeAnimator?.cancel()
        liveBadgeAnimator = ObjectAnimator.ofFloat(
            binding.tvLiveBadge, "alpha", 1f, 0.25f
        ).apply {
            duration = 1200
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopLiveBadgeAnimation() {
        liveBadgeAnimator?.cancel()
        liveBadgeAnimator = null
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val dao = GigLensApp.instance.database.offerCaptureDao()

            val todayCount  = dao.getTodayCount()  ?: 0
            val todayAvgPay = dao.getTodayAvgPay()
            val todayAvgPpm = dao.getTodayAvgPayPerMile()

            val weeklyNet   = dao.getWeeklyNetEarnings()
            val avgNetOffer = dao.getWeeklyAvgNetPerOffer()
            val avgGasCost  = dao.getWeeklyAvgGasCost()
            val truePerMile = dao.getWeeklyAvgTruePayPerMile()
            val avgDist     = dao.getWeeklyAvgDistance()

            val takeCount   = dao.getVerdictCount("TAKE",       7) ?: 0
            val borderCount = dao.getVerdictCount("BORDERLINE", 7) ?: 0
            val skipCount   = dao.getVerdictCount("SKIP",       7) ?: 0
            val totalV      = takeCount + borderCount + skipCount

            val totalCount  = dao.getCount()
            val chartData   = dao.getDailyAvgNetValue(7)
            val bestNet     = dao.getBestNetValue(7)
            val avgNet      = dao.getAvgNetValue(7)
            val worstNet    = dao.getWorstNetValue(7)
            val recent      = dao.getRecentOffers(3)

            runOnUiThread {
                // Today
                binding.tvTodayCount.text    = "$todayCount"
                binding.tvTodayAvgPay.text   = todayAvgPay?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "--"
                binding.tvTodayAvgPerMile.text = todayAvgPpm?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "--"

                // Earnings
                binding.tvEarningsAmount.text = weeklyNet?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "$0.00"
                binding.tvAvgNetOffer.text = avgNetOffer?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "--"
                binding.tvAvgGasCost.text = avgGasCost?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "--"
                binding.tvTruePerMile.text = truePerMile?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "--"
                binding.tvAvgDistance.text = avgDist?.let {
                    String.format(Locale.US, "%.1f mi", it) } ?: "--"

                // Verdict breakdown
                binding.tvBreakdownPeriod.text = "Last 7 days · $totalV offers"
                binding.progressTake.progress =
                    if (totalV > 0) takeCount * 100 / totalV else 0
                binding.tvTakeCount.text = "$takeCount"
                binding.tvTakePct.text =
                    if (totalV > 0) "${takeCount * 100 / totalV}%" else "0%"
                binding.progressBorderline.progress =
                    if (totalV > 0) borderCount * 100 / totalV else 0
                binding.tvBorderlineCount.text = "$borderCount"
                binding.tvBorderlinePct.text =
                    if (totalV > 0) "${borderCount * 100 / totalV}%" else "0%"
                binding.progressSkip.progress =
                    if (totalV > 0) skipCount * 100 / totalV else 0
                binding.tvSkipCount.text = "$skipCount"
                binding.tvSkipPct.text =
                    if (totalV > 0) "${skipCount * 100 / totalV}%" else "0%"

                // Offer history badge
                binding.tvOfferHistoryBadge.text = "$totalCount offers"

                // Analytics
                binding.tvBestOffer.text = bestNet?.let {
                    String.format(Locale.US, "+$%.2f", it) } ?: "--"
                binding.tvAvgNet.text = avgNet?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "--"
                binding.tvWorstOffer.text = worstNet?.let {
                    String.format(Locale.US, "$%.2f", it) } ?: "--"

                // Recent offers
                recent.getOrNull(0)?.let { bindOfferRow(
                    binding.tvOffer1Verdict, binding.tvOffer1Name,
                    binding.tvOffer1Meta, binding.tvOffer1Net, it) }
                recent.getOrNull(1)?.let { bindOfferRow(
                    binding.tvOffer2Verdict, binding.tvOffer2Name,
                    binding.tvOffer2Meta, binding.tvOffer2Net, it) }
                recent.getOrNull(2)?.let { bindOfferRow(
                    binding.tvOffer3Verdict, binding.tvOffer3Name,
                    binding.tvOffer3Meta, binding.tvOffer3Net, it) }

                // Chart
                loadChartForTab(activeChartTab, chartData)

                // Footer
                binding.tvFooter.text =
                    "GigLens v${packageManager.getPackageInfo(packageName, 0).versionName} · Auguste Enterprise"
            }
        }
    }

    private fun bindOfferRow(
        tvVerdict: android.widget.TextView,
        tvName:    android.widget.TextView,
        tvMeta:    android.widget.TextView,
        tvNet:     android.widget.TextView,
        offer:     OfferCapture
    ) {
        val verdictColor = when (offer.verdict) {
            "TAKE"       -> android.graphics.Color.parseColor("#00C9A7")
            "BORDERLINE" -> android.graphics.Color.parseColor("#F59E0B")
            else         -> android.graphics.Color.parseColor("#EF4444")
        }
        tvVerdict.text = when (offer.verdict) {
            "BORDERLINE" -> "BORD"
            else         -> offer.verdict ?: "?"
        }
        tvVerdict.setTextColor(verdictColor)
        tvName.text = offer.restaurant ?: "Unknown"
        tvMeta.text = buildString {
            offer.payAmount?.let { append(String.format(Locale.US, "$%.2f", it)) }
            offer.distance?.let { append(" · ${String.format(Locale.US, "%.1f", it)} mi") }
            offer.score?.let { append(" · Score $it") }
        }
        val net = offer.netValue ?: 0.0
        tvNet.text = if (net >= 0)
            String.format(Locale.US, "+$%.2f", net)
        else
            String.format(Locale.US, "-$%.2f", Math.abs(net))
        tvNet.setTextColor(
            if (net >= 0) android.graphics.Color.parseColor("#00C9A7")
            else android.graphics.Color.parseColor("#EF4444")
        )
    }

    private fun setChartTab(tab: String) {
        activeChartTab = tab
        val navyColor = android.graphics.Color.parseColor("#1A1A2E")
        val tealColor = android.graphics.Color.parseColor("#00C9A7")
        val grayColor = android.graphics.Color.parseColor("#6B7280")
        listOf(binding.btnTab7d to "7d",
               binding.btnTab30d to "30d",
               binding.btnTabAll to "all").forEach { (btn, t) ->
            btn.setBackgroundColor(if (t == tab) navyColor else android.graphics.Color.TRANSPARENT)
            btn.setTextColor(if (t == tab) tealColor else grayColor)
        }
        lifecycleScope.launch {
            val dao  = GigLensApp.instance.database.offerCaptureDao()
            val days = when (tab) { "30d" -> 30; "all" -> 365; else -> 7 }
            val data = dao.getDailyAvgNetValue(days)
            runOnUiThread { loadChartForTab(tab, data) }
        }
    }

    private fun loadChartForTab(tab: String, data: List<DailyNetValue>) {
        // CORRECT: build Chart.js HTML inline, load with cdnjs base URL
        // WRONG:   loadData() without base URL — CDN request blocked by WebView
        val labels = if (data.isEmpty()) "\"No data\""
                     else data.joinToString(",") { "\"${it.date}\"" }
        val values = if (data.isEmpty()) "0"
                     else data.joinToString(",") { "%.2f".format(it.avgNet) }
        val html = """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.0/chart.umd.min.js"></script>
            <style>body{margin:0;padding:0;background:#fff;}canvas{width:100%!important;}</style>
            </head><body>
            <canvas id="c"></canvas>
            <script>
            new Chart(document.getElementById('c'),{
              type:'line',
              data:{labels:[$labels],datasets:[{data:[$values],
                borderColor:'#00C9A7',backgroundColor:'rgba(0,201,167,0.08)',
                borderWidth:2.5,pointBackgroundColor:'#00C9A7',
                pointRadius:4,fill:true,tension:0.4}]},
              options:{responsive:true,maintainAspectRatio:false,
                plugins:{legend:{display:false},
                  tooltip:{backgroundColor:'#1A1A2E',bodyColor:'#00C9A7',
                    bodyFont:{family:'monospace',weight:'600',size:13},
                    callbacks:{label:c=>'${'$'}'+c.parsed.y.toFixed(2)+' net'}}},
                scales:{
                  x:{grid:{display:false},ticks:{color:'#9CA3AF',font:{size:11}},border:{display:false}},
                  y:{grid:{color:'#F3F4F6'},ticks:{color:'#9CA3AF',font:{size:11},
                    callback:v=>'${'$'}'+v},border:{display:false}}}}});
            </script></body></html>
        """.trimIndent()
        binding.webViewChart.loadDataWithBaseURL(
            "https://cdnjs.cloudflare.com", html, "text/html", "UTF-8", null
        )
    }
}
