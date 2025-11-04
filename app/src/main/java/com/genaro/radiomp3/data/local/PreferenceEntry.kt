package com.genaro.radiomp3.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Preference table for storing user settings
 * Designed for transparency: all audio quality, logging, and feedback preferences
 */
@Entity(tableName = "Preference")
data class PreferenceEntry(
    @PrimaryKey
    val key: String,                    // e.g., "show_feedback_banner"
    val value: String,                  // "true", "false", or custom value
    val category: String,               // "audio_info", "quality", "replaygain", "logging", etc.
    val type: String,                   // "boolean", "enum", "int", "string"
    val defaultValue: String,           // Default value for this preference
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Audio Info Display Preferences
 */
object AudioInfoPreferences {
    const val SHOW_FEEDBACK_BANNER = "show_feedback_banner"           // Default: true
    const val SHOW_MINI_TECH_INFO = "show_mini_tech_info"             // Default: true
    const val SHOW_TECHNICAL_PANEL = "show_technical_panel"           // Default: true (PRO button)
    const val FEEDBACK_BANNER_DISMISS_TIME = "feedback_banner_dismiss" // Default: 3000ms
}

/**
 * Audio Quality Monitoring Preferences
 */
object AudioQualityPreferences {
    const val WARN_IF_RESAMPLED = "warn_if_resampled"                 // Default: true
    const val SHOW_USB_DEVICE_INFO = "show_usb_device_info"           // Default: true
    const val MONITOR_BUFFERING = "monitor_buffering"                 // Default: true
    const val SHOW_BITRATE_LIVE = "show_bitrate_live"                 // Default: true
}

/**
 * ReplayGain & Normalization Preferences
 */
object ReplayGainPreferences {
    const val ENABLE_REPLAYGAIN = "enable_replaygain"                 // Default: false (audiophiles prefer disabled)
    const val REPLAYGAIN_MODE = "replaygain_mode"                     // Values: "none", "track", "album", "peak_limiting"
    const val REPLAYGAIN_PRESET = "replaygain_preset"                 // Values: "conservative", "normal", "aggressive"
}

/**
 * Format & Codec Preferences
 */
object FormatPreferences {
    const val PREFERRED_CODEC = "preferred_codec"                     // Values: "auto", "mp3", "flac", "wav", "aac", "opus", "vorbis"
    const val SHOW_FORMAT_PLACEHOLDER = "show_format_placeholder"     // Default: true
    const val ENABLE_GAPLESS = "enable_gapless"                       // Default: true
}

/**
 * Logging & Debug Preferences
 */
object LoggingPreferences {
    const val LOG_LEVEL = "log_level"                                 // Values: "off", "errors", "warnings", "verbose"
    const val LOG_TO_FILE = "log_to_file"                             // Default: false
    const val LOG_PLAYBACK_EVENTS = "log_playback_events"             // Default: true if verbose
    const val LOG_METADATA_PARSING = "log_metadata_parsing"           // Default: true if verbose
    const val LOG_RESAMPLING_EVENTS = "log_resampling_events"         // Default: true if verbose
}

/**
 * Default Preferences Configuration
 */
object DefaultPreferences {
    val defaults = listOf(
        // Audio Info Display
        PreferenceEntry(
            AudioInfoPreferences.SHOW_FEEDBACK_BANNER,
            "true",
            "audio_info",
            "boolean",
            "true"
        ),
        PreferenceEntry(
            AudioInfoPreferences.SHOW_MINI_TECH_INFO,
            "true",
            "audio_info",
            "boolean",
            "true"
        ),
        PreferenceEntry(
            AudioInfoPreferences.SHOW_TECHNICAL_PANEL,
            "true",
            "audio_info",
            "boolean",
            "true"
        ),
        PreferenceEntry(
            AudioInfoPreferences.FEEDBACK_BANNER_DISMISS_TIME,
            "3000",
            "audio_info",
            "int",
            "3000"
        ),

        // Audio Quality Monitoring
        PreferenceEntry(
            AudioQualityPreferences.WARN_IF_RESAMPLED,
            "true",
            "quality",
            "boolean",
            "true"
        ),
        PreferenceEntry(
            AudioQualityPreferences.SHOW_USB_DEVICE_INFO,
            "true",
            "quality",
            "boolean",
            "true"
        ),
        PreferenceEntry(
            AudioQualityPreferences.MONITOR_BUFFERING,
            "true",
            "quality",
            "boolean",
            "true"
        ),
        PreferenceEntry(
            AudioQualityPreferences.SHOW_BITRATE_LIVE,
            "true",
            "quality",
            "boolean",
            "true"
        ),

        // ReplayGain & Normalization
        PreferenceEntry(
            ReplayGainPreferences.ENABLE_REPLAYGAIN,
            "false",
            "replaygain",
            "boolean",
            "false"
        ),
        PreferenceEntry(
            ReplayGainPreferences.REPLAYGAIN_MODE,
            "none",
            "replaygain",
            "enum",
            "none"
        ),

        // Format & Codec
        PreferenceEntry(
            FormatPreferences.PREFERRED_CODEC,
            "auto",
            "format",
            "enum",
            "auto"
        ),
        PreferenceEntry(
            FormatPreferences.SHOW_FORMAT_PLACEHOLDER,
            "true",
            "format",
            "boolean",
            "true"
        ),
        PreferenceEntry(
            FormatPreferences.ENABLE_GAPLESS,
            "true",
            "format",
            "boolean",
            "true"
        ),

        // Logging & Debug
        PreferenceEntry(
            LoggingPreferences.LOG_LEVEL,
            "warnings",
            "logging",
            "enum",
            "warnings"
        ),
        PreferenceEntry(
            LoggingPreferences.LOG_TO_FILE,
            "false",
            "logging",
            "boolean",
            "false"
        )
    )
}
