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

        binding.btnToggleCapture.setOnClickListener { showAutoModeDialog() }
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
        val autoMode = OfferDetectorService.isRunning

        binding.tvStatus.text = if (autoMode) {
            "Auto mode active — offers captured automatically"
        } else {
            "Screenshot an offer → Share → GigLens"
        }

        binding.btnToggleCapture.text = if (autoMode) {
            "Auto Mode Active"
        } else {
            "Enable Auto Mode"
        }
    }

    /**
     * Shows dialog explaining auto mode before requesting permission.
     */
    private fun showAutoModeDialog() {
        if (OfferDetectorService.isRunning) {
            Toast.makeText(this, "Auto mode is already active", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Enable Auto Mode?")
            .setMessage(
                "Auto mode captures offers automatically without you needing to screenshot.\n\n" +
                "How it works:\n" +
                "• GigLens watches for offer screens in DoorDash\n" +
                "• When an offer appears, it captures and logs it automatically\n\n" +
                "This requires Accessibility Service permission. " +
                "GigLens only monitors DoorDash — no other apps are accessed."
            )
            .setPositiveButton("Enable") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Not now", null)
            .show()
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
