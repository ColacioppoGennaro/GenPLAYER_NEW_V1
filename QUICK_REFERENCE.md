# 🎵 GenPlayer - Quick Reference Guide

## 🏗️ ARCHITECTURE AT A GLANCE

```
┌─────────────────────────────────────────────────────────────────┐
│                    NowPlayingActivity                           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ FeedbackBanner (Top) - Shows real-time audio status      │   │
│  │ • Buffering, Format Info, Resampling Warning, Errors    │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Album Art + Technical Details (Overlay on PRO button)   │   │
│  │ • File Info, Format, Audio Format, Metadata, Device     │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Playback Controls (Play, Pause, Skip, Shuffle, Repeat)  │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
         │                              │                 │
         ▼                              ▼                 ▼
┌──────────────────┐    ┌─────────────────────────┐  ┌────────────────┐
│ PreferenceManager│    │ ResamplingMonitor       │  │ USBAudioAnalyzer
│                  │    │ + AudioLog              │  │                │
│ • Get/Set prefs  │    │                         │  │ • Detect USB   │
│ • By category    │    │ Hooks into ExoPlayer    │  │ • Max Hz       │
│ • Flow support   │    │ Detects resampling      │  │ • Capabilities│
└──────────────────┘    │ Logs all events         │  └────────────────┘
         │              └─────────────────────────┘         │
         ▼                      ▼                           ▼
┌──────────────────────────────────────────────────────────────────┐
│               Preference Table (Room Database)                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ Keys: show_feedback_banner, warn_if_resampled, etc.    │    │
│  │ Categories: audio_info, quality, logging, format, etc. │    │
│  │ Typed values: boolean, int, string, enum               │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 📊 FEEDBACK BANNER - WHAT USERS SEE

### When Buffering
```
┌─────────────────────────────────────────┐
│ 🔵 ⏳ Buffering... 45% (2.5 MB/s)      │
└─────────────────────────────────────────┘
```

### When Resampled (NOT Bit-Perfect)
```
┌─────────────────────────────────────────┐
│ 🟠 ⚠️ Risamplato: 44.1 → 48 kHz       │
└─────────────────────────────────────────┘
```

### When Bit-Perfect
```
┌─────────────────────────────────────────┐
│ 🟢 ✅ FLAC 24-bit/96 kHz (bit-perfect) │
└─────────────────────────────────────────┘
```

### When Error
```
┌─────────────────────────────────────────┐
│ 🔴 ❌ File corrotto - impossibile      │
└─────────────────────────────────────────┘
```

### When USB Audio Found
```
┌─────────────────────────────────────────┐
│ 🔵 🎚️ USB DAC: Audiolab M-DAC (192kHz) │
└─────────────────────────────────────────┘
```

---

## 🎯 USAGE EXAMPLES

### Example 1: Show Feedback in NowPlayingActivity
```kotlin
// Simple API
feedbackBanner.showFeedback("Custom message", FeedbackBanner.FeedbackType.INFO)

// Convenience methods
feedbackBanner.showBuffering(45, "2.5 MB/s")
feedbackBanner.showFormatInfo("FLAC", 24, 96)
feedbackBanner.showResamplingWarning(44100, 48000)
feedbackBanner.showError("File corrotto")
feedbackBanner.showUSBDeviceInfo("Audiolab M-DAC", 192)
```

### Example 2: Get User Preference
```kotlin
// In coroutine
lifecycleScope.launch {
    val showFeedback = preferenceManager.getBoolean("show_feedback_banner", true)
    val logLevel = preferenceManager.getString("log_level", "warnings")

    if (showFeedback) {
        feedbackBanner.visibility = View.VISIBLE
    }
}

// Reactive (Flow)
preferenceManager.getBooleanFlow("show_feedback_banner").collect { show ->
    feedbackBanner.visibility = if (show) View.VISIBLE else View.GONE
}
```

### Example 3: Detect Resampling
```kotlin
// In NowPlayingActivity
resamplingMonitor = ResamplingMonitor { inputHz, outputHz ->
    if (inputHz != outputHz) {
        feedbackBanner.showResamplingWarning(inputHz, outputHz)
        AudioLog.resamplingDetected(inputHz, outputHz)
    }
}

controller?.addAnalyticsListener(resamplingMonitor)
```

### Example 4: Check USB Audio Capabilities
```kotlin
val usbDevices = usbAnalyzer.getConnectedUSBDevices()
if (usbDevices.isNotEmpty()) {
    val primary = usbDevices.first()
    feedbackBanner.showUSBDeviceInfo(primary.name, primary.maxSampleRate)

    if (primary.supportsHighRes) {
        Log.d("Player", "Hi-Res audio supported!")
    }
}
```

### Example 5: Log Audio Events
```kotlin
// When playing file
AudioLog.playerStarted("Bohemian Rhapsody", trackId = 42)

// When format detected
AudioLog.formatDetected("FLAC", 24, 96000, 2)

// When metadata loaded
AudioLog.metadataLoaded("Bohemian Rhapsody", "Queen", "A Night at the Opera")

// When error
AudioLog.decoderError("MP3 frame sync error", exception)

