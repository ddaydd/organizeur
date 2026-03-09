# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Environment

The project runs on Ubuntu natively. Java 21 (system OpenJDK) and Android SDK:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"

# Build debug APK
cd organizeur-android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/organizeur-vX.Y.apk
```

## Install on Device

```bash
# Phone must be connected USB with debug enabled
~/Android/Sdk/platform-tools/adb install -r organizeur-android/app/build/outputs/apk/debug/organizeur-vX.Y.apk
```

## Project Structure

The actual project root is `organizeur-android/` (nested inside the repo root). All gradle commands run from there.

```
organizeur-android/          ← gradle project root (run ./gradlew from here)
└── app/src/main/
    ├── kotlin/com/organizeur/app/
    │   ├── MainActivity.kt          ← entry point, wires managers to Compose UI
    │   ├── callfilter/              ← call screening (CallScreeningService + ContactChecker)
    │   ├── alarm/                    ← alarm clock (Alarm, AlarmRepository, AlarmScheduler, AlarmReceiver, AlarmService, AlarmRingingActivity)
    │   ├── camera/                   ← MJPEG streaming (MjpegServer, MjpegStreamingService, CameraManager, NetworkUtils)
    │   ├── settings/                ← SharedPreferences for call filter settings
    │   ├── silentmode/              ← scheduled DND/volume control (Manager, Enforcer, AlarmScheduler, Receiver)
    │   ├── wearable/               ← wearable device integration
    │   │   ├── model/              ← shared models (BandState, DeviceInfo, DeviceType)
    │   │   ├── miband/             ← Mi Band 4 BLE (Manager, Auth, Constants, ui/, watchface/)
    │   │   └── gear/sap/           ← Samsung Gear 1 RFCOMM+SAP (GearManager, SapProtocol, SapFraming, ui/)
    │   └── ui/                      ← Jetpack Compose UI (MainScreen, CameraScreen, SilentModeSection, SilentModeEditDialog, AlarmSection, AlarmEditDialog, theme)
    ├── java/com/sec/android/WSM/   ← Samsung WSM JNI wrappers (DO NOT change package — JNI lookup)
    ├── jniLibs/{arm64-v8a,armeabi-v7a}/ ← native .so (libwsm2, libssl, libcrypto)
    ├── res/values/strings.xml       ← all strings in French
    └── AndroidManifest.xml
