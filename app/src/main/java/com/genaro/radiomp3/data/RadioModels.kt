package com.genaro.radiomp3.data

import com.google.gson.annotations.SerializedName

data class Station(
    @SerializedName("stationuuid") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url_resolved") val url: String,
    @SerializedName("favicon") val favicon: String?,
    @SerializedName("homepage") val homepage: String?,
    @SerializedName("country") val country: String,
    @SerializedName("codec") val codec: String?,
    @SerializedName("bitrate") val bitrate: Int?,
    @SerializedName("votes") val votes: Int = 0,
    @SerializedName("clickcount") val clickCount: Int = 0
) {
    // Helper function to get formatted audio quality
    fun getAudioQuality(): String {
        val codecStr = codec?.uppercase() ?: "?"
        val bitrateStr = bitrate?.let { "${it}k" } ?: "?"
        return "$codecStr @ $bitrateStr"
    }

    // Helper to determine quality level (for filtering)
    fun getQualityLevel(): AudioQuality {
        val br = bitrate ?: 0
        val cd = codec?.lowercase() ?: ""

        return when {
            cd.contains("flac") || br >= 700 -> AudioQuality.LOSSLESS
            br >= 256 || (cd.contains("aac") && br >= 160) -> AudioQuality.HIGH
            br >= 128 || (cd.contains("aac") && br >= 96) -> AudioQuality.MEDIUM
            else -> AudioQuality.LOW
        }
    }
}

data class Country(
    @SerializedName("name") val name: String,
    @SerializedName("stationcount") val stationCount: Int
)

data class Favorite(
    val id: String,
    val url: String,
    val name: String,
    val country: String,
    val favicon: String?,
    val homepage: String?,
    var order: Int,
    val addedAt: Long = System.currentTimeMillis()
)

enum class AudioQuality {
    LOSSLESS,  // FLAC or 700+ kbps
    HIGH,      // 256+ kbps or AAC 160+
    MEDIUM,    // 128+ kbps or AAC 96+
    LOW        // < 128 kbps
}

enum class BufferMode {
    LOW, MEDIUM, HIGH;

    fun getBufferMs(): Int = when (this) {
        LOW -> 15000
        MEDIUM -> 30000
        HIGH -> 60000
    }
}
