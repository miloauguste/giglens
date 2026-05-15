package com.augusteenterprise.giglens

// Author: Claude (Anthropic)

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.augusteenterprise.giglens.data.OfferDatabase

class GigLensApp : Application() {

    lateinit var database: OfferDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = OfferDatabase.getInstance(this)
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
        lateinit var instance: GigLensApp
            private set
    }
}
