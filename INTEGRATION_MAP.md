# 🎵 GenPlayer - Integration Map (Dove mettere mano)

## 📱 SCHERMATA PLAYER (NowPlayingActivity)

```
┌─────────────────────────────────────────────────────────────────┐
│  ← BACK  [16dp margin top]                           PRO ↓  [x] │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 🔵 ⏳ Buffering... 45% (2.5 MB/s)                       │    │ ← BANNER FEEDBACK
│  └─────────────────────────────────────────────────────────┘    │    (FeedbackBanner.kt)
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                                                           │    │
│  │                    [Album Art]                           │    │ ← Artwork
│  │                    (400x400 max)                         │    │
│  │                                                           │    │
│  │     ◄ Technical Details Overlay (PRO button click) ◄    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
│  Bohemian Rhapsody                                              │
│  Queen                                                           │
│  A Night at the Opera                                           │
│                                                                   │
│  320 kbps • 44.1 kHz • 16-bit ✅ Bit-Perfect                   │ ← Tech info
│                                                                   │
├─────────────────────────────────────────────────────────────────┤
│  0:00  ▓▓▓▓▓▓░░░░░░░░░░░░░  3:45                              │
│                                                                   │
│        [◄◄]  [⏯️]  [►►]  [🔀]  [🔁]                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔍 DOVE VEDI COSA

### 1️⃣ **FEEDBACK BANNER** (Top, sotto back button)

**Dove vedi:** Subito sotto il back button, prima dell'artwork

**Cosa mostra:**
```
🔵 ⏳ Buffering... 45% (2.5 MB/s)           ← INFO (blu, auto-dismiss 3s)
🟡 ⚠️ Risamplato: 44.1 → 48 kHz             ← WARNING (arancio, auto-dismiss 3s)
🟢 ✅ FLAC 24-bit/96 kHz (bit-perfect)      ← SUCCESS (verde, auto-dismiss 3s)
🔴 ❌ File corrotto - impossibile decodificare ← ERROR (rosso, user dismiss)
🔵 🎚️ USB DAC: Audiolab M-DAC (192 kHz)    ← INFO (blu, auto-dismiss 3s)
```

**Come settare:**
```kotlin
// In NowPlayingActivity.kt
private lateinit var feedbackBanner: FeedbackBanner

override fun onCreate(savedInstanceState: Bundle?) {
    // ...
    feedbackBanner = findViewById(R.id.feedbackBanner)  // ← Aggiungere nel layout

    // Mostra un messaggio di test
    feedbackBanner.showFormatInfo("FLAC", 24, 96)
}
```

**File da modificare:**
- ✏️ `activity_now_playing.xml` - Aggiungere il widget
- ✏️ `NowPlayingActivity.kt` - Inizializzare e usare

---

### 2️⃣ **RESAMPLING WARNING** (Se audio convertito)

**Dove vedi:** Nel feedback banner

**Trigger:** Quando ExoPlayer converte sample rate (es. 44.1 → 48 kHz)

**Esempio:**
```
File: 44.1 kHz FLAC
Device: Forzato a 48 kHz dal sistema
↓↓↓
Banner: 🟡 ⚠️ Risamplato: 44.1 → 48 kHz (NON bit-perfect)
```

**Come funziona:**
```kotlin
// In NowPlayingActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    // ...

    // Crea il monitor
    resamplingMonitor = ResamplingMonitor { inputHz, outputHz ->
        feedbackBanner.showResamplingWarning(inputHz, outputHz)
        AudioLog.resamplingDetected(inputHz, outputHz)
    }

    // Attacca a ExoPlayer
    controller?.addAnalyticsListener(resamplingMonitor)
}
```

**File da modificare:**
- ✏️ `NowPlayingActivity.kt` - Aggiungere ResamplingMonitor

---

### 3️⃣ **USB AUDIO INFO** (Se DAC USB connesso)

**Dove vedi:** Nel feedback banner

**Trigger:** Al caricamento della schermata (onCreate) oppure quando DAC si connette

**Esempio:**
```
DAC connesso: Audiolab M-DAC (192 kHz capable)
↓↓↓
Banner: 🔵 🎚️ USB DAC: Audiolab M-DAC (192 kHz)
```

**Come funziona:**
```kotlin
// In NowPlayingActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    // ...

    val usbAnalyzer = USBAudioAnalyzer(this)
    val usbDevices = usbAnalyzer.getConnectedUSBDevices()

    if (usbDevices.isNotEmpty()) {
        val device = usbDevices.first()
        feedbackBanner.showUSBDeviceInfo(device.name, device.maxSampleRate)
    }
}
```

**File da modificare:**
- ✏️ `NowPlayingActivity.kt` - Aggiungere USBAudioAnalyzer check

---

### 4️⃣ **TECHNICAL PANEL** (PRO button, mostra overlay)

**Dove vedi:** Click su PRO badge → mostra panel overlay sull'artwork

**Prima (cosa vedi adesso):**
```
File Information:
  File Size: 45.2 MB
  Compression: Lossless (FLAC)

