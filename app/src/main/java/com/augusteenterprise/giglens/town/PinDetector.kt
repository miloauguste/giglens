package com.augusteenterprise.giglens.town
// Author: Claude (Anthropic) - 2026-06-20
// Detects driver dot (blue) and delivery pins (white) in DoorDash offer map screenshots.
// Pure Kotlin, zero new dependencies — uses android.graphics.Color.colorToHSV() and
// bitmap.getPixels() bulk read for speed (~288K samples on a 1080x2400 screenshot at step=3).

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import com.augusteenterprise.giglens.logging.ShiftLogger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

private const val TAG = "PinDetector"

// Scan every Nth pixel in each axis — reduces work by STEP² without losing blob shape
private const val STEP = 3

// Blob size bounds in original-resolution pixels; divide by STEP² for sampled-pixel counts.
// BLOB_MIN_PX = 50: allows driver dots down to ~8px diameter (area ~50px) which appear in
// zoomed-out maps (wide area shown = small dots). Prior value of 200 filtered out the driver
// dot when the Taco Bell offer showed Hainesport→Eastampton (~10mi span) — dot was ~12–15px.
// BLOB_MAX_PX = 30000: delivery pin icons are ~120px diameter (~11300px) on a 1.7mi zoomed-in
// map (ID 22). Old value of 5000 (≈80px diameter) always filtered out the actual pins on short
// trips, causing detection to fall back to tiny notification-text blobs instead.
// BLOB_COMPACTNESS_MIN = 0.15: the DoorDash route line forms an elongated blob with ~4% fill
// even when its pixel count passes the size filter. Delivery pin icons are compact circles with
// 50–65% fill. Threshold at 15% cleanly rejects the route line while passing all pin shapes.
// BLOB_MIN_DIM_GRID = 10: both bounding-box dimensions (width AND height) must span at least
// 10 grid units = 30px. Kills road text labels (15–25px tall at all zoom levels) and thin
// route segments without cutting off small pins on zoomed-out maps (~30–40px at 5+mi span).
// NOTIFICATION_ZONE_X/Y: the DoorDash notification badge (circular icon at top-left of the
// cropped bitmap) is a 99×66px white blob that passes all other filters. Exclude anything
// whose bounding-box center falls in the top-left corner: x<25% of width AND y<15% of height.
private const val BLOB_MIN_PX = 50
private const val BLOB_MAX_PX = 30000
private const val BLOB_COMPACTNESS_MIN = 0.15f
private const val BLOB_MIN_DIM_GRID = 14       // 42px minimum in each axis
private const val NOTIFICATION_ZONE_X_FRAC = 0.25f   // left 25% of bitmap width
private const val NOTIFICATION_ZONE_Y_FRAC = 0.22f   // top 22% of cropped bitmap height

private val NEIGHBORS = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))

data class PinDetectionResult(
    val driverDot: PointF?,
    val briefcasePins: List<PointF>,  // pickup pins, sorted closest-to-driver first
    val housePins: List<PointF>,      // dropoff pins, farthest-from-driver first
    val success: Boolean
)

object PinDetector {

    // CORRECT: AtomicReference so the read-and-clear in estimateTown() is one atomic op —
    //          @Volatile alone allows two concurrent callers to both read the same result
    //          before either clears it. getAndSet(null) collapses the read + clear into one.
    val latestResult = AtomicReference<PinDetectionResult?>(null)

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
        ShiftLogger.d(TAG, "--- new offer --- bitmap=${w}x${h} blueGridPts=${blueGridPts.size} whiteGridPts=${whiteGridPts.size}")

        // Blob size in sampled-pixel units
        val minBlob = BLOB_MIN_PX / (STEP * STEP)
        val maxBlob = BLOB_MAX_PX / (STEP * STEP)

        val blueBlobs  = findBlobs(blueGridPts).filter  { it.size in minBlob..maxBlob }
        val notifZoneX = (w * NOTIFICATION_ZONE_X_FRAC).toInt()
        val notifZoneY = (h * NOTIFICATION_ZONE_Y_FRAC).toInt()
        val whiteBlobs = findBlobs(whiteGridPts).filter { blob ->
            if (blob.size !in minBlob..maxBlob) return@filter false
            val minX = blob.minOf { it.first };  val maxX = blob.maxOf { it.first }
            val minY = blob.minOf { it.second }; val maxY = blob.maxOf { it.second }
            // Both axes must span ≥ BLOB_MIN_DIM_GRID grid units (= 30px at STEP=3).
            // Kills road text labels (~15–25px tall) and thin route segments.
            if ((maxX - minX) < BLOB_MIN_DIM_GRID) return@filter false
            if ((maxY - minY) < BLOB_MIN_DIM_GRID) return@filter false
            if (blobCompactness(blob) < BLOB_COMPACTNESS_MIN) return@filter false
            // Reject the DoorDash notification badge (large white circle, top-left of crop).
            val cx = (minX + maxX) * STEP / 2
            val cy = (minY + maxY) * STEP / 2
            if (cx < notifZoneX && cy < notifZoneY) return@filter false
            true
        }

