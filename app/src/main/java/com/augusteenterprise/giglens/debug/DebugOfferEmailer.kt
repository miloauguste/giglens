package com.augusteenterprise.giglens.debug

import android.util.Log
import com.augusteenterprise.giglens.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

// Author: Claude (Anthropic) - Debug-only offer capture emailer
//
// Emails extracted offer data + computed pill result + screenshot for
// manual validation purposes (town accuracy, pin detection, scoring).
//
// CORRECT: gated by BuildConfig.DEBUG only -- never compiled into release builds
// WRONG:   gating with a runtime flag/setting -- credentials and send logic would
//          still ship inside the release APK, decompilable
//
// NOTE: this is the debug/manual-validation path, hardcoded to send to a single
// destination (you), no consent flow. If this becomes a driver-facing "help
// improve GigLens" opt-in feature later, this class needs a per-user destination
// and an explicit consent check before reuse -- see TODO below. Do not extend
// this class directly into that feature without that change.

data class OfferDebugPayload(
    val timestamp: Long,
    val payAmount: Double?,
    val distance: Double?,
    val restaurant: String,
    val deliverByRawText: String,
    val source: String,
    // Pill / ScoreResult fields
    val score: Int?,
    val verdict: String?,          // ScoreResult.verdict.toString()
    val netValue: Double?,
    val payPerMile: Double?,
    val truePayPerMile: Double?,
    // Town estimate
    val townDisplayName: String?,
    val townConfidence: String?
)

object DebugOfferEmailer {

    private const val TAG = "DebugOfferEmailer"

    // TODO(opt-in-version): replace with per-user destination + explicit
    // consent flag once this becomes a driver-facing feature rather than
    // a debug-only validation tool. Do not ship this hardcoded recipient
    // in any build a driver other than you would run.
    private const val DEBUG_RECIPIENT = "miloauguste@gmail.com" // <-- set your address
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = "587"

    /**
     * Fire-and-forget. Looks for the most recent screenshot PNG in the app's
     * debug/ folder (written by OfferDetectorService.testTakeScreenshot())
     * within maxScreenshotAgeMs of `payload.timestamp`, attaches it if found.
     *
     * CORRECT: search by file mtime closest to payload.timestamp -- screenshot
     *          and broadcast are produced by two different async call sites
     * WRONG:   assuming the screenshot file already exists by a fixed name --
     *          testTakeScreenshot() names files by its own timestamp, not the
     *          offer's fingerprint
     */
    fun sendAsync(
        payload: OfferDebugPayload,
        debugDir: File,
        maxScreenshotAgeMs: Long = 15_000L
    ) {
        if (!BuildConfig.DEBUG) {
            // Hard stop. This must never run in a release build.
            return
        }
        // CORRECT: launch on a standalone CoroutineScope, NOT the caller's
        //          scope -- the caller (AccessibilityOfferReceiver) holds a
        //          goAsync() pendingResult that must finish() within ~10s.
        //          SMTP send can take 5-30s on cellular and would block
        //          finish(), causing the system to kill the process before
        //          the email is sent.
        // WRONG:   scope.launch(Dispatchers.IO) as a child of the receiver's
        //          outer launch -- structured concurrency keeps the parent
        //          job alive until all children complete, so pendingResult
        //          would wait for the SMTP send
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val screenshotFile = findRecentScreenshot(debugDir, payload.timestamp, maxScreenshotAgeMs)
                send(payload, screenshotFile)
            } catch (e: Exception) {
                // Never let a debug-email failure affect offer detection.
                Log.w(TAG, "Debug offer email failed: ${e.message}", e)
            }
        }
    }

    private fun findRecentScreenshot(debugDir: File, aroundTimestamp: Long, maxAgeMs: Long): File? {
        if (!debugDir.exists()) return null
        return debugDir.listFiles { f -> f.name.startsWith("offer_screenshot_") && f.name.endsWith(".png") }
            ?.filter { kotlin.math.abs(it.lastModified() - aroundTimestamp) <= maxAgeMs }
            ?.minByOrNull { kotlin.math.abs(it.lastModified() - aroundTimestamp) }
    }

    private fun send(payload: OfferDebugPayload, screenshotFile: File?) {
        val username = BuildConfig.DEBUG_SMTP_USER
        val password = BuildConfig.DEBUG_SMTP_PASS

        if (username.isBlank() || password.isBlank()) {
            Log.w(TAG, "DEBUG_SMTP_USER/PASS not set in BuildConfig -- skipping send")
            return
        }

        val props = Properties().apply {
            put("mail.smtp.host", SMTP_HOST)
            put("mail.smtp.port", SMTP_PORT)
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(username))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(DEBUG_RECIPIENT))
            subject = "[GigLens Debug] Offer ${payload.restaurant.ifBlank { "unknown" }} -- \$${payload.payAmount ?: 0.0}"
        }

        val textPart = MimeBodyPart().apply { setText(buildBodyText(payload, screenshotFile)) }
        val multipart = MimeMultipart().apply { addBodyPart(textPart) }

        if (screenshotFile != null && screenshotFile.exists()) {
            val imagePart = MimeBodyPart()
            val bytes = screenshotFile.readBytes()
            imagePart.dataHandler = DataHandler(ByteArrayDataSource(bytes, "image/png"))
            imagePart.fileName = screenshotFile.name
            multipart.addBodyPart(imagePart)
        } else {
            Log.w(TAG, "No matching screenshot found for timestamp=${payload.timestamp} -- sending without attachment")
        }

        message.setContent(multipart)
        Transport.send(message)
        Log.d(TAG, "Debug offer email sent for timestamp=${payload.timestamp}, screenshot=${screenshotFile?.name ?: "none"}")
    }

    private fun buildBodyText(p: OfferDebugPayload, screenshotFile: File?): String {
        return """
            GigLens Debug Offer Capture
            ----------------------------
            Timestamp: ${p.timestamp}
            Source: ${p.source}

            Raw extracted data:
              Pay amount: ${p.payAmount ?: "N/A"}
              Distance: ${p.distance ?: "N/A"} mi
              Restaurant: ${p.restaurant.ifBlank { "N/A" }}
              Deliver-by raw text: ${p.deliverByRawText.ifBlank { "N/A" }}

            Pill / ScoreResult:
              Score: ${p.score ?: "N/A"}
              Verdict: ${p.verdict ?: "N/A"}
              Net value: ${p.netValue ?: "N/A"}
              Pay per mile: ${p.payPerMile ?: "N/A"}
              True pay per mile: ${p.truePayPerMile ?: "N/A"}

            Town estimate:
              Display name: ${p.townDisplayName ?: "N/A"}
              Confidence: ${p.townConfidence ?: "N/A"}

            Screenshot attached: ${if (screenshotFile != null) screenshotFile.name else "NONE -- no matching file found"}
        """.trimIndent()
    }
}
