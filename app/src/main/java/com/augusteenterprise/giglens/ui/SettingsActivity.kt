package com.augusteenterprise.giglens.ui
// Author: Claude - Added hourly rate field to Settings UI (Issue #3)

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.augusteenterprise.giglens.GigLensApp
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
            val dataSharing = appDao.getValue(AppConfigKeys.DATA_SHARING) ?: "none"
            val widgetEnabled = appDao.getValue(AppConfigKeys.WIDGET_ENABLED) == "true"
            
            runOnUiThread {
                updateWidgetPermUI()
                binding.switchWidget.isChecked = widgetEnabled
                binding.switchGps.isChecked = gpsEnabled
                binding.tvDetectedRegion.text = if (detectedRegion.isNotBlank()) {
                    "Detected region: $detectedRegion"
                } else {
                    "Detected region: — (enable GPS to detect)"
                }
                binding.etRegion.setText(manualRegion)
                binding.etCostPerMile.setText("%.2f".format(costPerMile))
                binding.etHourlyRate.setText("%.2f".format(hourlyRate))
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
            appDao.setValue(AppConfigKeys.DATA_SHARING, dataSharing)
            scorerDao.updateValue(ScorerConfigKeys.COST_PER_MILE, costPerMile)
            scorerDao.updateValue(ScorerConfigKeys.HOURLY_RATE, hourlyRate)
            
            runOnUiThread {
                Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
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
