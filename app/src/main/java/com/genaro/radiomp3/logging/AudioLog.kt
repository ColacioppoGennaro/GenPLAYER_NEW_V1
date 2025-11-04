package com.genaro.radiomp3.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Audio Playback Logging System
 * Structured logging with tags for transparency
 * Logs can be exported for debugging
 */
object AudioLog {

    enum class LogLevel(val value: Int) {
        OFF(0),
        ERRORS(1),
        WARNINGS(2),
        VERBOSE(3)
    }

    enum class LogTag(val prefix: String) {
        PLAYER("[PLAYER]"),
        FORMAT("[FORMAT]"),
        AUDIO_INFO("[AUDIO_INFO]"),
        METADATA("[METADATA]"),
        USB_AUDIO("[USB_AUDIO]"),
        BUFFERING("[BUFFERING]"),
        ERROR("[ERROR]"),
        RESAMPLING("[RESAMPLING]"),
        DECODER("[DECODER]"),
        NETWORK("[NETWORK]"),
        UI("[UI]")
    }

    private var logLevel = LogLevel.WARNINGS
    private var fileLoggingEnabled = false
    private var logFile: File? = null
    private val logBuffer = mutableListOf<String>()
    private const val MAX_LOG_LINES = 1000
    private const val LOG_FILENAME = "genplayer_audio.log"

    /**
     * Initialize logging
     */
    fun init(context: Context, level: LogLevel = LogLevel.WARNINGS, enableFileLogging: Boolean = false) {
        logLevel = level
        fileLoggingEnabled = enableFileLogging

        if (enableFileLogging) {
            val logsDir = File(context.filesDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            logFile = File(logsDir, LOG_FILENAME)
            Log.d("AudioLog", "Logging enabled to: ${logFile?.absolutePath}")
        }
    }

    // ===== PUBLIC LOGGING METHODS =====

    fun i(tag: LogTag, message: String) {
        if (logLevel.value >= LogLevel.VERBOSE.value) {
            val logLine = formatLog("INFO", tag, message)
            Log.i(tag.prefix, message)
            writeToFile(logLine)
        }
    }

    fun d(tag: LogTag, message: String) {
        if (logLevel.value >= LogLevel.WARNINGS.value) {
            val logLine = formatLog("DEBUG", tag, message)
            Log.d(tag.prefix, message)
            writeToFile(logLine)
        }
    }

    fun w(tag: LogTag, message: String) {
        if (logLevel.value >= LogLevel.WARNINGS.value) {
            val logLine = formatLog("WARN", tag, message)
            Log.w(tag.prefix, message)
            writeToFile(logLine)
        }
    }

    fun e(tag: LogTag, message: String, exception: Exception? = null) {
        if (logLevel.value >= LogLevel.ERRORS.value) {
            val fullMessage = if (exception != null) {
                "$message\n${exception.stackTraceToString()}"
            } else {
                message
            }
            val logLine = formatLog("ERROR", tag, fullMessage)
            Log.e(tag.prefix, message, exception)
            writeToFile(logLine)
        }
    }

    // ===== CONVENIENCE METHODS WITH CONTEXT =====

    fun playerStarted(trackTitle: String, trackId: Long) {
        d(LogTag.PLAYER, "Starting playback: track_id=$trackId, title=$trackTitle")
    }

    fun formatDetected(format: String, bitDepth: Int, sampleRate: Int, channels: Int) {
        i(
            LogTag.FORMAT,
            "Detected: $format, $bitDepth-bit, $sampleRate kHz, $channels-channel"
        )
    }

    fun resamplingDetected(inputHz: Int, outputHz: Int) {
        w(LogTag.RESAMPLING, "⚠️ RESAMPLING: $inputHz Hz → $outputHz Hz (NOT bit-perfect)")
    }

    fun noBitPerfect(inputHz: Int, outputHz: Int) {
        i(
            LogTag.RESAMPLING,
            "✅ No resampling: $inputHz Hz → $outputHz Hz (bit-perfect)"
        )
    }

    fun metadataLoaded(title: String, artist: String, album: String) {
        i(LogTag.METADATA, "Metadata loaded: '$title' by '$artist' from '$album'")
    }

    fun artworkFound(source: String) {
        i(LogTag.METADATA, "📷 Artwork found: $source")
    }

    fun usbDeviceDetected(deviceName: String, maxHz: Int) {
        i(LogTag.USB_AUDIO, "🎚️ USB DAC detected: $deviceName ($maxHz kHz)")
    }

    fun bufferingProgress(percent: Int, speedMBps: Double) {
        d(LogTag.BUFFERING, "⏳ Buffering: $percent% (${String.format("%.2f", speedMBps)} MB/s)")
    }

    fun decoderError(reason: String, exception: Exception? = null) {
        e(LogTag.DECODER, "❌ Decoder error: $reason", exception)
    }

    fun fileCorrupted(filename: String) {
        e(LogTag.DECODER, "❌ File corrupted - cannot decode: $filename")
    }

    // ===== FILE OPERATIONS =====

    suspend fun exportLogs(context: Context): File? {
        return try {
            val exportFile = File(context.filesDir, "logs/${LOG_FILENAME}.export")
            exportFile.writeText(logBuffer.joinToString("\n"))
            Log.d("AudioLog", "Logs exported to: ${exportFile.absolutePath}")
            exportFile
        } catch (e: Exception) {
            Log.e("AudioLog", "Error exporting logs", e)
            null
        }
    }

    fun clearLogs() {
        logBuffer.clear()
        logFile?.delete()
        Log.d("AudioLog", "Logs cleared")
    }

    fun getLogs(): String {
        return logBuffer.joinToString("\n")
    }

    fun getLogCount(): Int {
        return logBuffer.size
    }

    // ===== PRIVATE METHODS =====

    private fun formatLog(level: String, tag: LogTag, message: String): String {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        return "$timestamp ${tag.prefix} [$level] $message"
    }

    private fun writeToFile(logLine: String) {
        if (!fileLoggingEnabled || logFile == null) return

        try {
            logBuffer.add(logLine)

            // Keep buffer size manageable
            if (logBuffer.size > MAX_LOG_LINES) {
                logBuffer.removeAt(0)
            }

            // Write to file
            logFile?.appendText("$logLine\n")
        } catch (e: Exception) {
            Log.e("AudioLog", "Error writing to log file", e)
        }
    }
}
