# Organizeur

Application Android tout-en-un pour organiser son quotidien : filtrage d'appels, mode silencieux programmable, réveil intelligent, caméra réseau, et contrôle de montres connectées (Mi Band 4, Samsung Gear 1).

## Fonctionnalités

### Filtrage des appels

Bloque ou met en silencieux les appels provenant de numéros inconnus (absents des contacts). Utilise le `CallScreeningService` d'Android pour intercepter les appels avant qu'ils ne sonnent.

- **Mode Silencieux** : l'appel sonne sans son
- **Mode Rejeter** : l'appel est directement refusé

### Mode silencieux programmable

Planification automatique du mode Ne pas déranger avec contrôle fin des volumes.

- Plages horaires par jour de la semaine (support overnight)
- Heure de fin optionnelle (sinon désactivation manuelle ou par alarme)
- 3 modes DND : silence total, alarmes uniquement, prioritaires uniquement
- Contrôle individuel des volumes (sonnerie, notifications, média)
- Déclenchement précis via `AlarmManager.setExactAndAllowWhileIdle()`

### Alarmes / Réveil

Alarmes qui sonnent même en mode silencieux, avec désactivation automatique du DND.

- Bypass DND via `AudioAttributes.USAGE_ALARM`
- Sonnerie personnalisable par alarme (sélecteur système + preview)
- Snooze configurable (5/10/15 min)
- Overlay sur écran verrouillé (contourne les restrictions MIUI/Xiaomi)
- Re-planification automatique au boot
- Indication "Désactive le mode silencieux" quand une alarme tombe pendant une plage

### Caméra réseau (MJPEG)

Le téléphone sert de caméra de surveillance accessible via navigateur web.

- Flux MJPEG sur HTTP (port configurable)
- Compatible OBS, VLC, et tout client MJPEG
- Switch caméra avant/arrière
- Résolution sélectionnable (480p / 720p / 1080p)
- Foreground service avec notification
- Page viewer web incluse (`camera-viewer/index.html`)

### Mi Band 4

Connexion BLE au bracelet Xiaomi Mi Band 4 via la librairie [blessed-android](https://github.com/weliem/blessed-android-coroutines).

- Authentification AES custom
- Lecture batterie, fréquence cardiaque, pas
- Contrôle musique (play/pause/suivant/précédent)
- Éditeur de cadran (watchface) + upload via protocole DFU

### Samsung Gear 1 (SM-V700)

Connexion Bluetooth Classic RFCOMM avec protocole SAP (Samsung Accessory Protocol) reverse-engineeré.

- Authentification WSM via JNI (libs natives Samsung)
- Synchronisation de l'heure
- Changement de fond d'écran (envoi JPEG base64 via Host Manager)
- **Cadran custom** (`gear-clockface/`) : horloge web Tizen 2.2 personnalisable depuis le téléphone
  - Couleurs (heure, date, secondes, batterie, fond)
  - Image de fond personnalisée
  - Positionnement des éléments par glisser-déposer
  - Envoi des paramètres en temps réel via canal SAP dédié
  - Color picker HSV complet

### Configuration

- Toggles pour masquer/afficher chaque fonctionnalité dans le dashboard
- Dashboard adaptatif (cartes demi/pleine largeur)
- Mode immersif plein écran, orientation portrait forcée

## Structure du projet

```
organizeur-android/          ← Application Android (Kotlin + Jetpack Compose)
├── app/src/main/
│   ├── kotlin/com/organizeur/app/
│   │   ├── callfilter/      ← Filtrage d'appels
│   │   ├── silentmode/      ← Mode silencieux programmable
│   │   ├── alarm/           ← Réveil
│   │   ├── camera/          ← Caméra réseau MJPEG
│   │   ├── wearable/
│   │   │   ├── miband/      ← Mi Band 4 (BLE)
│   │   │   └── gear/        ← Samsung Gear 1 (SAP/RFCOMM)
│   │   ├── settings/        ← SharedPreferences
│   │   └── ui/              ← Compose UI
│   ├── java/com/sec/android/WSM/  ← JNI Samsung (ne pas modifier)
│   ├── jniLibs/             ← Libs natives (libwsm2, libssl, libcrypto)
│   └── res/
gear-clockface/              ← Cadran Tizen 2.2 pour Samsung Gear 1
camera-viewer/               ← Page web viewer MJPEG
organizeur-desktop/          ← Companion desktop (Meteor)
```

## Tech stack

- **Kotlin 2.0** + **Jetpack Compose** (Material 3)
- Android SDK 31–35
- CameraX 1.4 (streaming MJPEG)
- [blessed-android-coroutines](https://github.com/weliem/blessed-android-coroutines) (BLE Mi Band)
- Bluetooth Classic RFCOMM + SAP (Samsung Gear)
- Pas de ViewModel, Room, ni injection de dépendances — architecture simple avec managers instanciés dans `MainActivity`

## Build

```bash
cd organizeur-android
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/organizeur-v1.6.apk
```

## Installation

```bash
adb install -r organizeur-android/app/build/outputs/apk/debug/organizeur-v1.6.apk
```

## Licence

Projet personnel — tous droits réservés.
