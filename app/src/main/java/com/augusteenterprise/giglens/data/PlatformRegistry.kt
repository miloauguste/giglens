package com.augusteenterprise.giglens.data
// Author: Claude - Feature #8: Platform registry for supported gig apps
// DoorDash is active. Others grayed out — coming in future versions.
// To add a platform: add entry here + update accessibility_service_config.xml packageNames

data class GigPlatform(
    val id: String,              // internal key e.g. "doordash"
    val displayName: String,     // shown in Settings UI
    val packageName: String,     // Android package to monitor
    val offerKeywords: List<String>, // accessibility node keywords that signal an offer screen
    val supported: Boolean       // true = active, false = grayed out (coming soon)
)

object PlatformRegistry {

    val ALL = listOf(
        GigPlatform(
            id           = "doordash",
            displayName  = "DoorDash",
            packageName  = "com.doordash.driverapp",
            offerKeywords = listOf("accept", "decline", "$", "mi"),
            supported    = true
        ),
        GigPlatform(
            id           = "ubereats",
            displayName  = "Uber Eats",
            packageName  = "com.ubercab.eats",
            offerKeywords = listOf("accept", "decline", "$", "mi"),
            supported    = false  // coming soon
        ),
        GigPlatform(
            id           = "grubhub",
            displayName  = "Grubhub",
            packageName  = "com.grubhub.android",
            offerKeywords = listOf("accept", "decline", "$", "mi"),
            supported    = false  // coming soon
        ),
        GigPlatform(
            id           = "instacart",
            displayName  = "Instacart",
            packageName  = "com.instacart.client",
            offerKeywords = listOf("accept", "decline", "$", "mi"),
            supported    = false  // coming soon
        )
    )

    // Returns only supported (active) platforms
    val SUPPORTED get() = ALL.filter { it.supported }

    // Look up platform by package name
    fun byPackage(pkg: String) = ALL.firstOrNull { it.packageName == pkg }

    // Parse comma-separated platform IDs from DB value
    fun fromConfig(csv: String): List<GigPlatform> {
        val ids = csv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return ALL.filter { it.id in ids }
    }

    // Serialize list of platforms to comma-separated IDs for DB storage
    fun toConfig(platforms: List<GigPlatform>): String =
        platforms.joinToString(",") { it.id }
}
