package com.augusteenterprise.giglens.service

// Author: Claude (Anthropic)
// NotificationListenerService that captures DoorDash offer details
// from notifications. This is the default (non-scary) capture method.

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.OfferCapture
import com.augusteenterprise.giglens.ocr.OfferParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OfferNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "OfferNotification"
        private const val DOORDASH_PACKAGE = "com.dd.doordash"
        private const val CAPTURE_COOLDOWN_MS = 3000L

        var isRunning = false
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var lastCaptureTime = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        isRunning = true
        Log.i(TAG, "NotificationListenerService connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Only process DoorDash notifications
        if (sbn.packageName != DOORDASH_PACKAGE) return

        // Cooldown to avoid duplicate captures
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < CAPTURE_COOLDOWN_MS) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract text from notification
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        // Combine all notification text
        val fullText = listOf(title, text, bigText, subText)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (fullText.isBlank()) return

        Log.d(TAG, "DoorDash notification: $fullText")

        // Check if this looks like an offer notification
        val parsed = NotificationOfferParser.parse(fullText)

        if (parsed.isOffer) {
            lastCaptureTime = now
            Log.i(TAG, "Offer detected: \$${parsed.payAmount} | ${parsed.distance} mi | ${parsed.restaurant}")

            serviceScope.launch {
                val capture = OfferCapture(
                    payAmount = parsed.payAmount,
                    distance = parsed.distance,
                    restaurant = parsed.restaurant,
                    rawOcrText = fullText,
                    platform = "DoorDash"
                )
                val id = GigLensApp.instance.database.offerCaptureDao().insert(capture)
                Log.i(TAG, "Offer saved to DB with id=$id")
            }
        } else {
            Log.d(TAG, "Not an offer notification — skipping")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }

    override fun onListenerDisconnected() {
        isRunning = false
        Log.i(TAG, "NotificationListenerService disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        isRunning = false
        serviceJob.cancel()
        super.onDestroy()
    }
}

/**
 * Parses DoorDash notification text to extract offer details.
 * DoorDash notifications typically look like:
 *   Title: "New delivery opportunity"
 *   Text: "$7.50 - 3.2 mi - McDonald's"
 * or similar formats.
 */
object NotificationOfferParser {

    data class NotificationOffer(
        val isOffer: Boolean = false,
        val payAmount: Double? = null,
        val distance: Double? = null,
        val restaurant: String? = null
    )

    // Dollar amounts: $7.50, $12.00, $ 8.25
    private val PAY_REGEX = Regex("""\$\s?(\d{1,3}(?:\.\d{2})?)""")

    // Distance: 3.2 mi, 5 mi, 1.8 miles
    private val DISTANCE_REGEX = Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*(?:mi(?:les?)?)""", RegexOption.IGNORE_CASE)

    // Keywords that indicate a delivery offer notification
    private val OFFER_KEYWORDS = listOf(
        "delivery", "deliver", "opportunity",
        "order", "pickup", "pick up",
        "dash", "offer"
    )

    fun parse(notificationText: String): NotificationOffer {
        val lower = notificationText.lowercase()
        val hasDollar = PAY_REGEX.containsMatchIn(notificationText)
        val hasKeyword = OFFER_KEYWORDS.any { lower.contains(it) }

        // Must have a dollar amount and at least one offer keyword
        if (!hasDollar || !hasKeyword) {
            return NotificationOffer(isOffer = false)
        }

        val payAmount = PAY_REGEX.findAll(notificationText)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .maxOrNull()

        val distance = DISTANCE_REGEX.find(notificationText)
            ?.groupValues?.get(1)?.toDoubleOrNull()

        // Restaurant: try to extract from the text
        // Common format: "$7.50 - 3.2 mi - McDonald's"
        // or "Deliver from McDonald's"
        val restaurant = extractRestaurant(notificationText)

        return NotificationOffer(
            isOffer = true,
            payAmount = payAmount,
            distance = distance,
            restaurant = restaurant
        )
    }

    private fun extractRestaurant(text: String): String? {
        // Strategy 1: split by " - " and take the last segment that isn't $ or mi
        val dashParts = text.split(" - ", " – ", " — ")
        if (dashParts.size >= 3) {
            val candidate = dashParts.last().trim()
            if (!PAY_REGEX.containsMatchIn(candidate)
                && !DISTANCE_REGEX.containsMatchIn(candidate)
                && candidate.length in 2..50
            ) {
                return candidate
            }
        }

        // Strategy 2: look for "from <restaurant>" pattern
        val fromMatch = Regex("""(?:from|at)\s+(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE)
            .find(text)
        if (fromMatch != null) {
            val name = fromMatch.groupValues[1].trim()
            if (name.length in 2..50) return name
        }

        return null
    }
}
