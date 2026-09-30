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

## Deploy Gear Clock Face

```bash
# Watch must be connected USB (charging cradle) with debug enabled
cd gear-clockface
bash deploy.sh
# Builds into build/, signs with OrganizeurProfile, installs via sdb
```

- **TOUJOURS utiliser `deploy.sh`** — ne PAS packager manuellement avec `zip` ou `tizen package` depuis le dossier racine (inclut `.gitignore`, `deploy.sh`, `build/` dans le wgt → signature invalide ou package corrompu)
- `deploy.sh` copie les fichiers dans `build/`, injecte `settings.json` dans `index.html`, signe avec `tizen package -s OrganizeurProfile`, installe via `sdb`
- Tizen SDK : `~/tizen-studio/tools/sdb` (devices, push, shell) et `~/tizen-studio/tools/ide/bin/tizen` (package, install)

## Project Structure

The actual project root is `organizeur-android/` (nested inside the repo root). All gradle commands run from there.

```
organizeur-android/          ← gradle project root (run ./gradlew from here)
└── app/src/main/
    ├── kotlin/com/organizeur/app/
    │   ├── MainActivity.kt          ← entry point, wires managers to Compose UI
    │   ├── callfilter/              ← call screening (CallScreeningService + ContactChecker)
    │   ├── alarm/                    ← alarm clock (Alarm, AlarmRepository, AlarmScheduler, AlarmReceiver, AlarmService, AlarmRingingActivity, SnoozeNotifier)
    │   ├── camera/                   ← MJPEG streaming (MjpegServer, MjpegStreamingService, CameraManager, NetworkUtils)
    │   ├── settings/                ← SharedPreferences for call filter settings
    │   ├── silentmode/              ← scheduled DND/volume control (Manager, Enforcer, AlarmScheduler, Receiver)
    │   ├── timer/                   ← countdown timers + stopwatch (CountdownTimer, Stopwatch, TimerRepository, TimerScheduler, TimerNotifier, TimerController, TimerReceiver)
    │   ├── wearable/               ← wearable device integration
    │   │   ├── model/              ← shared models (BandState, DeviceInfo, DeviceType)
    │   │   ├── miband/             ← Mi Band 4 BLE (Manager, Auth, Constants, ui/, watchface/)
    │   │   └── gear/sap/           ← Samsung Gear 1 RFCOMM+SAP (GearManager, SapProtocol, SapFraming, ui/)
    │   └── ui/                      ← Jetpack Compose UI (MainScreen, CameraScreen, SilentModeSection, SilentModeEditDialog, AlarmSection, AlarmEditDialog, TimerScreen, TimerEditDialog, ScreenInfo, theme)
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
- **Seven features**:
  1. **Call filtering** (`callfilter/`): Android `CallScreeningService` blocks/silences unknown callers
  2. **Scheduled silent mode** (`silentmode/`): `AlarmManager` triggers `BroadcastReceiver` → `SilentModeEnforcer` applies DND + volume changes
  3. **Network camera** (`camera/`): MJPEG streaming via foreground service + raw `ServerSocket`. CameraX `ImageAnalysis` → YUV→NV21→JPEG → HTTP multipart stream
  4. **Alarm clock** (`alarm/`): Alarms that bypass DND via `USAGE_ALARM` AudioAttributes. Auto-deactivates silent mode when ringing. Foreground service with `mediaPlayback` type, overlay window on lock screen (MIUI workaround), snooze support, sonnerie personnalisable par alarme
  5. **Mi Band 4** (`wearable/miband/`): BLE connection via blessed-android, custom AES auth, battery/HR/steps, music controls, watchface editor + upload (DFU protocol)
  6. **Samsung Gear 1** (`wearable/gear/`): Bluetooth Classic RFCOMM + SAP protocol, WSM auth (JNI native libs), time sync
  7. **Timers & stopwatch** (`timer/`): countdown timers scheduled with `setAlarmClock()` (ring via `AlarmService.ACTION_START_TIMER`, no snooze) + stopwatch with laps. All state is derived from absolute timestamps, never ticked. `TimerController` is the single entry point (repository + scheduler + notifications), shared by the Compose UI and `TimerReceiver`

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
- **Heure de fin d'une plage silencieuse** : toujours la calculer à partir d'un début (`endAfter(lastStart)` si encore à venir, sinon `endAfter(nextStart)`), jamais seule. Calculée indépendamment, la fin d'une plage de nuit tombait une semaine trop tard et, au déclenchement du début, la reprogrammation écrasait la fin de la nuit en cours (même request code) : le silence ne s'arrêtait plus qu'au réveil. Vérifier avec `adb shell dumpsys alarm | grep -A2 SILENT_MODE_END | grep origWhen`
- **« Prioritaires uniquement »** : sans `priorityExceptions`, ce qui passe dépend des réglages Android (sur le Pixel : conversations prioritaires, rappels…, avec leur son). Avec, `SilentModeEnforcer` pose sa propre `NotificationManager.Policy` (alarmes + médias toujours autorisés) et restaure celle d'origine en fin de plage (`prev_policy_*`). Ce build Pixel n'expose pas la section Zen dans `dumpsys notification` : tester via `settings get global zen_mode` (1 = prioritaires) et `cmd notification post` → `mIntercept=true` dans `dumpsys notification --noredact`
- **Minuteurs** : request codes AlarmManager dans la plage `200_000 + abs(id.hashCode()) % 90_000` pour ne pas entrer en collision avec ceux des alarmes (100_000..189_999). Le décompte affiché dans la notification utilise le chronomètre natif (`setUsesChronometer` + `setChronometerCountDown` + `setWhen(endAt)`) : aucun service ni tick n'est nécessaire pour le tenir à jour. Les actions d'une notification ont chacune besoin d'un request code distinct (les extras ne comptent pas dans l'égalité des `Intent`, deux minuteurs partageraient sinon le même `PendingIntent`)
- **Vérifier qu'une alarme/un minuteur s'est réellement déclenché** (aucun log applicatif, et MIUI les supprime) : `adb shell dumpsys alarm | grep -A10 "com.organizeur.app +"` → compteur par tag, ex. `*walarm*:com.organizeur.app.TIMER_FIRE` avec `1 wakes 1 alarms, last -1m37s`. `dumpsys notification | grep -A40 "pkg=com.organizeur.app"` donne `when=`, `android.showChronometer`, `android.chronometerCountDown` ; l'état persisté se lit via `adb shell run-as com.organizeur.app cat /data/data/com.organizeur.app/shared_prefs/timer_prefs.xml`
- **Rappel d'alarme (snooze)** : request codes de notification en `300_000 + abs(id.hashCode() * 10 + slot) % 90_000` (alarmes 100_000..189_999, minuteurs 200_000..289_999). L'alarme du rappel réutilise le sentinel `day = 0` de `AlarmScheduler.createPendingIntent`. Toute annulation passe par `cancelSnooze(alarmId)` (alarme AlarmManager + état persisté + notification) — ne jamais annuler seulement l'un des trois. `DEFAULT_SNOOZE_MINUTES` (ui/AlarmSection.kt) doit rester aligné sur le défaut de `Alarm.snoozeDurationMinutes`
- **`POST_NOTIFICATIONS`** est requis depuis Android 13 pour que les notifications s'affichent (y compris celles d'un foreground service) — demandée à l'ouverture des écrans Chronomètre et Alarmes
- **Thème** : la palette Organizeur (`ui/theme/Theme.kt`) est le défaut ; les couleurs dynamiques (Material You) sont opt-in via Configuration → Apparence (`SettingsManager.useDynamicColors`). Ne pas remettre `dynamicColorScheme` inconditionnel : avec minSdk 31 il s'applique toujours et rend la palette du projet inutilisée (l'app prend alors les couleurs du fond d'écran)
- **`@android:style/Theme.Material.DayNight.*` n'existe pas** dans le framework (erreur AAPT « resource not found »). Pour un thème système clair/sombre : `@android:style/Theme.DeviceDefault.DayNight` + `windowActionBar=false` / `windowNoTitle=true` (voir `Theme.Organizeur` dans `res/values/themes.xml`). Ce thème sert de fenêtre de démarrage : son `windowBackground` doit suivre le mode (`values/colors.xml` + `values-night/colors.xml`), sinon flash blanc avant le premier rendu Compose
- **Les barres système sont masquées** (`MainActivity.onCreate`, `insetsController.hide(systemBars())`) : l'app affiche donc elle-même heure et batterie dans le bandeau, et le contenu colle au bord haut. En tenir compte avant d'ajouter du padding ou de raisonner sur les insets
- **Valider un changement visuel** : `adb shell screencap -p /sdcard/x.png` + `adb pull`, puis naviguer avec `adb shell input tap X Y` (écran 1080x2424, l'app est verrouillée en portrait). `adb shell input keyevent KEYCODE_BACK` quitte l'app depuis un sous-écran (la flèche de la TopAppBar, elle, revient au tableau de bord)
- **AlarmService.ringingAlarmName** : variable statique `companion object` pour communiquer l'état "en train de sonner" du service vers l'UI Compose. L'UI poll toutes les 500ms via `LaunchedEffect` + `delay`. Pas de LiveData/Flow pour rester cohérent avec l'archi sans ViewModel

## Release Process

Each build is versioned and archived in `releases/`:

1. **Incrémenter la version** dans `organizeur-android/app/build.gradle.kts` : `versionCode` +1, `versionName` +0.1
2. **Build** : `./gradlew assembleDebug`
3. **Créer le dossier** : `releases/vX.Y/`
4. **Copier l'APK** : `cp organizeur-android/app/build/outputs/apk/debug/organizeur-vX.Y.apk releases/vX.Y/` (l'APK n'est PAS nommé `app-debug.apk`)
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
