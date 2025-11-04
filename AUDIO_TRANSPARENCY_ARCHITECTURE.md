# 🎵 GenPlayer - Audio Transparency Architecture

## Obiettivo
Creare un'app che sia **onesta** con l'utente audiofilo. Se qualcosa non è perfetto, l'app lo dice. Se il sistema sta compromettendo la qualità audio, avvisa.

---

## 1. PREFERENZE UTENTE (Settings)

### Sezione: Display Audio Info
- [ ] **Mostra banner feedback** (ON/OFF) - Default: ON
  - Mostra "Buffering...", "Risamplato", "Errore", ecc. in tempo reale

- [ ] **Mostra dettagli tecnici mini** (ON/OFF) - Default: ON
  - Nel player principale: "320 kbps • 44.1 kHz • 16-bit"

- [ ] **Mostra completo technical panel** (PRO button) - Default: ON
  - File size, compression, encoder, ReplayGain, etc.

### Sezione: Audio Quality Monitoring
- [ ] **Avvisa se risamplato** (ON/OFF) - Default: ON
  - Se ExoPlayer converte sample rate, mostra warning chiaro
  - Esempio: "⚠️ Non bit-perfect: risamplato da 44.1→48 kHz"

- [ ] **Mostra USB device info** (ON/OFF) - Default: ON
  - Se dispositivo USB audio connesso, mostra: "USB DAC detected: supports 192 kHz"

- [ ] **Monitora buffering** (ON/OFF) - Default: ON
  - Mostra velocità download, percentuale buffer, retry count

### Sezione: ReplayGain & Normalization
- [ ] **Abilita ReplayGain** (ON/OFF) - Default: OFF (audiofili preferiscono disabilitato)
  - Se ON: applica gain calcolato nel DB

- [ ] **Metodo normalization** (dropdown)
  - None (no gain)
  - ReplayGain Track
  - ReplayGain Album
  - Peak Limiting

### Sezione: Format & Codec
- [ ] **Codec preferito** (dropdown) - Default: "Auto"
  - Auto, MP3, FLAC, WAV, AAC, Opus, Vorbis
  - Se disabled per device, mostra: "⚠️ Codec non supportato dal dispositivo"

- [ ] **Mostra formato placeholder** (ON/OFF) - Default: ON
  - Se copertina non trovata, mostra placeholder colorato per formato
  - MP3 = Arancio, FLAC = Blu, WAV = Verde, etc.

### Sezione: Logging & Debug
- [ ] **Log level** (dropdown)
  - Off
  - Errors Only
  - Warnings + Errors (default)
  - Verbose (tutta la storia del file)

- [ ] **Esporta log** (button)
  - Salva `.txt` con tutte le operazioni

---

## 2. FEEDBACK BANNER SYSTEM

Mostra in **real-time** cosa sta succedendo.

### Posizione
- **Top of player** (sotto back button, sopra artwork)
- **Non intrusive**: 40dp height, auto-dismiss dopo 3s (configurabile)
- **Colori per tipo**:
  - 🟢 **Info** (verde): "Riproducendo", "Caricato"
  - 🟡 **Warning** (giallo): "Risamplato", "Buffering", "File non perfetto"
  - 🔴 **Error** (rosso): "Errore decodifica", "File corrotto", "Timeout rete"

### Messaggi Specifici

#### Buffering / Loading
```
"⏳ Buffering... 45% (1.2 MB/s)"  [animated progress]
"⏳ Caricamento metadati..."
"⏳ Ricerca artwork..."
```

#### Format / Decoding
```
"✅ Riproducendo FLAC 24-bit (bit-perfect)"
"⚠️  Risamplato: 96 → 48 kHz (non bit-perfect)"
"❌ Formato non supportato: DSD"
"❌ File corrotto: impossibile decodificare"
```

#### Metadata / Artwork
```
"📷 Artwork embedded trovato"
"📷 Artwork online trovato (Deezer)"
"📷 Artwork non trovato - mostrando placeholder"
```

