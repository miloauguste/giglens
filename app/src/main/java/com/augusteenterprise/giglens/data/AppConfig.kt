package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic)
// Key-value store for string app configuration.
// Numeric settings use scorer_config; string settings use app_config.

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey val key: String,
    val value: String,
    val description: String = ""
)

object AppConfigKeys {
    const val DRIVER_REGION        = "driver_region"         // auto-detected from GPS reverse geocode
    const val DRIVER_MANUAL_REGION = "driver_manual_region"  // user-entered fallback
    const val DRIVER_STATE         = "driver_state"          // e.g. "NJ"
    const val GPS_ENABLED          = "gps_enabled"           // "true" | "false"
    const val MAPS_API_KEY         = "maps_api_key"          // Pro tier: Google Maps Distance Matrix
    const val DATA_SHARING         = "data_sharing"          // "none" | "aggregate" | "individual"
    const val WIDGET_ENABLED       = "widget_enabled"         // "true" | "false"
}

fun defaultAppConfig(): List<AppConfig> = listOf(
    AppConfig(AppConfigKeys.DRIVER_REGION,        "",     "Auto-detected region from GPS reverse geocode"),
    AppConfig(AppConfigKeys.DRIVER_MANUAL_REGION, "",     "User-entered region fallback"),
    AppConfig(AppConfigKeys.DRIVER_STATE,         "",     "Driver state abbreviation (auto)"),
    AppConfig(AppConfigKeys.GPS_ENABLED,          "false","GPS location enabled: true | false"),
    AppConfig(AppConfigKeys.MAPS_API_KEY,         "",     "Google Maps Distance Matrix API key (Pro tier)"),
    AppConfig(AppConfigKeys.DATA_SHARING,         "none", "Community data sharing: none | aggregate | individual"),
    AppConfig(AppConfigKeys.WIDGET_ENABLED,       "false","Widget overlay enabled/disabled")
)
