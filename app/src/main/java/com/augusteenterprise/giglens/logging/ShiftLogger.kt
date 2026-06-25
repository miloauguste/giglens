package com.augusteenterprise.giglens.logging
// Author: Claude (Anthropic) - 2026-06-25
// Writes tagged log entries to a date-stamped file in app-private storage.
//
// WHY THIS EXISTS:
//   Android's logcat ring buffer (~256 KB) fills in 3-5 minutes on a busy phone.
//   Shift logs are gone long before post-shift analysis begins. This logger writes
//   directly to the device filesystem so logs survive the shift regardless of whether
//   milo-dev is connected via ADB.
//
// PULL AFTER SHIFT (once back on home WiFi):
//   adb shell "run-as com.augusteenterprise.giglens \
//     cat /data/data/com.augusteenterprise.giglens/files/shift_YYYY-MM-DD.log"
//
// Or pull all logs at once:
//   adb shell "run-as com.augusteenterprise.giglens \
//     ls /data/data/com.augusteenterprise.giglens/files/" | grep shift

import android.util.Log
import com.augusteenterprise.giglens.GigLensApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShiftLogger {

    private const val TAG          = "ShiftLogger"
    private const val MAX_FILES    = 7        // keep one week of logs
    private const val MAX_FILE_MB  = 10       // rotate mid-shift if a single file exceeds 10 MB

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd",   Locale.US)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val lock    = Any()
    @Volatile private var logFile: File? = null

    fun init() {
        synchronized(lock) {
            val dir   = GigLensApp.instance.filesDir
            logFile   = File(dir, "shift_${dateFmt.format(Date())}.log")
            pruneOldFiles(dir)
            Log.i(TAG, "ShiftLogger ready → ${logFile?.name}")
        }
    }

    fun d(tag: String, msg: String) = append("D", tag, msg)
    fun i(tag: String, msg: String) = append("I", tag, msg)
    fun w(tag: String, msg: String) = append("W", tag, msg)
    fun e(tag: String, msg: String) = append("E", tag, msg)

    private fun append(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        try {
            val line = "${timeFmt.format(Date())} $level/$tag: $msg\n"
            synchronized(lock) {
                // Rotate if the file has grown too large (runaway logging guard)
                if (file.length() > MAX_FILE_MB * 1_048_576L) {
                    logFile = File(file.parentFile, "shift_${dateFmt.format(Date())}_${System.currentTimeMillis()}.log")
                }
                logFile!!.appendText(line)
            }
        } catch (e: Exception) {
            Log.e(TAG, "append failed: ${e.message}")
        }
    }

    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles { f ->
            f.name.startsWith("shift_") && f.name.endsWith(".log")
        } ?: return
        if (files.size > MAX_FILES) {
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_FILES)
                .forEach { it.delete().also { _ -> Log.d(TAG, "pruned ${it.name}") } }
        }
    }
}