        Log.d(TAG, "detect: blueBlobs=${blueBlobs.size} whiteBlobs=${whiteBlobs.size} " +
            "(sampled $minBlob..$maxBlob compactness>=$BLOB_COMPACTNESS_MIN " +
            "minDim>=$BLOB_MIN_DIM_GRID notifZone=${notifZoneX}x${notifZoneY})")
        ShiftLogger.d(TAG, "blobs after filter: blue=${blueBlobs.size} white=${whiteBlobs.size} " +
            "notifZone=${notifZoneX}x${notifZoneY}")

        // Driver dot = the single qualifying blue blob (take largest if multiple)
        val driverDot = blueBlobs.maxByOrNull { it.size }?.let { gridCentroid(it) }

        Log.d(TAG, "driverDot: pos=(${driverDot?.x?.toInt()},${driverDot?.y?.toInt()}) blueBlobs=${blueBlobs.size}")
        ShiftLogger.d(TAG, "driverDot: pos=(${driverDot?.x?.toInt()},${driverDot?.y?.toInt()})")
        blueBlobs.forEachIndexed { i, blob ->
            val c = gridCentroid(blob)
            Log.d(TAG, "blueBlob[$i]: pos=(${c.x.toInt()},${c.y.toInt()}) sampledPx=${blob.size} distFromDriver=${driverDot?.let { pixelDist(c, it).toInt() }}")
            ShiftLogger.d(TAG, "blueBlob[$i]: pos=(${c.x.toInt()},${c.y.toInt()}) sampledPx=${blob.size}")
        }

        if (driverDot == null || whiteBlobs.isEmpty()) {
            Log.w(TAG, "detect: driverDot=$driverDot whiteBlobs=${whiteBlobs.size} — success=false")
            ShiftLogger.w(TAG, "success=false driverDot=${driverDot != null} whiteBlobs=${whiteBlobs.size}")
            val result = PinDetectionResult(driverDot, emptyList(), emptyList(), false)
            latestResult.set(result)
            return result
        }

        // Sort blobs and centroids together so size is preserved alongside position for logging
        val sortedBlobsWithCentroids = whiteBlobs
            .map { blob -> Pair(blob, gridCentroid(blob)) }
            .sortedBy { (_, centroid) -> pixelDist(centroid, driverDot) }
        val whiteCentroids = sortedBlobsWithCentroids.map { it.second }

        sortedBlobsWithCentroids.forEachIndexed { i, (blob, c) ->
            val minX = blob.minOf { it.first }; val maxX = blob.maxOf { it.first }
            val minY = blob.minOf { it.second }; val maxY = blob.maxOf { it.second }
            val bboxW = (maxX - minX + 1) * STEP
            val bboxH = (maxY - minY + 1) * STEP
            Log.d(TAG, "whiteBlob[$i]: pos=(${c.x.toInt()},${c.y.toInt()}) bbox=${bboxW}x${bboxH} sampledPx=${blob.size} distFromDriver=${pixelDist(c, driverDot).toInt()}px")
            ShiftLogger.d(TAG, "whiteBlob[$i]: pos=(${c.x.toInt()},${c.y.toInt()}) bbox=${bboxW}x${bboxH} distFromDriver=${pixelDist(c, driverDot).toInt()}px")
        }

        val briefcasePins: List<PointF>
        val housePins: List<PointF>

        if (whiteCentroids.size == 1) {
            briefcasePins = whiteCentroids
            housePins     = whiteCentroids
        } else {
            // With the dimension + notification-zone filters above, only actual delivery
            // pin blobs survive — the closest is reliably the pickup (briefcase icon) and
            // the farthest is the dropoff (house icon). A previous dot-product approach
            // tried to detect "doubled-back routes" but misfired on sharp-turn routes
            // (e.g. driver at center → Taco Bell upper-right → customer far-left bends
            // 135°, giving a negative dot product even though pickup IS the closer blob).
            // ShiftLogger now records bbox for every surviving blob so any future mis-
            // classification can be diagnosed from post-shift log pulls.
            val closest  = whiteCentroids.first()
            val farthest = whiteCentroids.last()
            Log.d(TAG, "classify: closest=pickup=(${closest.x.toInt()},${closest.y.toInt()}) dropoff=(${farthest.x.toInt()},${farthest.y.toInt()})")
            ShiftLogger.d(TAG, "classify: pickup=(${closest.x.toInt()},${closest.y.toInt()}) dropoff=(${farthest.x.toInt()},${farthest.y.toInt()})")
            briefcasePins = whiteCentroids.dropLast(1)
            housePins     = listOf(farthest)
        }

        Log.i(TAG, "detect: driverDot=$driverDot briefcase=${briefcasePins.size} house=${housePins.size} — success=true")
        ShiftLogger.i(TAG, "success=true driverDot=(${driverDot.x.toInt()},${driverDot.y.toInt()}) briefcase=${briefcasePins.size} house=${housePins.size}")
        val result = PinDetectionResult(
            driverDot     = driverDot,
            briefcasePins = briefcasePins,
            housePins     = housePins,
            success       = true
        )
        latestResult.set(result)
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

    // Bounding-box fill ratio — compact shapes (circles) score 0.50–0.75; elongated shapes
    // (route lines) score < 0.10. Operates on grid coordinates; ratio is scale-invariant.
    private fun blobCompactness(pts: List<Pair<Int, Int>>): Float {
        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }
        val bboxArea = ((maxX - minX + 1) * (maxY - minY + 1)).toFloat()
        return if (bboxArea > 0f) pts.size / bboxArea else 0f
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