Audio Format:
  Format: FLAC
  Bitrate: N/A
  VBR/CBR: —
  Sample Rate: 96 kHz
  Bit Depth: 24-bit
  Channels: Stereo

Metadata:
  Duration: 3:45
  ReplayGain: —
  Encoder: —
```

**Dopo (aggiunto):**
```
... (tutto sopra) ...

Device Information:              ← NUOVO SECTION
  USB Audio: Audiolab M-DAC
  Max Sample Rate: 192 kHz
  Channels: 8

Playback Status:                ← NUOVO SECTION
  Resampling: NO ✅ (96 kHz → 96 kHz)
  Bit-Perfect: YES ✅
  Buffering: 100%
```

**Come mettere:**
```kotlin
// In NowPlayingActivity.kt, nel metodo toggleTechnicalDetailsPanel()
// o quando aggiorna il technical panel, aggiungere:

private fun updateTechnicalDetailsPanel(track: Track) {
    // ... (codice esistente) ...

    // AGGIUNGERE:
    // USB Audio Info
    val usbAnalyzer = USBAudioAnalyzer(this)
    val usbDevices = usbAnalyzer.getConnectedUSBDevices()
    if (usbDevices.isNotEmpty()) {
        val device = usbDevices.first()
        txtUSBDevice.text = "USB Audio: ${device.name}"
        txtUSBMaxHz.text = "Max: ${device.maxSampleRate} kHz"
    }

    // Resampling Status
    val resamplingInfo = resamplingMonitor.getResamplingInfo()
    txtResamplingStatus.text = resamplingInfo
}
```

**File da modificare:**
- ✏️ `activity_now_playing.xml` - Aggiungere TextViews per USB + Resampling info
- ✏️ `NowPlayingActivity.kt` - Popolare i nuovi TextViews

---

### 5️⃣ **FORMAT INFO LINE** (Sotto artist)

**Dove vedi:** Righe di testo sotto "Queen" → "A Night at the Opera"

**Prima:**
```
Queen
A Night at the Opera
320 kbps • 44.1 kHz • 16-bit
```

**Dopo (aggiunto icon e info):**
```
Queen
A Night at the Opera
🟢 320 kbps • 44.1 kHz • 16-bit • CBR
```

**O meglio ancora con info resampling:**
```
Queen
A Night at the Opera
🟢 320 kbps • 44.1 kHz • 16-bit • ✅ Bit-Perfect
```

**Come fare:**
```kotlin
// Nel txtTechInfo che esiste già
private fun updateTechInfo(track: Track) {
    val isBitPerfect = resamplingMonitor.isCurrentlyResampling().not()
    val bitPerfectIcon = if (isBitPerfect) "✅" else "⚠️"

    val techLine = "${track.bitrateKbps} kbps • " +
                   "${track.sampleRateHz} kHz • " +
                   "${track.bitDepth}-bit • " +
                   "$bitPerfectIcon"

    txtTechInfo.text = techLine
    txtTechInfo.visibility = View.VISIBLE
}
```

**File da modificare:**
- ✏️ `NowPlayingActivity.kt` - Aggiungere logica nel metodo che mostra tech info

---

## 📂 FILE DA MODIFICARE (In ordine)

### Priority 1: Layout XML
```
✏️ app/src/main/res/layout/activity_now_playing.xml

Dove aggiungere FeedbackBanner:
  DOPO: <ImageButton android:id="@+id/btnBack" ... >
  PRIMA: <ScrollView android:id="@+id/mainContent" ... >

Codice da inserire:
  <com.genaro.radiomp3.ui.widgets.FeedbackBanner
      android:id="@+id/feedbackBanner"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:layout_marginStart="16dp"
      android:layout_marginEnd="16dp"
      android:layout_marginTop="8dp"
      android:visibility="gone" />

Dove aggiungere USB/Resampling info nel technical panel:
  DENTRO: <ScrollView android:id="@+id/technicalDetailsPanel" ... >
  DOPO: <TextView android:id="@+id/txtEncoder" ... >

