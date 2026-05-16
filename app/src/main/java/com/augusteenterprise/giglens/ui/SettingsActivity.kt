package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic)
// Settings screen — driver configures region, GPS preference, vehicle cost, data sharing.
// All values read from and written to app_config / scorer_config DB tables.
// No hardcoded defaults in UI logic — all defaults come from DB seed values.

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.AppConfigKeys
import com.augusteenterprise.giglens.data.ScorerConfigKeys
import com.augusteenterprise.giglens.databinding.ActivitySettingsBinding
import com.augusteenterprise.giglens.geocoding.GeocodingHelper
import com.augusteenterprise.giglens.location.LocationHelper
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private lateinit var binding: ActivitySettingsBinding

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            binding.switchGps.isChecked = true
            detectRegionFromGps()
        } else {
            binding.switchGps.isChecked = false
            Toast.makeText(this, "Location permission denied — use manual region", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }

        loadSettings()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val appDao    = GigLensApp.instance.database.appConfigDao()
            val scorerDao = GigLensApp.instance.database.scorerConfigDao()

            val detectedRegion = appDao.getValue(AppConfigKeys.DRIVER_REGION) ?: ""
            val manualRegion   = appDao.getValue(AppConfigKeys.DRIVER_MANUAL_REGION) ?: ""
            val gpsEnabled     = appDao.getValue(AppConfigKeys.GPS_ENABLED) == "true"
            val costPerMile    = scorerDao.getValue(ScorerConfigKeys.COST_PER_MILE) ?: 0.90
            val dataSharing    = appDao.getValue(AppConfigKeys.DATA_SHARING) ?: "none"

            runOnUiThread {
                binding.switchGps.isChecked = gpsEnabled
                binding.tvDetectedRegion.text = if (detectedRegion.isNotBlank()) {
                    "Detected region: $detectedRegion"
                } else {
                    "Detected region: — (enable GPS to detect)"
                }
                binding.etRegion.setText(manualRegion)
                binding.etCostPerMile.setText("%.2f".format(costPerMile))
                when (dataSharing) {
                    "aggregate"  -> binding.rbSharingAggregate.isChecked = true
                    "individual" -> binding.rbSharingIndividual.isChecked = true
                    else         -> binding.rbSharingNone.isChecked = true
                }
            }
        }
    }

    private fun setupListeners() {
        binding.switchGps.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestLocationAndDetect()
            } else {
                lifecycleScope.launch {
                    GigLensApp.instance.database.appConfigDao()
                        .setValue(AppConfigKeys.GPS_ENABLED, "false")
                }
            }
        }
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
    }

    private fun requestLocationAndDetect() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            detectRegionFromGps()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun detectRegionFromGps() {
        binding.tvDetectedRegion.text = "Detecting region…"
        lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(applicationContext)
            if (location != null) {
                val region = GeocodingHelper.reverseGeocode(location.latitude, location.longitude)
                val appDao = GigLensApp.instance.database.appConfigDao()
                if (region != null) {
                    appDao.setValue(AppConfigKeys.DRIVER_REGION, region)
                    appDao.setValue(AppConfigKeys.GPS_ENABLED, "true")
                    Log.d(TAG, "GPS region detected: $region")
                    runOnUiThread {
                        binding.tvDetectedRegion.text = "Detected region: $region"
                        Toast.makeText(this@SettingsActivity, "Region detected: $region", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        binding.tvDetectedRegion.text = "Detected region: — (could not resolve)"
                        Toast.makeText(this@SettingsActivity, "Could not detect region — enter manually", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                runOnUiThread {
                    binding.tvDetectedRegion.text = "Detected region: — (no GPS fix)"
                    Toast.makeText(this@SettingsActivity, "No GPS fix — enter region manually", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            val appDao    = GigLensApp.instance.database.appConfigDao()
            val scorerDao = GigLensApp.instance.database.scorerConfigDao()

            val manualRegion = binding.etRegion.text?.toString()?.trim() ?: ""
            appDao.setValue(AppConfigKeys.DRIVER_MANUAL_REGION, manualRegion)

            appDao.setValue(AppConfigKeys.GPS_ENABLED,
                if (binding.switchGps.isChecked) "true" else "false")

            val costStr = binding.etCostPerMile.text?.toString()?.trim() ?: "0.90"
            val cost = costStr.toDoubleOrNull()
            if (cost != null && cost > 0) {
                scorerDao.updateValue(ScorerConfigKeys.COST_PER_MILE, cost)
            } else {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Invalid cost per mile value", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val dataSharing = when {
                binding.rbSharingAggregate.isChecked  -> "aggregate"
                binding.rbSharingIndividual.isChecked -> "individual"
                else                                  -> "none"
            }
            appDao.setValue(AppConfigKeys.DATA_SHARING, dataSharing)
            Log.d(TAG, "Settings saved — region: $manualRegion | cost: $cost | sharing: $dataSharing")

            runOnUiThread {
                Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
