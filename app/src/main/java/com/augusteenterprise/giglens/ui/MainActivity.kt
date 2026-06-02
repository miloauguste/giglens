package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic)
// Main activity: shows capture stats, explains how to use share feature,
// and provides opt-in for auto mode (Accessibility Service).

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.R
import com.augusteenterprise.giglens.databinding.ActivityMainBinding
import com.augusteenterprise.giglens.service.OfferDetectorService
import com.augusteenterprise.giglens.ui.SettingsActivity
import com.augusteenterprise.giglens.service.ScreenCaptureService
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // MediaProjection permission (auto mode)
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification permission (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // No button listener needed — capture enabled via Settings
        binding.btnViewHistory.setOnClickListener { openHistory() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        loadStats()
    }

    private fun updateUI() {
        // Author: Claude (Anthropic) - May 26 2026: Fix status logic
        // CORRECT: check both OfferDetectorService AND ScreenCaptureService
        // WRONG:   checking only OfferDetectorService — misses missing MediaProjection permission
        val accessibilityActive = OfferDetectorService.isRunning
        val captureActive = ScreenCaptureService.isRunning

        binding.tvStatus.text = when {
            captureActive -> "Tap the 📷 button over DoorDash to capture an offer"
            else -> "Screenshot an offer → Share → GigLens"
        }
        // Update status card
        if (captureActive) {
            binding.tvCaptureStatusDot.setTextColor(android.graphics.Color.parseColor("#00C9A7"))
            binding.tvCaptureStatusTitle.text = "Screen capture active"
            binding.tvCaptureStatusSub.text = "Tap the 📷 button over DoorDash to score an offer"
        } else {
            binding.tvCaptureStatusDot.setTextColor(android.graphics.Color.parseColor("#999999"))
            binding.tvCaptureStatusTitle.text = "Screen capture off"
            binding.tvCaptureStatusSub.text = "Enable in Settings → Floating button"
        }
    }


    /**
     * Shows dialog explaining auto mode before requesting permission.
     */
    private fun showAutoModeDialog() {
        // Author: Claude (Anthropic) - May 26 2026: Wire MediaProjection request
        // CORRECT: request screen capture permission so ScreenCaptureService can take screenshots
        // WRONG:   only opening Accessibility Settings — never requesting MediaProjection
        android.util.Log.e("GigLens_DEBUG", "showAutoModeDialog called isRunning=${ScreenCaptureService.isRunning}")
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
            .setPositiveButton("Enable") { _, _ ->
                requestScreenCapturePermission()
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun requestScreenCapturePermission() {
        android.util.Log.e("GigLens_DEBUG", "requestScreenCapturePermission called")
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

    private fun loadStats() {
        lifecycleScope.launch {
            val dao = GigLensApp.instance.database.offerCaptureDao()
            val count = dao.getCount()
            val avgPay = dao.getAveragePay()
            val avgPerMile = dao.getAveragePayPerMile()

            runOnUiThread {
                binding.tvCaptureCount.text = "$count offers captured"
                binding.tvAvgPay.text = if (avgPay != null) {
                    String.format(Locale.US, "$%.2f", avgPay)
                } else "--"
                binding.tvAvgPerMile.text = if (avgPerMile != null) {
                    String.format(Locale.US, "$%.2f", avgPerMile)
                } else "--"
            }
        }
    }

    private fun openHistory() {
        Toast.makeText(this, "History view coming in Phase 2", Toast.LENGTH_SHORT).show()
    }
}
