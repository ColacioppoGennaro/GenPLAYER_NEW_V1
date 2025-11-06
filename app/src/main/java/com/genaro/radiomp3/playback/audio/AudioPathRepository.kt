package com.genaro.radiomp3.playback.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Repository for Audio Path information (Source + Sink)
 * Provides "nerd-friendly" transparency on:
 * - Where audio data comes from (transport, format, bitrate, buffer)
 * - Where audio data goes (device, format, mixer, resampling, offload)
 *
 * Event-driven design: no polling, minimal battery impact
 */
class AudioPathRepository(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val usbAnalyzer = USBAudioAnalyzer(context)

    companion object {
        private const val TAG = "AudioPathRepo"
    }

    // ===== DATA CLASSES =====

    data class SourceInfo(
        val transport: String,          // "Wi-Fi", "Ethernet", "Cellular", "Local"
        val transportDetails: String,   // "Wi-Fi 802.11ax 780 Mbps −56 dBm", "Ethernet 1 Gbps"
        val origin: String,             // "Local", "SD", "OTG", "NAS", "HTTP"
        val codec: String?,             // "FLAC", "MP3", "AAC"
        val sampleRateHz: Int?,         // 44100
        val bitDepth: Int?,             // 16, 24, 32
        val channels: Int?,             // 2, 6, 8
        val bitrateKbps: Int?,          // 850
        val inputBufferSec: Double?     // 1.2
    ) {
        fun toCompactString(): String {
            val parts = mutableListOf<String>()

            // Transport
            parts.add("Origine: $transportDetails")

            // Format
            if (codec != null && sampleRateHz != null) {
                val format = buildString {
                    append(codec)
                    append(" ${sampleRateHz / 1000}kHz")
                    if (bitDepth != null) append("/$bitDepth-bit")
                }
                parts.add(format)
            }

            // Bitrate
            if (bitrateKbps != null && bitrateKbps > 0) {
                parts.add("${bitrateKbps}kbps")
            }

            // Buffer
            if (inputBufferSec != null && inputBufferSec > 0) {
                parts.add("Buffer %.1fs".format(inputBufferSec))
            }

            return parts.joinToString(" • ")
        }
    }

    data class SinkInfo(
        val routeLabel: String,         // "USB DAC – Topping E30", "BT – WH-1000XM5"
        val deviceType: Int,            // AudioDeviceInfo.TYPE_*
        val mixerRateHz: Int?,          // 48000
        val outputSampleRateHz: Int?,   // 44100
        val outputBitDepth: Int?,       // 16, 24, 32
        val channels: Int?,             // 2, 6, 8
        val resampling: Boolean?,       // true if input SR != output/mixer
        val offload: Boolean?,          // true/false
        val notes: String?              // "codec BT non esposto", "UAC2"
    ) {
        fun toCompactString(): String {
            val parts = mutableListOf<String>()

            // Device
            parts.add("Uscita: $routeLabel")

            // Format
            if (outputSampleRateHz != null) {
                val format = buildString {
                    append("${outputSampleRateHz / 1000}kHz")
                    if (outputBitDepth != null) append("/$outputBitDepth-bit")
                }
                parts.add(format)
            }

            // Mixer/Resampling
            if (mixerRateHz != null) {
                val resamplingText = if (resampling == true) "resampling" else "no resampling"
                parts.add("Mixer ${mixerRateHz / 1000}kHz ($resamplingText)")
            }

            // Offload
            if (offload != null) {
                parts.add("Offload ${if (offload) "ON" else "OFF"}")
            }

            // Notes
            if (!notes.isNullOrEmpty()) {
                parts.add(notes)
            }

            return parts.joinToString(" • ")
        }
    }

    // ===== SOURCE INFO =====

    /**
     * Get transport information (Wi-Fi, Ethernet, Cellular, Local)
     */
    fun getTransportInfo(): Pair<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "Local" to "Local storage"
        }

        try {
            val network = connectivityManager.activeNetwork
            if (network == null) {
                return "Local" to "Local storage"
            }

            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities == null) {
                return "Local" to "Local storage"
            }

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    "Ethernet" to "Ethernet (1 Gbps stimato)"
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    getWifiDetails()
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    "Cellular" to "Rete mobile"
                }
                else -> {
                    "Local" to "Local storage"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transport info", e)
            return "Local" to "Local storage"
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiDetails(): Pair<String, String> {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null) {
                return "Wi-Fi" to "Wi-Fi connected"
            }

            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo == null) {
                return "Wi-Fi" to "Wi-Fi connected"
            }

            val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (connectionInfo.wifiStandard) {
                    6 -> "802.11ax"  // WIFI_STANDARD_11AX
                    5 -> "802.11ac"  // WIFI_STANDARD_11AC
                    4 -> "802.11n"   // WIFI_STANDARD_11N
                    else -> "802.11a/b/g"
                }
            } else {
                "802.11"
            }

            val linkSpeed = connectionInfo.linkSpeed // Mbps
            val rssi = connectionInfo.rssi // dBm

            val details = "Wi-Fi $standard ${linkSpeed} Mbps ${rssi} dBm"
            return "Wi-Fi" to details
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi details", e)
            return "Wi-Fi" to "Wi-Fi connected"
        }
    }

    /**
     * Detect origin from URI
     */
    fun detectOrigin(uri: Uri?): String {
        if (uri == null) return "Unknown"

        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: ""
                when {
                    path.contains("/storage/emulated/") -> "Local"
                    path.contains("/mnt/usb") || path.contains("/usbstorage") -> "OTG"
                    path.contains("/sdcard") -> "SD"
                    else -> "Local"
                }
            }
            "content" -> {
                val authority = uri.authority ?: ""
                when {
                    authority.contains("externalstorage") -> "SD/OTG"
                    authority.contains("media") -> "MediaStore"
                    else -> "Local"
                }
            }
            "http", "https" -> "HTTP/HTTPS"
            "smb" -> "NAS"
            else -> uri.scheme ?: "Unknown"
        }
    }

    // ===== SINK INFO =====

    /**
     * Get current audio output device info
     */
    fun getCurrentSinkInfo(): SinkInfo {
        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val currentDevice = devices.firstOrNull { it.isSink }

            if (currentDevice == null) {
                return createFallbackSinkInfo()
            }

            val typeLabel = getDeviceTypeLabel(currentDevice.type)
            val productName = currentDevice.productName?.toString() ?: "Audio Device"
            val routeLabel = "$typeLabel – $productName"

            // Get mixer rate
            val mixerRate = try {
                audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            } catch (e: Exception) {
                null
            }

            // USB-specific info
            val notes = when (currentDevice.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Codec BT non esposto"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    val usbDevice = usbAnalyzer.getPrimaryUSBDevice()
                    if (usbDevice != null) "UAC (max ${usbDevice.maxSampleRate}kHz)" else "UAC"
                }
                else -> null
            }

            return SinkInfo(
                routeLabel = routeLabel,
                deviceType = currentDevice.type,
                mixerRateHz = mixerRate,
                outputSampleRateHz = null, // Will be filled when we have AudioTrack info
                outputBitDepth = null,
                channels = null,
                resampling = null,
                offload = null,
                notes = notes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sink info", e)
            return createFallbackSinkInfo()
        }
    }

    private fun getDeviceTypeLabel(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB DAC"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT A2DP"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cuffie"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT SCO"
            else -> "Audio"
        }
    }

    private fun createFallbackSinkInfo(): SinkInfo {
        return SinkInfo(
            routeLabel = "Audio Device",
            deviceType = AudioDeviceInfo.TYPE_UNKNOWN,
            mixerRateHz = null,
            outputSampleRateHz = null,
            outputBitDepth = null,
            channels = null,
            resampling = null,
            offload = null,
            notes = null
        )
    }

    /**
     * Create SourceInfo from ExoPlayer format and URI
     */
    fun createSourceInfo(
        uri: Uri?,
        codec: String?,
        sampleRateHz: Int?,
        bitDepth: Int?,
        channels: Int?,
        bitrateKbps: Int?
    ): SourceInfo {
        val (transport, transportDetails) = getTransportInfo()
        val origin = detectOrigin(uri)

        return SourceInfo(
            transport = transport,
            transportDetails = transportDetails,
            origin = origin,
            codec = codec,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            channels = channels,
            bitrateKbps = bitrateKbps,
            inputBufferSec = null // Will be calculated separately
        )
    }
}
