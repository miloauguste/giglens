package com.augusteenterprise.giglens.town
// Author: Claude (Anthropic) - 2026-06-20
// Detects driver dot (blue) and delivery pins (white) in DoorDash offer map screenshots.
// Pure Kotlin, zero new dependencies — uses android.graphics.Color.colorToHSV() and
// bitmap.getPixels() bulk read for speed (~288K samples on a 1080x2400 screenshot at step=3).

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import kotlin.math.sqrt

private const val TAG = "PinDetector"

// Scan every Nth pixel in each axis — reduces work by STEP² without losing blob shape
private const val STEP = 3

// Blob size bounds in original-resolution pixels; divide by STEP² for sampled-pixel counts.
// BLOB_MIN_PX = 50: allows driver dots down to ~8px diameter (area ~50px) which appear in
// zoomed-out maps (wide area shown = small dots). Prior value of 200 filtered out the driver
// dot when the Taco Bell offer showed Hainesport→Eastampton (~10mi span) — dot was ~12–15px.
private const val BLOB_MIN_PX = 50
private const val BLOB_MAX_PX = 5000

private val NEIGHBORS = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))

data class PinDetectionResult(
    val driverDot: PointF?,
    val briefcasePins: List<PointF>,  // pickup pins, sorted closest-to-driver first
    val housePins: List<PointF>,      // dropoff pins, farthest-from-driver first
    val success: Boolean
)

object PinDetector {

    // CORRECT: @Volatile so the write from the takeScreenshot executor thread is
    //          immediately visible to the coroutine reading it in estimateTown()
    // WRONG:   plain var — JVM may cache stale value on reading thread
    @Volatile var latestResult: PinDetectionResult? = null

    fun detect(bitmap: Bitmap): PinDetectionResult {
        val w = bitmap.width
        val h = bitmap.height

        // Bulk read avoids ~288K individual JNI getPixel() calls on a 1080x2400 screenshot
        val allPixels = IntArray(w * h)
        bitmap.getPixels(allPixels, 0, w, 0, 0, w, h)

        val hsv = FloatArray(3)
        val blueGridPts  = mutableListOf<Pair<Int, Int>>()  // (grid_x, grid_y)
        val whiteGridPts = mutableListOf<Pair<Int, Int>>()

        for (y in 0 until h step STEP) {
            for (x in 0 until w step STEP) {
                Color.colorToHSV(allPixels[y * w + x], hsv)
                val hDeg = hsv[0]        // 0–360
                val s255 = hsv[1] * 255f // scaled to 0–255 to match spec thresholds
                val v255 = hsv[2] * 255f

                when {
                    hDeg in 200f..240f && s255 > 150f && v255 > 100f ->
                        blueGridPts.add(Pair(x / STEP, y / STEP))
                    v255 > 200f && s255 < 30f ->
                        whiteGridPts.add(Pair(x / STEP, y / STEP))
                }
            }
        }

        Log.d(TAG, "detect: blue=${blueGridPts.size} white=${whiteGridPts.size} grid pts (bitmap=${w}x${h} step=$STEP)")

        // Blob size in sampled-pixel units
        val minBlob = BLOB_MIN_PX / (STEP * STEP)
        val maxBlob = BLOB_MAX_PX / (STEP * STEP)

        val blueBlobs  = findBlobs(blueGridPts).filter  { it.size in minBlob..maxBlob }
        val whiteBlobs = findBlobs(whiteGridPts).filter { it.size in minBlob..maxBlob }

        Log.d(TAG, "detect: blueBlobs=${blueBlobs.size} whiteBlobs=${whiteBlobs.size} (sampled bounds $minBlob..$maxBlob)")

        // Driver dot = the single qualifying blue blob (take largest if multiple)
        val driverDot = blueBlobs.maxByOrNull { it.size }?.let { gridCentroid(it) }

        if (driverDot == null || whiteBlobs.isEmpty()) {
            Log.w(TAG, "detect: driverDot=$driverDot whiteBlobs=${whiteBlobs.size} — success=false")
            val result = PinDetectionResult(driverDot, emptyList(), emptyList(), false)
            latestResult = result
            return result
        }

        // Sort white blob centroids by distance from driver dot
        val whiteCentroids = whiteBlobs.map { gridCentroid(it) }
            .sortedBy { pixelDist(it, driverDot) }

        // Closest = briefcase (pickup); farthest = house (dropoff)
        val briefcasePins = if (whiteCentroids.size == 1) whiteCentroids else whiteCentroids.dropLast(1)
        val housePins     = listOf(whiteCentroids.last())

        Log.i(TAG, "detect: driverDot=$driverDot briefcase=${briefcasePins.size} house=${housePins.size} — success=true")
        val result = PinDetectionResult(
            driverDot     = driverDot,
            briefcasePins = briefcasePins,
            housePins     = housePins,
            success       = true
        )
        latestResult = result
        return result
    }

    /**
     * Connected-components flood fill on a flat list of (grid_x, grid_y) pairs.
     * Returns each blob as a list of grid-coordinate pairs.
     */
    private fun findBlobs(pts: List<Pair<Int, Int>>): List<List<Pair<Int, Int>>> {
        if (pts.isEmpty()) return emptyList()

        // CORRECT: shl 20 supports grid coords up to ~1M — handles 4K screens at any step value
        // WRONG:   shl 16 (max 65535) — breaks on high-res devices with small step values
        val ptSet   = pts.mapTo(HashSet(pts.size * 2)) { (gx, gy) -> gridKey(gx, gy) }
        val visited = HashSet<Long>(pts.size)
        val blobs   = mutableListOf<List<Pair<Int, Int>>>()

        for ((startGx, startGy) in pts) {
            val startKey = gridKey(startGx, startGy)
            if (startKey in visited) continue

            val blob  = mutableListOf<Pair<Int, Int>>()
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue.add(Pair(startGx, startGy))
            visited.add(startKey)

            while (queue.isNotEmpty()) {
                val (cx, cy) = queue.removeFirst()
                blob.add(Pair(cx, cy))

                for ((dx, dy) in NEIGHBORS) {
                    val nx   = cx + dx
                    val ny   = cy + dy
                    val nkey = gridKey(nx, ny)
                    if (nkey !in visited && nkey in ptSet) {
                        visited.add(nkey)
                        queue.add(Pair(nx, ny))
                    }
                }
            }
            blobs.add(blob)
        }
        return blobs
    }

    // Returns centroid in original bitmap pixel coordinates
    private fun gridCentroid(pts: List<Pair<Int, Int>>): PointF {
        var sumX = 0.0
        var sumY = 0.0
        for ((gx, gy) in pts) { sumX += gx * STEP; sumY += gy * STEP }
        return PointF((sumX / pts.size).toFloat(), (sumY / pts.size).toFloat())
    }

    private fun pixelDist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun gridKey(gx: Int, gy: Int): Long = gx.toLong() shl 20 or gy.toLong()
}
