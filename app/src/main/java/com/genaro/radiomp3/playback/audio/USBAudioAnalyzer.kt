package com.genaro.radiomp3.playback.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Analyzes connected USB audio devices
 * Provides transparency on device capabilities:
 * - Max sample rate
 * - Supported formats
 * - Channel configuration
 * - Bit depth
 */
class USBAudioAnalyzer(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    data class USBAudioDevice(
        val name: String,
        val maxSampleRate: Int,
        val supportedChannels: List<Int>,
        val maxChannels: Int,
        val supportsHighRes: Boolean // 192 kHz capable
    ) {
        override fun toString(): String {
            return """
                USB Audio Device: $name
                Max Sample Rate: $maxSampleRate kHz
                Max Channels: $maxChannels
                Supports High-Res (192kHz): $supportsHighRes
            """.trimIndent()
        }
    }

    /**
     * Get info about connected USB audio devices
     */
    fun getConnectedUSBDevices(): List<USBAudioDevice> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w("USBAudioAnalyzer", "USB audio device enumeration requires API 28+")
            return emptyList()
        }

        return try {
            val devices = mutableListOf<USBAudioDevice>()

            // Get all connected audio devices
            @Suppress("MissingPermission")
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            audioDevices.forEach { device ->
                // Only interested in USB audio devices
                if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                ) {
                    val usbDevice = parseUSBDevice(device)
                    if (usbDevice != null) {
                        devices.add(usbDevice)
                        Log.i("USBAudioAnalyzer", "Found USB device: ${usbDevice.name}")
                    }
                }
            }

            devices
        } catch (e: Exception) {
            Log.e("USBAudioAnalyzer", "Error getting USB devices", e)
            emptyList()
        }
    }

    /**
     * Get the primary USB audio device (if any)
     */
    fun getPrimaryUSBDevice(): USBAudioDevice? {
        return getConnectedUSBDevices().firstOrNull()
    }

    /**
     * Check if high-resolution audio (> 96 kHz) is possible
     */
    fun supportsHighResolution(): Boolean {
        return getConnectedUSBDevices().any { it.supportsHighRes }
    }

    /**
     * Get max sample rate available on connected devices
     */
    fun getMaxAvailableSampleRate(): Int {
        return getConnectedUSBDevices().maxOfOrNull { it.maxSampleRate } ?: 48000
    }

    /**
     * Check if device supports specific sample rate
     */
    fun supportsSampleRate(hz: Int): Boolean {
        return getConnectedUSBDevices().any { device ->
            device.maxSampleRate >= hz
        }
    }

    /**
     * Get warning if requested sample rate not supported
     */
    fun getWarningIfUnsupported(requestedHz: Int): String? {
        val maxSupported = getMaxAvailableSampleRate()
        return if (requestedHz > maxSupported) {
            "⚠️ USB Device supporta solo fino a $maxSupported kHz, non $requestedHz kHz"
        } else {
            null
        }
    }

    /**
     * Parse AudioDeviceInfo into USBAudioDevice
     */
    private fun parseUSBDevice(device: AudioDeviceInfo): USBAudioDevice? {
        return try {
            val name = device.productName?.toString() ?: "USB Audio Device"

            // Get sample rates
            val sampleRates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                device.sampleRates.toList()
            } else {
                listOf(44100, 48000, 96000, 192000) // Fallback defaults
            }

            val maxSampleRate = sampleRates.maxOrNull() ?: 48000
            val supportsHighRes = maxSampleRate >= 192000

            // Get channels
            val channels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                device.channelCounts.toList()
            } else {
                listOf(1, 2) // Fallback: mono, stereo
            }

            val maxChannels = channels.maxOrNull() ?: 2

            USBAudioDevice(
                name = name,
                maxSampleRate = maxSampleRate,
                supportedChannels = channels,
                maxChannels = maxChannels,
                supportsHighRes = supportsHighRes
            )
        } catch (e: Exception) {
            Log.e("USBAudioAnalyzer", "Error parsing USB device", e)
            null
        }
    }

    /**
     * Generate human-readable capability string
     */
    fun getCapabilityString(): String {
        val devices = getConnectedUSBDevices()
        return if (devices.isEmpty()) {
            "No USB audio devices connected"
        } else {
            devices.joinToString("\n") { device ->
                """${device.name}
                    |• Max: ${device.maxSampleRate} kHz
                    |• Channels: up to ${device.maxChannels}
                    |• Hi-Res: ${if (device.supportsHighRes) "✅ Yes" else "❌ No"}
                """.trimMargin()
            }
        }
    }
}