#### USB Audio
```
"🎚️ USB DAC rilevato: 192 kHz capable"
"⚠️ USB DAC: limita a 48 kHz (check impostazioni)"
```

#### Network (future NAS)
```
"🌐 NAS connesso (192.168.1.100)"
"⚠️ NAS offline - ultimo aggiornamento: 2 ore fa"
"⏳ Buffering NAS: 12% (500 KB/s)"
```

---

## 3. VISUAL SIGNALS (Segnali Visivi)

### Panel Tecnico (PRO Button) - Esteso

#### Icon System
```
✅ = OK / Perfetto
⚠️  = Attenzione / Non ottimale
❌ = Errore / Non disponibile
🔄 = Processing
📡 = Network
🎚️ = Audio device
📷 = Artwork
```

#### Color Coding per Format
```
#FF8C00 = MP3      (Lossy)
#0099CC = FLAC     (Lossless)
#00AA44 = WAV      (Uncompressed)
#AA44FF = AAC      (Lossy)
#FF4444 = Error/Unsupported
```

#### Sample Rate Indicator
```
44.1 kHz = Standard CD
48 kHz   = Video standard
96 kHz   = Hi-Res
192 kHz  = Ultra Hi-Res (✅ if bit-perfect)
```

#### Resampling Warning
```
Input:  44.1 kHz  (file)
Output: 48 kHz    (device)
        ↓ ↓ ↓ (risampling icon)
Status: ⚠️ NON bit-perfect
Reason: Device forcing 48 kHz
```

### Queue / Playlist Indicators
```
[📁 Folder] = Local file
[🌐 NAS]   = NAS file (future)
[🎙️ Radio] = Streaming
[✗]         = Error / Can't decode
[⏳]        = Loading
```

---

## 4. LOGGING SYSTEM

**Log file structure**: `/data/data/com.genaro.radiomp3/logs/`

### Log Tags
```
[PLAYER] = ExoPlayer state
[FORMAT] = Format detection / codec
[AUDIO_INFO] = Sample rate, bitrate, channels
[METADATA] = Title, artist, artwork
[USB_AUDIO] = Device capability
[BUFFERING] = Download speed, buffer %
[ERROR] = Errors
[RESAMPLING] = Up/down sampling events
```

### Example Log Sequence
```
[PLAYER] 14:23:45.123 - Starting playback: track_id=42
[FORMAT] 14:23:45.456 - Detected: FLAC, 24-bit, 96 kHz, Stereo
[AUDIO_INFO] 14:23:46.100 - Bitrate: N/A (lossless), Channels: 2
[USB_AUDIO] 14:23:46.500 - Device: "Audiolab M-DAC" - Max 192 kHz
[METADATA] 14:23:47.200 - Title: "Bohemian Rhapsody" | Artist: "Queen"
[METADATA] 14:23:48.100 - Artwork: embedded found (300x300)
[RESAMPLING] 14:23:50.000 - NO resampling needed (96 kHz match)
[PLAYER] 14:24:00.000 - Playback started successfully
```

---

## 5. DATA MODEL - Preferences Table

```kotlin
@Entity
data class PreferenceEntry(
    val key: String,           // e.g., "show_feedback_banner"
    val value: String,         // "true" o "false"
    val category: String,      // "audio_info", "quality", "replaygain", etc.
    val type: String,          // "boolean", "enum", "int", "string"
    val defaultValue: String,
    val lastModified: Long
)
```

---

## 6. UI FLOWS

### Flow 1: User Opens Player
```
1. Load track metadata from DB
2. Check format support (format visible immediately)
3. Start playback
4. Monitor resampling in real-time
5. Show feedback banner if events occur
6. User can tap PRO button → full technical panel
```

### Flow 2: User Taps PRO Button
```
1. Show overlay panel with ALL technical details
2. Sections:
   - File Info (size, compression, encoder)
   - Audio Format (bitrate, sample rate, bit depth, channels)
   - Metadata (duration, ReplayGain, detected ID3 tags)
   - Device Info (USB audio capabilites, output format)
   - Resampling Status (✅ bit-perfect OR ⚠️ resampled)
3. Auto-dismiss on back / swipe down
```

