package com.augusteenterprise.giglens
// Author: Claude - Wired up GeocodingHelper.loadStateNameMap on app init

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.augusteenterprise.giglens.data.OfferDatabase
import com.augusteenterprise.giglens.geocoding.GeocodingHelper

class GigLensApp : Application() {

    lateinit var database: OfferDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        // Apply dark mode before any Activity is created so the first frame is correct
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean(PREF_DARK_MODE, false))
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
        )
        instance = this
        database = OfferDatabase.getInstance(this)
        GeocodingHelper.loadStateNameMap(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "giglens_capture"
        const val PREFS_NAME = "giglens_prefs"
        const val PREF_DARK_MODE = "dark_mode"
        lateinit var instance: GigLensApp
            private set
    }
}
