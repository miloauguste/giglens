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
    const val DRIVER_REGION   = "driver_region"    // e.g. "New Jersey, USA" — set from GPS or user prefs
    const val DRIVER_STATE    = "driver_state"     // e.g. "NJ"
    const val MAPS_API_KEY    = "maps_api_key"     // Pro tier: Google Maps Distance Matrix
    const val DATA_SHARING    = "data_sharing"     // "none" | "aggregate" | "individual"
}

fun defaultAppConfig(): List<AppConfig> = listOf(
    AppConfig(AppConfigKeys.DRIVER_REGION, "",    "Driver's region for geocoding (set automatically from GPS)"),
    AppConfig(AppConfigKeys.DRIVER_STATE,  "",    "Driver's state abbreviation (set automatically from GPS)"),
    AppConfig(AppConfigKeys.MAPS_API_KEY,  "",    "Google Maps Distance Matrix API key (Pro tier)"),
    AppConfig(AppConfigKeys.DATA_SHARING,  "none","Community data sharing: none | aggregate | individual")
)
