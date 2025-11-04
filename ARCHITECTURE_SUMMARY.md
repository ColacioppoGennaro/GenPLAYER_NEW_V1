# 🎵 GenPlayer V1 - Architecture Summary

## FASE COMPLETATA: Transparency & Preference Layer

Abbiamo implementato la **fondazione per la trasparenza totale** dell'app. Tutto è in posto per comunicare onestamente con l'audiofilo.

---

## 📦 COSA ABBIAMO IMPLEMENTATO

### 1. PREFERENCE SYSTEM
```
✅ PreferenceEntry.kt
   - Modello Room per tutte le preferenze utente
   - Categorie: audio_info, quality, replaygain, format, logging
   - Typed getters/setters (boolean, int, string, enum)

✅ PreferenceDao.kt
   - Query per key, category, all preferences
   - Flow support per reactive updates

✅ PreferenceManager.kt (Singleton)
   - Auto-init con defaults al primo avvio
   - Metodi typed: getBoolean(), getInt(), getString()
   - Flow support per reactive preferences
   - Export preferences as text

✅ AppDatabase.kt (Aggiornato)
   - PreferenceEntry entity aggiunto
   - preferenceDao() abstract method
   - Version incrementata per migration
```

### 2. FEEDBACK SYSTEM
```
✅ FeedbackBanner.kt (Custom View)
   - 4 tipi di feedback: INFO, WARNING, ERROR, SUCCESS
   - Auto-dismiss configurabile (default 3s)
   - Metodi convenience:
     - showFeedback(message, type)
     - showBuffering(%, speed)
     - showResamplingWarning(inputHz, outputHz)
     - showError(reason)
     - showLoading(what)
     - showUSBDeviceInfo(name, hz)

✅ widget_feedback_banner.xml
   - Layout 40dp height, non-intrusive
   - Icon + Message + Dismiss button
   - Colori per tipo feedback
```

### 3. AUDIO TRANSPARENCY LAYER
```
✅ ResamplingMonitor.kt
   - ExoPlayer AnalyticsListener
   - Rileva cambio formato
   - Callback quando resampling rilevato
   - ResamplingDetectorAudioProcessor (AudioProcessor custom)

✅ USBAudioAnalyzer.kt
   - Query AudioDeviceInfo (API 28+)
   - Enumera dispositivi USB audio
   - Rileva sample rate max, channels
   - Verifica Hi-Res capability (192 kHz)
   - Metodi:
     - getConnectedUSBDevices()
     - supportsSampleRate(hz)
     - getWarningIfUnsupported(hz)
     - getCapabilityString()

✅ AudioLog.kt (Logging System)
   - LogTag enum: PLAYER, FORMAT, AUDIO_INFO, METADATA, USB_AUDIO, BUFFERING, ERROR, RESAMPLING, DECODER, NETWORK, UI
   - LogLevel: OFF, ERRORS, WARNINGS, VERBOSE
   - Metodi convenience:
     - formatDetected(format, bitDepth, sampleRate, channels)
     - resamplingDetected(inputHz, outputHz)
     - metadataLoaded(title, artist, album)
     - usbDeviceDetected(name, maxHz)
     - decoderError(reason, exception)
   - File logging con export capability
   - Buffer in-memory con max lines
```

---

## 🎯 PREFERENCE KEYS DEFINITI

### Audio Info Display
```
show_feedback_banner           = true   (Mostra banner feedback in tempo reale)
show_mini_tech_info            = true   (Mostra "320 kbps • 44.1 kHz • 16-bit")
show_technical_panel           = true   (PRO button mostra panel completo)
feedback_banner_dismiss_time   = 3000ms (Auto-dismiss milliseconds)
```

### Audio Quality Monitoring
```
warn_if_resampled              = true   (Avvisa se risamplato)
show_usb_device_info           = true   (Mostra USB DAC capabilities)
monitor_buffering              = true   (Mostra progresso buffering)
show_bitrate_live              = true   (Mostra bitrate in tempo reale)
```

### ReplayGain & Normalization
```
enable_replaygain              = false  (Disabled di default - audiofili preferiscono)
replaygain_mode                = "none" (Valori: "none", "track", "album", "peak_limiting")
replaygain_preset              = "conservative" (Valori: "conservative", "normal", "aggressive")
```