```

## Architecture

- **Single Activity** pattern: `MainActivity` → Compose `MainScreen`
- **No ViewModel/Room/DI**: managers are plain classes instantiated in `onCreate()`
- **Persistence**: `SharedPreferences` with JSON (`org.json`) for complex data (silent mode schedules)
- **Minimal external dependencies**: AndroidX Compose/Material3, CameraX, blessed-android-coroutines (BLE)
- **Six features**:
  1. **Call filtering** (`callfilter/`): Android `CallScreeningService` blocks/silences unknown callers
  2. **Scheduled silent mode** (`silentmode/`): `AlarmManager` triggers `BroadcastReceiver` → `SilentModeEnforcer` applies DND + volume changes
  3. **Network camera** (`camera/`): MJPEG streaming via foreground service + raw `ServerSocket`. CameraX `ImageAnalysis` → YUV→NV21→JPEG → HTTP multipart stream
  4. **Alarm clock** (`alarm/`): Alarms that bypass DND via `USAGE_ALARM` AudioAttributes. Auto-deactivates silent mode when ringing. Foreground service with `mediaPlayback` type, overlay window on lock screen (MIUI workaround), snooze support, sonnerie personnalisable par alarme
  5. **Mi Band 4** (`wearable/miband/`): BLE connection via blessed-android, custom AES auth, battery/HR/steps, music controls, watchface editor + upload (DFU protocol)
  6. **Samsung Gear 1** (`wearable/gear/`): Bluetooth Classic RFCOMM + SAP protocol, WSM auth (JNI native libs), time sync

## Key Technical Details

- **Min SDK 31** (Android 12), **Target SDK 35** (Android 15)
- Kotlin 2.0.21, Compose BOM 2024.12.01, Gradle 8.9, AGP 8.7.3
- Version catalog: `gradle/libs.versions.toml`
- `FilterChip` requires `@OptIn(ExperimentalMaterial3Api::class)` in this Compose version
- `FlowRow` requires `@OptIn(ExperimentalLayoutApi::class)`
- `rememberSnapFlingBehavior` requires `@OptIn(ExperimentalFoundationApi::class)` — utilisé dans le WheelPicker (sélecteur d'heure à roulette)
- Silent mode alarms use `AlarmManager.setExactAndAllowWhileIdle()` with `SCHEDULE_EXACT_ALARM` permission
- DND control uses `NotificationManager.setInterruptionFilter()` with `ACCESS_NOTIFICATION_POLICY` (not a runtime permission — user must grant manually in system settings)
- All UI strings are in French

## Pièges connus

- L'APK de sortie est nommé `organizeur-vX.Y.apk` (pas `app-debug.apk`) — voir `applicationVariants` dans build.gradle.kts
- `ic_launcher_foreground` (adaptive icon) a ~25% de padding transparent intégré. Pour l'afficher sans marge, utiliser `requiredSize` (pas `size`) dans un `Box` avec `clip` pour forcer le débordement et le recadrage
- `Modifier.size()` respecte les contraintes du parent, `Modifier.requiredSize()` les ignore — important quand on veut qu'un enfant dépasse son conteneur
- **CameraX YUV_420_888 → NV21** : ne PAS faire de bulk copy (`vBuffer.get(nv21, pos, width)`) sur les plans UV — le dernier row a `width-1` octets (pas `width`), ce qui cause un `BufferUnderflowException` silencieux. Toujours utiliser l'accès absolu pixel par pixel : `vBuffer.get(row * rowStride + col * pixelStride)`
- Le `PreviewView` de CameraX doit être connecté au service via `LaunchedEffect` qui observe le binder — pas dans le factory de `AndroidView` (le binder est encore null à ce moment)
- **AlarmScheduler request codes** : `100_000 + abs(hashCode * 10 + day) % 90_000` pour éviter collision avec les request codes du mode silencieux (`scheduleId.hashCode() * 10 + day`)
- **"Désactive" scheduling logic** : pour les plages silencieuses overnight ou sans fin, vérifier le jour précédent (`prevDay`). Pour éviter les faux positifs, comparer l'offset (minutes depuis le début de la plage) de chaque alarme pour ne montrer "Désactive" que sur la première alarme à se déclencher dans la fenêtre
- **MIUI/Xiaomi bloque `startActivity` depuis un service/receiver** même avec `setAlarmClock()` + alarm clock exemption. `fullScreenIntent` sur les notifications ne s'affiche pas non plus. Seule solution fiable : overlay `SYSTEM_ALERT_WINDOW` avec `TYPE_APPLICATION_OVERLAY` + flags lock screen. Aussi, MIUI supprime les messages logcat (Log.d/w) — utiliser un fichier debug si besoin (`adb shell run-as com.organizeur.app cat files/debug.log`)
- **AlarmService.ringingAlarmName** : variable statique `companion object` pour communiquer l'état "en train de sonner" du service vers l'UI Compose. L'UI poll toutes les 500ms via `LaunchedEffect` + `delay`. Pas de LiveData/Flow pour rester cohérent avec l'archi sans ViewModel

## Release Process

Each build is versioned and archived in `releases/`:

1. **Incrémenter la version** dans `organizeur-android/app/build.gradle.kts` : `versionCode` +1, `versionName` +0.1
2. **Build** : `./gradlew assembleDebug`
3. **Créer le dossier** : `releases/vX.Y/`
4. **Copier l'APK** : `cp app/build/outputs/apk/debug/app-debug.apk ../../releases/vX.Y/`
5. **Écrire** `releases/vX.Y/RELEASE_NOTES.md` avec la date, le versionCode, et les changements
6. **Installer** sur le téléphone via adb (voir section "Install on Device")

Structure des releases :
```
releases/
├── v1.1/
│   ├── app-debug.apk
│   └── RELEASE_NOTES.md
├── v1.2/
│   ├── app-debug.apk
│   └── RELEASE_NOTES.md
└── ...
```
