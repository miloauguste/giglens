package com.augusteenterprise.giglens.ui
// Author: Claude - Feature #8: Added auto capture mode + supported platforms UI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import android.content.ComponentName
import android.provider.Settings
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.PlatformRegistry
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
    
    // MediaProjection permission launcher
    // CORRECT: request from Settings when driver enables floating button — one step
    // WRONG:   requiring driver to go to MainActivity separately to grant permission
    // CORRECT: request both FINE and COARSE in one prompt — matches manifest permissions
    // WRONG:   requesting only FINE — some OEMs grant COARSE-only on first ask, leaves a gap
    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            Log.d(TAG, "Location permission granted (fine=$fineGranted coarse=$coarseGranted)")
            binding.switchGps.isChecked = true
            GlobalScope.launch {
                GigLensApp.instance.database.appConfigDao()
                    .setValue(AppConfigKeys.GPS_ENABLED, "true")
            }
        } else {
            Log.d(TAG, "Location permission denied — keeping GPS toggle OFF")
            binding.switchGps.isChecked = false
            Toast.makeText(this,
                "Location permission is required for delivery town estimation",
                Toast.LENGTH_LONG).show()
            GlobalScope.launch {
                GigLensApp.instance.database.appConfigDao()
                    .setValue(AppConfigKeys.GPS_ENABLED, "false")
            }
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val serviceIntent = android.content.Intent(this, com.augusteenterprise.giglens.service.ScreenCaptureService::class.java).apply {
                putExtra(com.augusteenterprise.giglens.service.ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(com.augusteenterprise.giglens.service.ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            android.widget.Toast.makeText(this, "Screen capture enabled", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(this, "Screen capture permission denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

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

        loadDarkModeToggle()
        setupListeners()
        loadSettings()
    }
    
    private fun loadDarkModeToggle() {
        val prefs = getSharedPreferences(GigLensApp.PREFS_NAME, Context.MODE_PRIVATE)
        binding.switchDarkMode.isChecked = prefs.getBoolean(GigLensApp.PREF_DARK_MODE, false)
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(GigLensApp.PREF_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
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
        // CORRECT: toggle ON requests real Android location permission if not already granted
        // WRONG:   toggle just sets a DB flag — GPS_ENABLED=true with no actual permission means
        //          LocationHelper.getCurrentLocation() silently returns null, town estimation
        //          fails with no warning to the driver (confirmed bug, 2026-06-17 shift)
        binding.switchGps.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "switchGps toggled: $isChecked")
            if (isChecked) {
                val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (fineGranted || coarseGranted) {
                    Log.d(TAG, "Location permission already granted")
                    GlobalScope.launch {
                        GigLensApp.instance.database.appConfigDao()
                            .setValue(AppConfigKeys.GPS_ENABLED, "true")
                    }
                } else {
                    Log.d(TAG, "Location permission not granted — requesting now")
                    // CORRECT: snap toggle back to OFF while request is pending — avoids showing
                    //          a checked state that doesn't yet reflect real permission status
                    // WRONG:   leaving toggle ON during request — driver sees ON even if they deny
                    binding.switchGps.isChecked = false
                    locationPermissionLauncher.launch(arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            } else {
                GlobalScope.launch {
                    GigLensApp.instance.database.appConfigDao()
                        .setValue(AppConfigKeys.GPS_ENABLED, "false")
                }
            }
        }
        
        binding.rgCaptureMode.setOnCheckedChangeListener { _, _ ->
            updateAccessibilityPermUI()
        }

        binding.btnGrantAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnLookupMpg.setOnClickListener {
            // CORRECT: open EPA fueleconomy.gov so driver can look up their exact MPG
            // WRONG:   hardcoding MPG — every vehicle is different
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.fueleconomy.gov/feg/Find.do"))
            startActivity(intent)
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
                val screenshotDelayMs = appDao.getValue(AppConfigKeys.SCREENSHOT_DELAY_MS)?.toLongOrNull() ?: 1500L
            val mpg = scorerDao.getValue(ScorerConfigKeys.MPG) ?: 30.0
            val gasPrice = scorerDao.getValue(ScorerConfigKeys.GAS_PRICE) ?: 3.20
            val thresholdGreenPct  = scorerDao.getValue(ScorerConfigKeys.THRESHOLD_GREEN_PCT)  ?: 75.0
            val thresholdYellowPct = scorerDao.getValue(ScorerConfigKeys.THRESHOLD_YELLOW_PCT) ?: 50.0
            val floorPayPerMile    = scorerDao.getValue(ScorerConfigKeys.FLOOR_PAY_PER_MILE)   ?: 1.50
            val floorTotalPay      = scorerDao.getValue(ScorerConfigKeys.FLOOR_TOTAL_PAY)      ?: 6.00
            val dataSharing = appDao.getValue(AppConfigKeys.DATA_SHARING) ?: "none"
            val widgetEnabled = appDao.getValue(AppConfigKeys.WIDGET_ENABLED) == "true"
            val captureMode = appDao.getValue(AppConfigKeys.AUTO_CAPTURE_MODE) ?: "off"
            android.util.Log.d("SettingsActivity", "loadSettings: widget=$widgetEnabled mode=$captureMode")
            val enabledPlatforms = appDao.getValue(AppConfigKeys.ENABLED_PLATFORMS) ?: "doordash"
            val pinDetectionEnabled = appDao.getValue(AppConfigKeys.PIN_DETECTION_ENABLED) == "true"

            runOnUiThread {
                updateWidgetPermUI()
                binding.switchWidget.isChecked = widgetEnabled
                when (captureMode) {
                    "accessibility"  -> binding.rbCaptureAccessibility.isChecked = true
                    "tap", "button"  -> binding.rbCaptureButton.isChecked = true
                    "both"           -> binding.rbCaptureBoth.isChecked = true
                    else             -> binding.rbCaptureOff.isChecked = true
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
                binding.etScreenshotDelayMs.setText(screenshotDelayMs.toString())
                binding.switchPinDetectionEnabled.isChecked = pinDetectionEnabled
                binding.etMpg.setText("%.1f".format(mpg))
                binding.etGasPrice.setText("%.2f".format(gasPrice))
                binding.etThresholdGreenPct.setText("%.0f".format(thresholdGreenPct))
                binding.etThresholdYellowPct.setText("%.0f".format(thresholdYellowPct))
                binding.etFloorPayPerMile.setText("%.2f".format(floorPayPerMile))
                binding.etFloorTotalPay.setText("%.2f".format(floorTotalPay))
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
        val screenshotDelayMs = binding.etScreenshotDelayMs.text.toString().toLongOrNull()?.coerceIn(0L, 5000L) ?: 1500L
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
            appDao.setValue(AppConfigKeys.SCREENSHOT_DELAY_MS, screenshotDelayMs.toString())
            appDao.setValue(AppConfigKeys.PIN_DETECTION_ENABLED, binding.switchPinDetectionEnabled.isChecked.toString())
            val captureMode = when {
                binding.rbCaptureAccessibility.isChecked -> "accessibility"
                binding.rbCaptureButton.isChecked        -> "tap"
                binding.rbCaptureBoth.isChecked          -> "both"
                else                                     -> "off"
            }
            appDao.setValue(AppConfigKeys.AUTO_CAPTURE_MODE, captureMode)
            val enabledPlatforms = mutableListOf<String>()
            if (binding.cbPlatformDoordash.isChecked) enabledPlatforms.add("doordash")
            appDao.setValue(AppConfigKeys.ENABLED_PLATFORMS,
                if (enabledPlatforms.isEmpty()) "doordash" else enabledPlatforms.joinToString(","))
            appDao.setValue(AppConfigKeys.DATA_SHARING, dataSharing)
            val mpg = binding.etMpg.text.toString().toDoubleOrNull()?.coerceIn(1.0, 150.0) ?: 30.0
            val gasPrice = binding.etGasPrice.text.toString().toDoubleOrNull()?.coerceIn(0.50, 10.0) ?: 3.20
            scorerDao.updateValue(ScorerConfigKeys.COST_PER_MILE, costPerMile)
            scorerDao.updateValue(ScorerConfigKeys.HOURLY_RATE, hourlyRate)
            scorerDao.updateValue(ScorerConfigKeys.RESULT_DISPLAY_SECONDS, resultDuration)
            scorerDao.updateValue(ScorerConfigKeys.MPG, mpg)
            scorerDao.updateValue(ScorerConfigKeys.GAS_PRICE, gasPrice)
            val thresholdGreenPct  = binding.etThresholdGreenPct.text.toString().toDoubleOrNull()?.coerceIn(1.0, 100.0)  ?: 75.0
            val thresholdYellowPct = binding.etThresholdYellowPct.text.toString().toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: 50.0
            val floorPayPerMile    = binding.etFloorPayPerMile.text.toString().toDoubleOrNull()?.coerceIn(0.0, 10.0)     ?: 1.50
            val floorTotalPay      = binding.etFloorTotalPay.text.toString().toDoubleOrNull()?.coerceIn(0.0, 50.0)       ?: 6.00
            scorerDao.updateValue(ScorerConfigKeys.THRESHOLD_GREEN_PCT,  thresholdGreenPct)
            scorerDao.updateValue(ScorerConfigKeys.THRESHOLD_YELLOW_PCT, thresholdYellowPct)
            scorerDao.updateValue(ScorerConfigKeys.FLOOR_PAY_PER_MILE,   floorPayPerMile)
            scorerDao.updateValue(ScorerConfigKeys.FLOOR_TOTAL_PAY,      floorTotalPay)
            
            val savedMode = captureMode
            runOnUiThread {
                when (savedMode) {
                    "button", "both" -> {
                        // CORRECT: check MediaProjection first — start both services together
                        // WRONG:   starting CaptureButtonService without ScreenCaptureService
                        if (!com.augusteenterprise.giglens.service.ScreenCaptureService.isRunning) {
                            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE)
                                as android.media.projection.MediaProjectionManager
                            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                        } else {
                            Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "accessibility" -> {
                        // CORRECT: no MediaProjection needed — skip screen share dialog entirely
                        // TODO: remove this entire when block when ScreenCaptureService is removed
                        if (com.augusteenterprise.giglens.service.ScreenCaptureService.isRunning) {
                            stopService(Intent(this@SettingsActivity, com.augusteenterprise.giglens.service.ScreenCaptureService::class.java))
                        }
                        Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        if (com.augusteenterprise.giglens.service.ScreenCaptureService.isRunning) {
                            stopService(Intent(this@SettingsActivity, com.augusteenterprise.giglens.service.ScreenCaptureService::class.java))
                        }
                        Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun openAccessibilitySettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                startActivity(android.content.Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS"))
                return
            } catch (_: Exception) { }
        }
        startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
            binding.tvAccessibilityActive.visibility = android.view.View.GONE
        } else if (needsAccessibility && isAccessibilityEnabled()) {
            binding.tvAccessibilityPermStatus.visibility = android.view.View.GONE
            binding.btnGrantAccessibility.visibility = android.view.View.GONE
            binding.tvAccessibilityActive.visibility = android.view.View.VISIBLE
        } else {
            binding.tvAccessibilityPermStatus.visibility = android.view.View.GONE
            binding.btnGrantAccessibility.visibility = android.view.View.GONE
            binding.tvAccessibilityActive.visibility = android.view.View.GONE
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