### Format & Codec
```
preferred_codec                = "auto" (Valori: "auto", "mp3", "flac", "wav", "aac", "opus", "vorbis")
show_format_placeholder        = true   (Mostra placeholder colorato se artwork manca)
enable_gapless                 = true   (ExoPlayer gapless playback)
```

### Logging & Debug
```
log_level                      = "warnings" (Valori: "off", "errors", "warnings", "verbose")
log_to_file                    = false  (Salva log in file - development only)
log_playback_events            = true   (Se log_level >= verbose)
log_metadata_parsing           = true   (Se log_level >= verbose)
log_resampling_events          = true   (Se log_level >= verbose)
```

---

## 📊 DATA FLOW

### Scenario 1: User Plays FLAC 24/96 File
```
1. User taps track in LocalMusicActivity
2. NowPlayingActivity loads
3. PreferenceManager.getBoolean("show_feedback_banner") → true
4. FeedbackBanner.showLoading("Caricamento metadati")
5. SAFScanner reads file from DB
6. ResamplingMonitor attaches to ExoPlayer
7. AudioLog.formatDetected("FLAC", 24, 96000, 2)
8. ExoPlayer onDownstreamFormatChanged triggered
9. USBAudioAnalyzer checks for USB DAC
10. If USB DAC at 192 kHz: FeedbackBanner.showUSBDeviceInfo("Audiolab M-DAC", 192)
11. ResamplingMonitor checks: input (96 kHz) vs output (USB 192 kHz)
12. No conversion needed → AudioLog.noBitPerfect(96000, 96000)
13. FeedbackBanner.showFormatInfo("FLAC", 24, 96) [auto-dismiss 3s]
14. Playback starts
15. User taps PRO button → Technical Panel shows all details + ✅ Bit-perfect indicator
```

### Scenario 2: Unsupported Format or Corrupted File
```
1. User taps track
2. ExoPlayer tries to decode
3. Decoder error caught
4. AudioLog.decoderError("Format non supportato: DSF", exception)
5. FeedbackBanner.showError("Formato non supportato: DSF")
6. NowPlayingActivity catches error, skips to next
7. Log file updated with full stack trace
```

### Scenario 3: Network Buffering (Future NAS)
```
1. User plays file from NAS
2. Buffering starts
3. OnBufferingUpdate event
4. AudioLog.bufferingProgress(45, 2.5)
5. FeedbackBanner.showBuffering(45, "2.5 MB/s") [no auto-dismiss]
6. Buffering completes
7. FeedbackBanner.dismiss()
```

---

## 🔌 INTEGRATION POINTS

### Pronto per integrare in:

#### NowPlayingActivity
```kotlin
class NowPlayingActivity : BaseActivity() {

    private lateinit var feedbackBanner: FeedbackBanner
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var resamplingMonitor: ResamplingMonitor
    private lateinit var usbAnalyzer: USBAudioAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize
        feedbackBanner = findViewById(R.id.feedbackBanner)
        preferenceManager = PreferenceManager.getInstance(applicationContext)
        usbAnalyzer = USBAudioAnalyzer(applicationContext)

        // Check if feedback enabled
        lifecycleScope.launch {
            val showFeedback = preferenceManager.getBoolean("show_feedback_banner")
            if (showFeedback) {
                setupFeedbackBanner()
            }
        }

        // Attach resampling monitor
        resamplingMonitor = ResamplingMonitor { inputHz, outputHz ->
            feedbackBanner.showResamplingWarning(inputHz, outputHz)
            AudioLog.resamplingDetected(inputHz, outputHz)
        }
        controller?.addAnalyticsListener(resamplingMonitor)

        // Check USB devices
        val usbDevices = usbAnalyzer.getConnectedUSBDevices()
        if (usbDevices.isNotEmpty()) {
            val primary = usbDevices.first()
            feedbackBanner.showUSBDeviceInfo(primary.name, primary.maxSampleRate)
        }
    }
}
```

#### PlayerHolder.kt
```kotlin
// Quando decodifica un file:
AudioLog.formatDetected(format, bitDepth, sampleRate, channels)

// Quando trova metadati:
AudioLog.metadataLoaded(title, artist, album)

// Quando errore decoder:
AudioLog.decoderError("MP3 frame sync error", exception)
```

