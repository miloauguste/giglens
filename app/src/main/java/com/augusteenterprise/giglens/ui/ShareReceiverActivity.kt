package com.augusteenterprise.giglens.ui
// Author: Claude - Fixed deprecated getParcelableExtra (Issue #2)

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.data.OfferCapture
import com.augusteenterprise.giglens.geocoding.GeocodingHelper
import com.augusteenterprise.giglens.location.LocationHelper
import com.augusteenterprise.giglens.ocr.OfferParser
import com.augusteenterprise.giglens.ocr.StreetExtractor
import com.augusteenterprise.giglens.scoring.OfferScorer
import com.augusteenterprise.giglens.service.EXTRA_NET_VALUE
import com.augusteenterprise.giglens.service.EXTRA_VERDICT
import com.augusteenterprise.giglens.service.EXTRA_PAY_AMOUNT
import com.augusteenterprise.giglens.service.EXTRA_RESTAURANT
import com.augusteenterprise.giglens.service.EXTRA_PICKUP_MILES
import com.augusteenterprise.giglens.service.EXTRA_TOTAL_MILES
import com.augusteenterprise.giglens.service.EXTRA_VEHICLE_COST
import com.augusteenterprise.giglens.service.EXTRA_TIME_COST
import com.augusteenterprise.giglens.service.EXTRA_TOTAL_COST
import com.augusteenterprise.giglens.service.EXTRA_MINUTES_ON_JOB
import com.augusteenterprise.giglens.service.EXTRA_SCORE
import com.augusteenterprise.giglens.service.EXTRA_COST_PER_MILE
import com.augusteenterprise.giglens.service.OfferOverlayService
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
            val imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
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
                    Log.d(TAG, "OCR result (${rawText.length} chars)")

                    val parsed = OfferParser.parse(rawText)

                    if (parsed.isOfferScreen) {
                        Log.i(TAG, "Offer: \$${parsed.payAmount} | ${parsed.distance} mi | ${parsed.restaurant}")

                        lifecycleScope.launch {
                            val db = GigLensApp.instance.database
                            val captureDao = db.offerCaptureDao()
                            val configDao = db.scorerConfigDao()
                            val scorer = OfferScorer(configDao)

                            val location = LocationHelper.getCurrentLocation(applicationContext)
                            val addresses = StreetExtractor.extract(rawText)
                            
                            val distanceEstimate = GeocodingHelper.estimateDeliveryDistance(
                                pickupStreet = addresses.pickupStreet,
                                dropoffStreet = addresses.dropoffStreet,
                                regionHint = null
                            )

                            val pickupDistance: Double? =
                                if (location != null && distanceEstimate.pickupPoint != null) {
                                    LocationHelper.straightLineDistance(
                                        location.latitude, location.longitude,
                                        distanceEstimate.pickupPoint.lat,
                                        distanceEstimate.pickupPoint.lon
                                    ) * 1.3
                                } else null

                            val personalAvg = captureDao.getAverageScore()
                            val result = scorer.score(
                                payAmount = parsed.payAmount,
                                deliveryDistance = parsed.distance,
                                pickupDistance = pickupDistance,
                                personalAvgScore = personalAvg
                            )

                            if (result != null) {
                                Log.i(TAG, "Score: ${result.score} | Verdict: ${result.verdict} | Net: ${result.netValue}")
                                
                                // Save to DB
                                val capture = OfferCapture(
                                    payAmount = parsed.payAmount,
                                    distance = parsed.distance,
                                    restaurant = parsed.restaurant,
                                    screenshotPath = savedPath,
                                    rawOcrText = rawText,
                                    platform = detectPlatform(rawText),
                                    score = result.score,
                                    verdict = result.verdict.name,
                                    payPerMile = result.payPerMile,
                                    vsPersonalAvg = result.vsPersonalAvg,
                                    driverLat = location?.latitude,
                                    driverLon = location?.longitude,
                                    pickupDistance = result.pickupDistance,
                                    totalDistance = result.totalDistance,
                                    truePayPerMile = result.truePayPerMile,
                                    vehicleCost = result.vehicleCost,
                                    netValue = result.netValue
                                )
                                captureDao.insert(capture)
                                
                                // Start floating pill service
                                val serviceIntent = Intent(this@ShareReceiverActivity, OfferOverlayService::class.java).apply {
                                    putExtra(EXTRA_NET_VALUE,      result.netValue)
                                    putExtra(EXTRA_VERDICT,        result.verdict.name)
                                    putExtra(EXTRA_PAY_AMOUNT,     parsed.payAmount)
                                    putExtra(EXTRA_RESTAURANT,     parsed.restaurant ?: "")
                                    putExtra(EXTRA_PICKUP_MILES,   parsed.distance ?: 0.0)
                                    putExtra(EXTRA_TOTAL_MILES,    result.totalDistance ?: 0.0)
                                    putExtra(EXTRA_VEHICLE_COST,   result.vehicleCost)
                                    putExtra(EXTRA_TIME_COST,      result.timeCost)
                                    putExtra(EXTRA_TOTAL_COST,     result.totalCost)
                                    putExtra(EXTRA_MINUTES_ON_JOB, result.minutesOnJob)
                                    putExtra(EXTRA_SCORE,          result.score)
                                    putExtra(EXTRA_COST_PER_MILE,  result.costPerMileUsed)
                                }
                                Log.d(TAG, "Starting OfferOverlayService with netValue=${result.netValue}")
                                startService(serviceIntent)
                            }
                            
                            finish()
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
            else -> "Unknown"
        }
    }
}
