package com.augusteenterprise.giglens.ui
// Author: Claude - Feature #8: Added auto capture mode + supported platforms UI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.content.ComponentName
import android.provider.Settings
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.PlatformRegistry
import com.augusteenterprise.giglens.service.CaptureButtonService
import com.augusteenterprise.giglens.databinding.ActivitySettingsBinding
import com.augusteenterprise.giglens.data.AppConfigKeys
import com.augusteenterprise.giglens.data.ScorerConfigKeys
import com.augusteenterprise.giglens.service.OfferOverlayService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
    }
    
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onResume() {
        super.onResume()
        // Author: Claude (Anthropic) - May 26 2026: Re-check accessibility state on resume
        // CORRECT: re-evaluate when returning from system Accessibility settings screen
        // WRONG:   only checking on onCreate — misses state changes made in system settings
        updateAccessibilityPermUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setupListeners()
        loadSettings()
    }
    
    private fun setupListeners() {
        binding.switchWidget.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "switchWidget toggled: $isChecked")
            if (isChecked) {
                if (!OverlayPermissionHelper.hasPermission(this)) {
                    binding.switchWidget.isChecked = false
                    OverlayPermissionHelper.requestPermission(this)
                    return@setOnCheckedChangeListener
                }
                startWidgetService()
                GlobalScope.launch {
                    GigLensApp.instance.database.appConfigDao()
                        .setValue(AppConfigKeys.WIDGET_ENABLED, "true")
                    Log.d(TAG, "widget_enabled saved: true")
                }
            } else {
                stopService(Intent(this, OfferOverlayService::class.java))
                GlobalScope.launch {
                    GigLensApp.instance.database.appConfigDao()
                        .setValue(AppConfigKeys.WIDGET_ENABLED, "false")
                    Log.d(TAG, "widget_enabled saved: false")
                }
            }
        }
        
        binding.btnGrantOverlay.setOnClickListener {
            OverlayPermissionHelper.requestPermission(this)
        }
        
        binding.rgCaptureMode.setOnCheckedChangeListener { _, _ ->
            updateAccessibilityPermUI()
        }

        binding.btnGrantAccessibility.setOnClickListener {
            startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun loadSettings() {
        lifecycleScope.launch {
            val appDao = GigLensApp.instance.database.appConfigDao()
            val scorerDao = GigLensApp.instance.database.scorerConfigDao()
            
            val detectedRegion = appDao.getValue(AppConfigKeys.DRIVER_REGION) ?: ""
            val manualRegion = appDao.getValue(AppConfigKeys.DRIVER_MANUAL_REGION) ?: ""
            val gpsEnabled = appDao.getValue(AppConfigKeys.GPS_ENABLED) == "true"
            val costPerMile = scorerDao.getValue(ScorerConfigKeys.COST_PER_MILE) ?: 0.90
            val hourlyRate = scorerDao.getValue(ScorerConfigKeys.HOURLY_RATE) ?: 15.00
            val resultDuration = scorerDao.getValue(ScorerConfigKeys.RESULT_DISPLAY_SECONDS) ?: 60.0
            val dataSharing = appDao.getValue(AppConfigKeys.DATA_SHARING) ?: "none"
            val widgetEnabled = appDao.getValue(AppConfigKeys.WIDGET_ENABLED) == "true"
            val captureMode = appDao.getValue(AppConfigKeys.AUTO_CAPTURE_MODE) ?: "off"
            val enabledPlatforms = appDao.getValue(AppConfigKeys.ENABLED_PLATFORMS) ?: "doordash"
            
            runOnUiThread {
                updateWidgetPermUI()
                binding.switchWidget.isChecked = widgetEnabled
                when (captureMode) {
                    "accessibility" -> binding.rbCaptureAccessibility.isChecked = true
                    "button"        -> binding.rbCaptureButton.isChecked = true
                    "both"          -> binding.rbCaptureBoth.isChecked = true
                    else            -> binding.rbCaptureOff.isChecked = true
                }
                val platforms = enabledPlatforms.split(",").map { it.trim() }
                binding.cbPlatformDoordash.isChecked = "doordash" in platforms
                updateAccessibilityPermUI()
                binding.switchGps.isChecked = gpsEnabled
                binding.tvDetectedRegion.text = if (detectedRegion.isNotBlank()) {
                    "Detected region: $detectedRegion"
                } else {
                    "Detected region: — (enable GPS to detect)"
                }
                binding.etRegion.setText(manualRegion)
                binding.etCostPerMile.setText("%.2f".format(costPerMile))
                binding.etHourlyRate.setText("%.2f".format(hourlyRate))
                binding.etResultDuration.setText("%.0f".format(resultDuration))
                when (dataSharing) {
                    "aggregate" -> binding.rbSharingAggregate.isChecked = true
                    "individual" -> binding.rbSharingIndividual.isChecked = true
                    else -> binding.rbSharingNone.isChecked = true
                }
            }
        }
    }
    
    private fun saveSettings() {
        val manualRegion = binding.etRegion.text.toString().trim()
        val gpsEnabled = binding.switchGps.isChecked
        val costPerMile = binding.etCostPerMile.text.toString().toDoubleOrNull() ?: 0.90
        val hourlyRate = binding.etHourlyRate.text.toString().toDoubleOrNull() ?: 15.00
        val resultDuration = binding.etResultDuration.text.toString().toDoubleOrNull()?.coerceIn(5.0, 300.0) ?: 60.0
        val dataSharing = when {
            binding.rbSharingAggregate.isChecked -> "aggregate"
            binding.rbSharingIndividual.isChecked -> "individual"
            else -> "none"
        }
        
        lifecycleScope.launch {
            val appDao = GigLensApp.instance.database.appConfigDao()
            val scorerDao = GigLensApp.instance.database.scorerConfigDao()
            
            appDao.setValue(AppConfigKeys.DRIVER_MANUAL_REGION, manualRegion)
            appDao.setValue(AppConfigKeys.GPS_ENABLED, gpsEnabled.toString())
            val captureMode = when {
                binding.rbCaptureAccessibility.isChecked -> "accessibility"
                binding.rbCaptureButton.isChecked        -> "button"
                binding.rbCaptureBoth.isChecked          -> "both"
                else                                     -> "off"
            }
            appDao.setValue(AppConfigKeys.AUTO_CAPTURE_MODE, captureMode)
            val enabledPlatforms = mutableListOf<String>()
            if (binding.cbPlatformDoordash.isChecked) enabledPlatforms.add("doordash")
            appDao.setValue(AppConfigKeys.ENABLED_PLATFORMS,
                if (enabledPlatforms.isEmpty()) "doordash" else enabledPlatforms.joinToString(","))
            appDao.setValue(AppConfigKeys.DATA_SHARING, dataSharing)
            scorerDao.updateValue(ScorerConfigKeys.COST_PER_MILE, costPerMile)
            scorerDao.updateValue(ScorerConfigKeys.HOURLY_RATE, hourlyRate)
            scorerDao.updateValue(ScorerConfigKeys.RESULT_DISPLAY_SECONDS, resultDuration)
            
            // Start/stop CaptureButtonService based on mode
            val savedMode = captureMode
            runOnUiThread {
                when (savedMode) {
                    "button", "both" -> {
                        if (!CaptureButtonService.isRunning) {
                            startService(Intent(this@SettingsActivity, CaptureButtonService::class.java))
                        }
                    }
                    else -> {
                        if (CaptureButtonService.isRunning) {
                            stopService(Intent(this@SettingsActivity, CaptureButtonService::class.java))
                        }
                    }
                }
                Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun isAccessibilityEnabled(): Boolean {
        // Author: Claude (Anthropic) - May 26 2026: Fix relative vs fully-qualified service name mismatch
        // CORRECT: match both relative and fully-qualified forms
        // WRONG:   only checking "$packageName/.service.OfferDetectorService" — misses full path form
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val shortForm = "$packageName/.service.OfferDetectorService"
        val fullForm  = "$packageName/com.augusteenterprise.giglens.service.OfferDetectorService"
        return enabled.contains(shortForm) || enabled.contains(fullForm)
    }

    private fun updateAccessibilityPermUI() {
        val mode = when {
            binding.rbCaptureAccessibility.isChecked -> "accessibility"
            binding.rbCaptureButton.isChecked -> "button"
            binding.rbCaptureBoth.isChecked -> "both"
            else -> "off"
        }
        val needsAccessibility = mode == "accessibility" || mode == "both"
        if (needsAccessibility && !isAccessibilityEnabled()) {
            binding.tvAccessibilityPermStatus.visibility = android.view.View.VISIBLE
            binding.btnGrantAccessibility.visibility = android.view.View.VISIBLE
        } else {
            binding.tvAccessibilityPermStatus.visibility = android.view.View.GONE
            binding.btnGrantAccessibility.visibility = android.view.View.GONE
        }
    }

    private fun startWidgetService() {
        val intent = Intent(this, OfferOverlayService::class.java)
        startService(intent)
        Log.d(TAG, "Widget service started")
    }
    
    private fun updateWidgetPermUI() {
        val hasPermission = OverlayPermissionHelper.hasPermission(this)
        if (hasPermission) {
            binding.tvWidgetPermStatus.text = "Permission granted"
            binding.tvWidgetPermStatus.setTextColor(
                android.graphics.Color.parseColor("#4CAF50"))
            binding.btnGrantOverlay.visibility = View.GONE
        } else {
            binding.tvWidgetPermStatus.text = "Permission required: Draw over other apps"
            binding.tvWidgetPermStatus.setTextColor(
                android.graphics.Color.parseColor("#FF9800"))
            binding.btnGrantOverlay.visibility = View.VISIBLE
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OverlayPermissionHelper.REQUEST_CODE) {
            updateWidgetPermUI()
            if (OverlayPermissionHelper.hasPermission(this)) {
                binding.switchWidget.isChecked = true
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