Codice da inserire:
  <!-- 🎚️ DEVICE INFORMATION -->
  <TextView
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:text="🎚️ Device Information"
      android:textColor="#FFD700"
      android:textSize="16sp"
      android:textStyle="bold"
      android:layout_marginTop="16dp"
      android:layout_marginBottom="8dp" />

  <TextView
      android:id="@+id/txtUSBDevice"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:text="USB Device: —"
      android:textColor="#FFFFFF"
      android:textSize="14sp"
      android:layout_marginBottom="4dp" />

  <TextView
      android:id="@+id/txtUSBMaxHz"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:text="Max Sample Rate: —"
      android:textColor="#FFFFFF"
      android:textSize="14sp"
      android:layout_marginBottom="16dp" />

  <!-- ✅ PLAYBACK STATUS -->
  <TextView
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:text="✅ Playback Status"
      android:textColor="#FFD700"
      android:textSize="16sp"
      android:textStyle="bold"
      android:layout_marginTop="8dp"
      android:layout_marginBottom="8dp" />

  <TextView
      android:id="@+id/txtBitPerfect"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:text="Bit-Perfect: —"
      android:textColor="#FFFFFF"
      android:textSize="14sp"
      android:layout_marginBottom="4dp" />

  <TextView
      android:id="@+id/txtResamplingStatus"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:text="Resampling: —"
      android:textColor="#FFFFFF"
      android:textSize="14sp"
      android:layout_marginBottom="4dp" />

  <TextView
      android:id="@+id/txtBufferingStatus"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:text="Buffering: —"
      android:textColor="#AAAAAA"
      android:textSize="14sp"
      android:layout_marginBottom="16dp" />
```

---

### Priority 2: NowPlayingActivity.kt

```kotlin
// AGGIUNGERE IMPORTS
import com.genaro.radiomp3.ui.widgets.FeedbackBanner
import com.genaro.radiomp3.playback.audio.ResamplingMonitor
import com.genaro.radiomp3.playback.audio.USBAudioAnalyzer
import com.genaro.radiomp3.data.prefs.PreferenceManager
import com.genaro.radiomp3.logging.AudioLog

class NowPlayingActivity : BaseActivity() {

    // AGGIUNGERE FIELD VARIABILI
    private lateinit var feedbackBanner: FeedbackBanner
    private lateinit var resamplingMonitor: ResamplingMonitor
    private lateinit var usbAnalyzer: USBAudioAnalyzer
    private lateinit var preferenceManager: PreferenceManager

    // Per i nuovi TextViews
    private lateinit var txtUSBDevice: TextView
    private lateinit var txtUSBMaxHz: TextView
    private lateinit var txtBitPerfect: TextView
    private lateinit var txtResamplingStatus: TextView
    private lateinit var txtBufferingStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        // AGGIUNGERE - Inizializzare components
        feedbackBanner = findViewById(R.id.feedbackBanner)
        preferenceManager = PreferenceManager.getInstance(this)
        usbAnalyzer = USBAudioAnalyzer(this)

        // Nuovi TextViews
        txtUSBDevice = findViewById(R.id.txtUSBDevice)
        txtUSBMaxHz = findViewById(R.id.txtUSBMaxHz)
        txtBitPerfect = findViewById(R.id.txtBitPerfect)
        txtResamplingStatus = findViewById(R.id.txtResamplingStatus)
        txtBufferingStatus = findViewById(R.id.txtBufferingStatus)

        // AGGIUNGERE - Check se feedback banner abilitato
        lifecycleScope.launch {
            val showFeedback = preferenceManager.getBoolean(
                "show_feedback_banner",
                true
            )
            feedbackBanner.visibility = if (showFeedback) View.VISIBLE else View.GONE
        }

        // AGGIUNGERE - Setup ResamplingMonitor
        resamplingMonitor = ResamplingMonitor { inputHz, outputHz ->
            feedbackBanner.showResamplingWarning(inputHz, outputHz)
            AudioLog.resamplingDetected(inputHz, outputHz)

            // Update technical panel
            updateResamplingStatus(inputHz, outputHz)
        }
        controller?.addAnalyticsListener(resamplingMonitor)

        // AGGIUNGERE - Check USB devices
        checkUSBDevices()

        // ... resto del codice ...
    }

    // AGGIUNGERE - Nuovo metodo
    private fun checkUSBDevices() {
        val devices = usbAnalyzer.getConnectedUSBDevices()
        if (devices.isNotEmpty()) {
            val device = devices.first()
            feedbackBanner.showUSBDeviceInfo(device.name, device.maxSampleRate)
            AudioLog.usbDeviceDetected(device.name, device.maxSampleRate)

            // Update technical panel
            txtUSBDevice.text = "USB Audio: ${device.name}"
            txtUSBMaxHz.text = "Max: ${device.maxSampleRate} kHz"
        }
    }

    // AGGIUNGERE - Nuovo metodo
    private fun updateResamplingStatus(inputHz: Int, outputHz: Int) {
        val isBitPerfect = inputHz == outputHz
        txtBitPerfect.text = "Bit-Perfect: ${if (isBitPerfect) "✅ YES" else "❌ NO"}"
        txtResamplingStatus.text = if (isBitPerfect) {
            "✅ No resampling ($inputHz kHz)"
        } else {
            "⚠️ Resampling: $inputHz → $outputHz kHz"
        }
    }

    // MODIFICARE - Nel metodo updateTechnicalDetailsPanel()
    private fun updateTechnicalDetailsPanel(track: Track) {
        // ... codice esistente ...

        // AGGIUNGERE alla fine:
        updateResamplingStatus(
            track.sampleRateHz ?: 44100,
            track.sampleRateHz ?: 44100  // TODO: get actual device output Hz
        )
        checkUSBDevices()
    }
}
```

---

### Priority 3: PlayerHolder.kt (Log events)

```kotlin
// AGGIUNGERE IMPORT
import com.genaro.radiomp3.logging.AudioLog

