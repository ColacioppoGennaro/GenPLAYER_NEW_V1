# GenPlayer V1 - Setup Guida

## Android Studio Setup

### Requisiti
- **Android Studio** 2024.1 o superiore
- **JDK 17+** (Android Studio lo scarica automaticamente)
- **Android SDK** minimo API 24 (Android 7.0)

### Passi Iniziali
1. Apri il progetto in Android Studio
2. Attendi che Gradle scarichi tutte le dipendenze (~5-10 minuti)
3. File → Project Structure → verifica che Android SDK è configurato
4. Build → Clean Project
5. Build → Rebuild Project

### Emulatore
- Crea un emulatore Android con API 33+ per le migliori prestazioni
- Device Manager → Create Device → Pixel 4a, API 33+

---

## VSCode Setup (Opzionale)

Se usi VSCode per editare file Kotlin/XML:

### Estensioni Consigliate
- **Kotlin Language** (JetBrains)
- **Android Tools** (Google)
- **XML** (RedHat)

### Impostazioni Consigliabili
File → Preferences → Settings → Cerca:
```
"editor.fontSize": 12
"editor.formatOnSave": true
"[kotlin]": { "editor.defaultFormatter": "fwcd.kotlin" }
```

---

## Configurazione Emoji

⚠️ **Problema Noto**: Le emoji nel codice possono apparire giganti in VSCode/Android Studio

### Soluzione
Reduci la dimensione del font degli emoji:

**VSCode (settings.json):**
```json
"editor.fontFamily": "'Courier New', monospace",
"editor.fontSize": 14,
"editor.fontLigatures": false
```

**Android Studio (File → Settings → Editor → Font):**
- Font: Consolas o JetBrains Mono
- Size: 12-14
- Line spacing: 1.2

---

## Build & Run

### Debug
```bash
./gradlew clean build
# O da Android Studio: Build → Build & Run
```

### Release
```bash
./gradlew assembleRelease
```

### Pulire Cache
```bash
./gradlew clean
rm -rf .gradle build
```

---

## Struttura Progetto

```
GenPlayerV1/
├── app/
│   ├── src/main/java/com/genaro/radiomp3/   # Sorgente Kotlin
│   ├── src/main/res/                        # Risorse (layout, drawable, etc.)
│   ├── build.gradle.kts                     # Configurazione modulo
├── gradle/
├── build.gradle.kts                         # Configurazione root
├── settings.gradle.kts                      # Configurazione progetto
└── gradlew / gradlew.bat                    # Gradle Wrapper
```

---

## Troubleshooting

### Progetto non compila
1. Verifica JDK 17+: `java -version`
2. Pulisci cache: `./gradlew clean`
3. Sync Gradle: File → Sync Now

### Android Studio lento
1. Disabilita inspections inutili
2. Aumenta memoria: Help → Edit Custom VM Options
   ```
   -Xmx4096m
   -XX:+UseG1GC
   ```

### Errore "No SDK"
- File → Project Structure → SDK Location
- Verifica che Android SDK è scaricato

---

## Git

Pushare cambiamenti:
```bash
git add .
git commit -m "Description"
git push origin main
```

---

## Note di Sviluppo

- **Database**: Room ORM (app/src/main/java/com/genaro/radiomp3/data/local/)
- **Network**: Retrofit + OkHttp (app/src/main/java/com/genaro/radiomp3/api/)
- **Playback**: ExoPlayer (app/src/main/java/com/genaro/radiomp3/playback/)
- **UI**: Material Design 3 + AndroidX

---

Buono sviluppo! 🚀