// Export logs
val logFile = AudioLog.exportLogs(context)
```

---

## 🔑 PREFERENCE KEYS REFERENCE

### Display
```kotlin
const val SHOW_FEEDBACK_BANNER = "show_feedback_banner"           // true
const val SHOW_MINI_TECH_INFO = "show_mini_tech_info"             // true
const val SHOW_TECHNICAL_PANEL = "show_technical_panel"           // true
const val FEEDBACK_BANNER_DISMISS_TIME = "feedback_banner_dismiss" // 3000ms
```

### Quality
```kotlin
const val WARN_IF_RESAMPLED = "warn_if_resampled"                 // true
const val SHOW_USB_DEVICE_INFO = "show_usb_device_info"           // true
const val MONITOR_BUFFERING = "monitor_buffering"                 // true
const val SHOW_BITRATE_LIVE = "show_bitrate_live"                 // true
```

### Logging
```kotlin
const val LOG_LEVEL = "log_level"                                 // "warnings"
const val LOG_TO_FILE = "log_to_file"                             // false
const val LOG_PLAYBACK_EVENTS = "log_playback_events"             // true
```

---

## 🚀 QUICK INTEGRATION CHECKLIST

### Step 1: Add FeedbackBanner to Layout
```xml
<!-- In activity_now_playing.xml, after back button -->
<com.genaro.radiomp3.ui.widgets.FeedbackBanner
    android:id="@+id/feedbackBanner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone" />
```

### Step 2: Initialize in NowPlayingActivity
```kotlin
private lateinit var feedbackBanner: FeedbackBanner
private lateinit var preferenceManager: PreferenceManager
private lateinit var usbAnalyzer: USBAudioAnalyzer

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_now_playing)

    feedbackBanner = findViewById(R.id.feedbackBanner)
    preferenceManager = PreferenceManager.getInstance(this)
    usbAnalyzer = USBAudioAnalyzer(this)

    // Check if feedback enabled
    lifecycleScope.launch {
        val showFeedback = preferenceManager.getBoolean("show_feedback_banner", true)
        feedbackBanner.visibility = if (showFeedback) View.VISIBLE else View.GONE
    }

    // Check USB devices
    checkUSBDevices()
}

private fun checkUSBDevices() {
    val devices = usbAnalyzer.getConnectedUSBDevices()
    if (devices.isNotEmpty()) {
        val primary = devices.first()
        feedbackBanner.showUSBDeviceInfo(primary.name, primary.maxSampleRate)
    }
}
```

### Step 3: Attach Resampling Monitor
```kotlin
resamplingMonitor = ResamplingMonitor { inputHz, outputHz ->
    feedbackBanner.showResamplingWarning(inputHz, outputHz)
    AudioLog.resamplingDetected(inputHz, outputHz)
}

controller?.addAnalyticsListener(resamplingMonitor)
```

### Step 4: Log Events in PlayerHolder
```kotlin
// In onDownstreamFormatChanged
AudioLog.formatDetected(format, bitDepth, sampleRate, channels)

// In onMetadataChanged
AudioLog.metadataLoaded(title, artist, album)
```

---

## 📈 DATA FLOW EXAMPLE

### User Plays FLAC 24/96
```
1. NowPlayingActivity.onCreate()
   ├─ Load feedbackBanner from layout
   ├─ Initialize PreferenceManager
   ├─ Check "show_feedback_banner" → true
   ├─ Initialize USBAudioAnalyzer
   └─ Check USB devices → "Audiolab M-DAC 192 kHz"
      └─ FeedbackBanner.showUSBDeviceInfo()

2. User taps track in LocalMusicActivity

3. Controller.prepare() → ExoPlayer starts

4. ExoPlayer.onDownstreamFormatChanged()
   ├─ Format detected: FLAC 24-bit 96 kHz
   ├─ AudioLog.formatDetected()
   └─ ResamplingMonitor callback
      └─ Input: 96 kHz, Output: 96 kHz (USB supports 192)
         └─ No resampling needed
         └─ AudioLog.noBitPerfect(96000, 96000) ✅

5. FeedbackBanner.showFormatInfo("FLAC", 24, 96)
   └─ "✅ FLAC 24-bit/96 kHz (bit-perfect)"
   └─ Auto-dismiss after 3 seconds

6. Playback continues...
   └─ If any buffering: FeedbackBanner.showBuffering()
   └─ If any error: FeedbackBanner.showError()
   └─ If resampling needed: FeedbackBanner.showResamplingWarning()

7. User taps PRO button
   ├─ Technical panel opens
   ├─ Shows all metadata: File size, compression, format
   ├─ Shows device info: USB DAC capabilities
   └─ Shows resampling status: ✅ Bit-perfect (96 kHz → 96 kHz)
```

---

## 💾 PERSISTENCE & STATE

**Preferences are stored in Room database**, so they persist across app restarts.

```kotlin
// First run: Auto-initialized with defaults
// Subsequent runs: Loads from DB

// To reset: PreferenceManager.reset()
// To export: PreferenceManager.exportPreferencesAsString()
```

---

## 🔍 DEBUG CHECKLIST

- [ ] Verify PreferenceEntry created in DB (version 2)
- [ ] Verify PreferenceDao methods working
- [ ] Verify PreferenceManager.getInstance() returns same singleton
- [ ] Verify FeedbackBanner displays without crashing
- [ ] Verify ExoPlayer listeners attach correctly
- [ ] Verify USB audio detection works
- [ ] Verify AudioLog writes to file if enabled
- [ ] Verify auto-dismiss timer works
- [ ] Verify preference changes affect UI immediately (Flow)

---

## 📝 NOTES

- **Preferences table is auto-initialized** with defaults on first run
- **All logging is async** (doesn't block UI)
- **FeedbackBanner auto-dismisses** by default (configurable)
- **USB audio detection** requires API 28+ (graceful fallback)
- **Resampling detection** hooks into ExoPlayer analytics (no performance impact)
- **PreferenceManager** is a singleton (safe for concurrent access)

---

**READY FOR INTEGRATION** 🚀