class PlayerHolder(...) {

    // TROVARE metodo: onDownstreamFormatChanged()
    // AGGIUNGERE all'inizio o in un punto logico:

    override fun onDownstreamFormatChanged(...) {
        val format = ... // già calcolato
        val bitDepth = ... // già calcolato
        val sampleRate = ... // già calcolato
        val channels = ... // già calcolato

        // AGGIUNGERE:
        AudioLog.formatDetected(format, bitDepth, sampleRate, channels)

        // ... resto del codice ...
    }

    // TROVARE metodo: onMetadataChanged()
    // AGGIUNGERE:

    onMetadataChanged?.invoke(title, artist, artworkUrl)
    AudioLog.metadataLoaded(title ?: "Unknown", artist ?: "Unknown", album ?: "Unknown")
}
```

---

## 🎯 INTEGRATION ORDER

1. **First:** Aggiungere FeedbackBanner nel layout XML
2. **Second:** Aggiungere TextViews per USB/Resampling nel technical panel
3. **Third:** Inizializzare in NowPlayingActivity.onCreate()
4. **Fourth:** Aggiungere ResamplingMonitor hook
5. **Fifth:** Aggiungere USBAudioAnalyzer check
6. **Sixth:** Aggiungere AudioLog calls in PlayerHolder
7. **Test:** Play a FLAC file e guarda il feedback banner

---

## ✅ CHECKLIST FINALE

```
Layout (activity_now_playing.xml):
☐ FeedbackBanner aggiunto dopo back button
☐ txtUSBDevice aggiunto nel technical panel
☐ txtUSBMaxHz aggiunto nel technical panel
☐ txtBitPerfect aggiunto nel technical panel
☐ txtResamplingStatus aggiunto nel technical panel
☐ txtBufferingStatus aggiunto nel technical panel

Activity (NowPlayingActivity.kt):
☐ Imports aggiunti
☐ lateinit var feedbackBanner aggiunto
☐ lateinit var resamplingMonitor aggiunto
☐ lateinit var usbAnalyzer aggiunto
☐ latein it var preferenceManager aggiunto
☐ findViewById per tutti i nuovi TextViews
☐ checkUSBDevices() method aggiunto
☐ updateResamplingStatus() method aggiunto
☐ ResamplingMonitor creato e aggiunto a controller
☐ Preferences check per feedback banner visibility
☐ updateTechnicalDetailsPanel() modificato

PlayerHolder (PlayerHolder.kt):
☐ AudioLog imports aggiunto
☐ AudioLog.formatDetected() call aggiunto
☐ AudioLog.metadataLoaded() call aggiunto
```

---

## 🚀 POI VEDRAI

**Quando avvii l'app:**

1. Scegli un file audio da LocalMusicActivity
2. Opens NowPlayingActivity
3. **Vedi il feedback banner** (se non è stato disabilitato nelle prefs)
4. Vedi il USB audio info (se DAC connesso)
5. Vedi il resampling warning (se audio convertito)
6. Tap PRO button → vedi il nuovo technical panel con Device Info + Playback Status
7. Logs salvati in file (se file logging abilitato)

**Esempio:**
```
User taps: "Bohemian Rhapsody.flac" (24-bit, 96 kHz)
↓
NowPlayingActivity opens
↓
FeedbackBanner shows: "🟢 ✅ FLAC 24-bit/96 kHz (bit-perfect)"
↓
If USB DAC: "🔵 🎚️ USB DAC: M-DAC (192 kHz)"
↓
User taps PRO
↓
Technical panel shows:
  Device Information:
    USB Audio: Audiolab M-DAC
    Max Sample Rate: 192 kHz
  Playback Status:
    Bit-Perfect: ✅ YES
    Resampling: ✅ No resampling (96 kHz → 96 kHz)
```

---

Vuoi che cominci con il passo 1 (modificare il layout)? 👍