### Flow 3: Error During Playback
```
1. Playback attempted
2. Decoder error caught
3. Feedback banner shows: "❌ File corrotto - impossibile decodificare"
4. Log entry created with full error stack
5. Skip to next track (optional: user preference)
```

---

## 7. IMPLEMENTATION PRIORITY

### Phase 1 (Sprint 1-2)
- [x] Preferences table in Room
- [x] Preferences UI (Settings Activity)
- [x] Feedback Banner layout + logic
- [x] Resampling detection (ExoPlayer event listener)
- [x] Basic logging system

### Phase 2 (Sprint 3)
- [ ] jaudiotagger integration for native tags
- [ ] ReplayGain calculation + storage
- [ ] USB audio device analyzer
- [ ] Enhanced technical panel

### Phase 3 (Future - NAS)
- [ ] Network buffering monitor
- [ ] NAS connectivity checks
- [ ] SMB error handling

---

## 8. CODE STRUCTURE

```
com.genaro.radiomp3/
├── data/
│   ├── local/
│   │   ├── PreferenceEntry.kt
│   │   └── dao/PreferenceDao.kt
│   └── prefs/
│       └── PreferenceManager.kt  [Singleton - getPreference, setPreference]
├── playback/
│   ├── AudioTransparency.kt      [Handles all feedback messages]
│   ├── ResamplingMonitor.kt       [ExoPlayer event listener]
│   ├── USBAudioAnalyzer.kt        [AudioDeviceInfo queries]
│   └── LogManager.kt              [File logging]
├── ui/
│   ├── NowPlayingActivity.kt      [Updated with banner + signals]
│   ├── SettingsActivity.kt        [Preferences UI]
│   └── widgets/
│       ├── FeedbackBanner.kt      [Banner view]
│       └── TechnicalDetailsPanel.kt [PRO panel - enhanced]
└── logging/
    └── AudioLog.kt                [Structured logging]
```

---

## 9. EXAMPLE SCENARIO

**User plays FLAC 24/96 on phone with USB DAC connected**

```
Timeline:
├─ 0ms:   USER taps track
├─ 10ms:  [PLAYER] Starting playback
├─ 50ms:  [FORMAT] FLAC detected, 24-bit, 96 kHz
├─ 100ms: [USB_AUDIO] DAC detected, 192 kHz capable
├─ 150ms: [RESAMPLING] No resampling needed - 96 kHz → 96 kHz ✅
├─ 200ms: [METADATA] Title + embedded artwork loaded
├─ 250ms: [PLAYER] Playback started
│
└─ Banner: "✅ FLAC 24-bit/96 kHz (bit-perfect)" [auto-dismiss after 3s]

User taps PRO:
├─ Shows panel:
│  ├─ 📁 File: 45.2 MB, Lossless FLAC
│  ├─ 🎵 Format: 96 kHz, 24-bit, Stereo, FLAC (reference quality)
│  ├─ 🎚️ Device: USB DAC "Audiolab M-DAC", 192 kHz capable
│  ├─ ✅ Output: NO resampling (bit-perfect output)
│  └─ 📷 Artwork: Embedded found
```

---

## 10. Domande per Te

1. **Quando mostrare il feedback banner?**
   - Solo errori?
   - Sempre (info + warnings)?
   - Solo se "Mostra feedback" = ON?

2. **Auto-dismiss speed?**
   - 3 secondi (default)?
   - Configurabile nelle prefs?
   - "Tap to dismiss"?

3. **Per il NAS future - buffering notifications?**
   - Mostrare velocità download?
   - Conteggio retry falliti?
   - ETA?

4. **Log export - dove salvare?**
   - Shared folder?
   - Email?
   - Cloud sync?

---

**Word**: Trasparenza totale. Se qualcosa non è bit-perfect, lo dice. Se c'è un errore, lo spiega. Niente magia.