#### MusicPlayerService.kt
```kotlin
// OnDestroy:
AudioLog.playerStarted("Bohemian Rhapsody", trackId = 42)
```

---

## 📂 FILE STRUCTURE

```
app/src/main/java/com/genaro/radiomp3/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt (✅ Updated with PreferenceEntry & PreferenceDao)
│   │   ├── PreferenceEntry.kt (✅ NEW)
│   │   └── dao/
│   │       └── PreferenceDao.kt (✅ NEW)
│   └── prefs/
│       └── PreferenceManager.kt (✅ NEW - Singleton)
├── playback/
│   └── audio/
│       ├── ResamplingMonitor.kt (✅ NEW)
│       ├── ResamplingDetectorAudioProcessor.kt (in ResamplingMonitor.kt)
│       └── USBAudioAnalyzer.kt (✅ NEW)
├── logging/
│   └── AudioLog.kt (✅ NEW)
├── ui/
│   └── widgets/
│       └── FeedbackBanner.kt (✅ NEW)
└── res/
    └── layout/
        └── widget_feedback_banner.xml (✅ NEW)
```

---

## 🚀 PROSSIMI STEP (Per NowPlayingActivity Integration)

1. **Aggiungere FeedbackBanner nel layout** `activity_now_playing.xml`
   ```xml
   <com.genaro.radiomp3.ui.widgets.FeedbackBanner
       android:id="@+id/feedbackBanner"
       android:layout_width="match_parent"
       android:layout_height="wrap_content"
       android:layout_marginTop="8dp"
       android:layout_marginStart="16dp"
       android:layout_marginEnd="16dp"
       android:visibility="gone" />
   ```

2. **Inizializzare in NowPlayingActivity.onCreate()**
   ```kotlin
   feedbackBanner = findViewById(R.id.feedbackBanner)
   preferenceManager = PreferenceManager.getInstance(this)
   usbAnalyzer = USBAudioAnalyzer(this)

   // Setup feedback based on prefs
   lifecycleScope.launch {
       val showFeedback = preferenceManager.getBoolean("show_feedback_banner", true)
       if (!showFeedback) {
           feedbackBanner.visibility = View.GONE
       }
   }
   ```

3. **Attach ResamplingMonitor**
   ```kotlin
   resamplingMonitor = ResamplingMonitor { inputHz, outputHz ->
       feedbackBanner.showResamplingWarning(inputHz, outputHz)
       AudioLog.resamplingDetected(inputHz, outputHz)
   }
   controller?.addAnalyticsListener(resamplingMonitor)
   ```

4. **Integrare AudioLog in PlayerHolder**
   ```kotlin
   // onDownstreamFormatChanged
   AudioLog.formatDetected(codecName, bitDepth, sampleRate, channels)

   // onMetadataChanged
   AudioLog.metadataLoaded(title, artist, album)
   ```

5. **Creare SettingsActivity**
   - Mostra PreferenceEntry items by category
   - User può toggle feedback, logging level, etc.

---

## 🎯 WORD

**Trasparenza totale.**

Se il file non è decodificabile → L'app lo dice.
Se il risampling avviene → L'app lo dice.
Se il DAC USB supporta 192 kHz → L'app lo mostra.
Se il buffering è lento → L'app mostra la velocità.

L'audiofilo sa **esattamente cosa sta succedendo**.

Niente magia. Niente mistero.

---

## ✅ COMPLETATO
- [x] Preference system with Room storage
- [x] Feedback banner widget with multiple types
- [x] Resampling detection (ExoPlayer integration ready)
- [x] USB audio device analysis
- [x] Structured audio logging system
- [x] All preference keys defined
- [x] Documentation complete

## 🔜 PROSSIMO
- [ ] Integrate FeedbackBanner in NowPlayingActivity
- [ ] Create SettingsActivity for preferences UI
- [ ] Connect ResamplingMonitor to ExoPlayer
- [ ] Test with actual FLAC/WAV files
- [ ] Implement ReplayGain calculation (Phase 2)
- [ ] jaudiotagger integration for native tags (Phase 2)

---

**State**: Ready for NowPlayingActivity integration
**Architecture**: Modular, testable, extensible
**Future**: NAS scanner will reuse same feedback/logging system
