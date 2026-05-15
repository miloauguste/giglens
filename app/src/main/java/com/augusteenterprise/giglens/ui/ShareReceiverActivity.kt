package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic)
// Receives shared screenshots from other apps, runs OCR, scores offer, saves data.

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.OfferCapture
import com.augusteenterprise.giglens.ocr.OfferParser
import com.augusteenterprise.giglens.scoring.OfferScorer
import com.augusteenterprise.giglens.location.LocationHelper
import com.augusteenterprise.giglens.scoring.Verdict
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ShareReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShareReceiver"
    }

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (imageUri != null) {
                processSharedImage(imageUri)
            } else {
                Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Unsupported share type", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processSharedImage(uri: Uri) {
        Toast.makeText(this, "Processing offer screenshot…", Toast.LENGTH_SHORT).show()

        try {
            val inputImage = InputImage.fromFilePath(this, uri)
            val savedPath = saveImageCopy(uri)

            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    Log.d(TAG, "OCR result (${rawText.length} chars): ${rawText.take(300)}")

                    val parsed = OfferParser.parse(rawText)

                    if (parsed.isOfferScreen) {
                        Log.i(TAG, "Offer: \$${parsed.payAmount} | ${parsed.distance} mi | ${parsed.restaurant}")

                        lifecycleScope.launch {
                            val db = GigLensApp.instance.database
                            val captureDao = db.offerCaptureDao()
                            val configDao = db.scorerConfigDao()

                            // ── Score the offer ───────────────────────────────
			    val scorer = OfferScorer(configDao)

			   // Get driver GPS location for pickup distance calculation
				val location = LocationHelper.getCurrentLocation(applicationContext)
				val pickupDistance = location?.let {
				Log.d(TAG, "Driver location: ${it.latitude}, ${it.longitude}")
    			// Straight-line estimate — good enough until Maps API is added
   			 // TODO: replace with Maps Distance Matrix API for road distance
			    null // placeholder until restaurant geocoding is implemented
			}

			// Get driver's personal average score from last 30 offers
			val personalAvg = captureDao.getAverageScore()	
                            Log.d(TAG, "Personal avg score: $personalAvg")

			    val result = scorer.score(
				    payAmount        = parsed.payAmount,
				    deliveryDistance = parsed.distance,
				    pickupDistance   = pickupDistance,
			    	personalAvgScore = personalAvg
				)	
                            if (result != null) {
                                Log.i(TAG, "Score: ${result.score} | Verdict: ${result.verdict} | " +
                                    "\$/mi: ${"%.2f".format(result.payPerMile)} | " +
                                    "vs avg: ${result.vsPersonalAvg?.let { "${"%.1f".format(it)}%" } ?: "n/a"} | " +
                                    "failedFloor: ${result.failedFloor}")
                            } else {
                                Log.d(TAG, "Score: null (missing pay or distance)")
                            }

                            // ── Save to DB with score fields ──────────────────
                            val capture = OfferCapture(
                                payAmount      = parsed.payAmount,
                                distance       = parsed.distance,
                                restaurant     = parsed.restaurant,
                                screenshotPath = savedPath,
                                rawOcrText     = rawText,
                                platform       = detectPlatform(rawText),
                                score          = result?.score,
                                verdict        = result?.verdict?.name,
                                payPerMile     = result?.payPerMile,
                                vsPersonalAvg  = result?.vsPersonalAvg,
				driverLat      = location?.latitude,
				driverLon      = location?.longitude,
    				pickupDistance = result?.pickupDistance,
    				deliveryDistance = parsed.distance,
    				totalDistance  = result?.totalDistance,
    				truePayPerMile = result?.truePayPerMile,
				vehicleCost    = result?.vehicleCost,
				netValue       = result?.netValue
                            )
                            val id = captureDao.insert(capture)
                            Log.i(TAG, "Offer saved with id=$id score=${result?.score} verdict=${result?.verdict?.name}")

                            // ── Build toast with verdict ──────────────────────
                            runOnUiThread {
                                val verdictEmoji = when (result?.verdict) {
                                    Verdict.TAKE       -> "🟢"
                                    Verdict.BORDERLINE -> "🟡"
                                    Verdict.SKIP       -> "🔴"
                                    null               -> "📋"
                                }
                                val msg = buildString {
                                    append("$verdictEmoji Offer captured!")
                                    parsed.payAmount?.let { append(" \$${"%.2f".format(it)}") }
                                    parsed.distance?.let { append(" • ${it} mi") }
                                    result?.let {
                                        append(" • Net: ${"%.2f".format(it.netValue)}")
                                        append(" • Score: ${it.score}")
                                if (it.failedFloor) append(" ⚠️")
                                }
                                }
                                Toast.makeText(this@ShareReceiverActivity, msg, Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }
                    } else {
                        Log.d(TAG, "Not recognized as an offer screen")
                        Toast.makeText(this, "Couldn't detect an offer in this screenshot", Toast.LENGTH_LONG).show()
                        savedPath?.let { File(it).delete() }
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed: ${e.message}", e)
                    Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
                    finish()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveImageCopy(uri: Uri): String? {
        return try {
            val dir = File(getExternalFilesDir(null), "GigLens/screenshots")
            dir.mkdirs()
            val timestamp = System.currentTimeMillis()
            val file = File(dir, "offer_$timestamp.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving screenshot: ${e.message}", e)
            null
        }
    }

    private fun detectPlatform(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("doordash") || lower.contains("dasher") -> "DoorDash"
            lower.contains("uber eats") || lower.contains("uber")  -> "Uber Eats"
            lower.contains("grubhub")                              -> "Grubhub"
            lower.contains("instacart")                            -> "Instacart"
            lower.contains("amazon flex")                          -> "Amazon Flex"
            else                                                   -> "Unknown"
        }
    }
}
