# Release Notes

## 2026-05-26

### Fix alarme sur écran verrouillé
- Suppression de `requestDismissKeyguard()` dans `AlarmRingingActivity` — forçait le déverrouillage PIN/empreinte avant de pouvoir interagir avec l'alarme
- Suppression de `FLAG_DISMISS_KEYGUARD` dans l'overlay de `AlarmService`
- L'alarme s'affiche maintenant directement par-dessus le lock screen sans demander de déverrouillage

## 2026-03-13

### Cadran Gear — Reglage epaisseur du texte (font-weight)
- Nouveau slider "Epaisseur" dans les reglages du cadran Organizeur (100 Thin → 900 Black)
- Applique le font-weight a tous les elements texte (heure, date, secondes, batterie)
- Preview en temps reel sur le telephone avant envoi
- Envoye a la montre via SAP, persiste en localStorage
- Fichiers modifies : `gear-clockface/clock.js`, `gear-clockface/settings.json`, `GearClocksScreen.kt`

## 2026-03-03

### Cadran Organizeur — SAP privilege ACE fix
- Le privilege `accessoryprotocol` est maintenant accorde au cadran web custom
- Modifie `/usr/etc/ace/TizenPolicy.xml` sur la montre : ajout du fingerprint de notre certificat dans la section Public API
- Ajout du privilege dans `/opt/dbspace/.privilegelist.db` comme "public"
- **Important** : le daemon ACE cache la politique en memoire — un reboot de la montre est necessaire apres modification

### Cadran Organizeur — Connexion SAP etablie
- La connexion SAP entre le cadran (consumer) et le telephone (provider) s'etablit avec succes
- Le cadran affiche "SAP:ok" apres `requestServiceConnection` + `OnServiceCreateConfirm`
- URL du privilege corrigee : `http://developer.samsung.com/privilege/accessoryprotocol` (sans "tizen/")
- Sans `<tizen:privilege>` : SecurityError. Avec : Error 42 avant fix ACE

### Cadran Organizeur — Envoi de donnees en cours de debug
- Les donnees envoyees depuis le telephone via `sendClockSettings()` n'arrivent pas encore au cadran
- Debug : indicateur `#sap-status` affiche l'etat SAP, le type et prefixe des donnees recues
- Debug : ping `{"ping":true}` envoye automatiquement par le cadran a la connexion
- Prochaine etape : verifier les logs telephone (session SAP ID, frame format)

### Fichiers modifies
- `gear-clockface/config.xml` : v1.3.0, privilege URL corrigee, `<tizen:privilege>` + `<feature>` SAP
- `gear-clockface/clock.js` : SAP consumer complet, debug indicators, ping on connect
- `gear-clockface/style.css` : `#sap-status` debug overlay
- `gear-clockface/index.html` : `<div id="sap-status">`
- `gear-clockface/deploy.sh` : copie de `res/xml/accessoryservices.xml` dans le build
- `gear-clockface/res/xml/accessoryservices.xml` : nouveau, profil SAP consumer
- `GearManager.kt` : `sendClockSettings()`, SC handler pour `/organizeur/clocksettings`, CAPEX provider
- `GearConstants.kt` : `PROFILE_CLOCK_SETTINGS`, `CHANNEL_CLOCK_SETTINGS`
- `GearClocksScreen.kt` : settings inline avec AnimatedVisibility, couleurs + toggles + bouton Appliquer
- `MainScreen.kt` : cablage `onSendClockSettings` vers GearManager

---

## 2026-03-02

### Wallpaper Samsung Gear 1 via Host Manager
- Changement de fond d'ecran fonctionnel sur la montre Samsung Gear 1 (SM-V700)
- Protocole reverse-engineere : `mgr_home_bg_req` via Host Manager (pas file transfer)
- Image envoyee en base64 JPEG dans un message JSON, avec fragmentation HM multi-parties
- Ecran Compose pour choisir une image, la recadrer en carre (center-crop) et l'envoyer en 320x320

### Parser HM multi-fragment
- Reassemblage des messages Host Manager fragmentes (>4KB)
- Format decouvert : `[05 00]` debut, `[09 00]` milieu, `[0f 00]` fin
- Permet de recevoir le wallpaper actuel de la montre (`mgr_home_bg_res`, ~138KB)
- Permet d'envoyer le nouveau wallpaper en ~12 fragments de 4KB

### Initialisation correcte de la montre
- Ajout de `"isfrominitial": true` dans `mgr_sync_init_setting_req` (capture Samsung originale)
- La montre active maintenant tous ses services apres l'initialisation (contacts, calendrier, camera, etc.)
- Handlers ajoutes pour `mgr_setupwizard_eula_finished_res`, `mgr_setting_voice_control_res`, `mgr_sync_init_setting_res`

### Fichiers modifies
- `GearManager.kt` : parser multi-fragment, wallpaper via HM, isfrominitial, handlers post-init
- `GearFileTransfer.kt` : `_state` passe en `internal` pour acces depuis GearManager
- `GearWallpaperScreen.kt` : center-crop au lieu de stretch pour conserver le ratio

---

## 2026-02-13

### Fix alignement 16 Ko des libs natives (Android 15)
- CameraX mis à jour de 1.3.1 → 1.4.1
- `extractNativeLibs="true"` dans AndroidManifest.xml pour forcer l'extraction des libs natives
- `useLegacyPackaging = true` dans build.gradle.kts (cohérent avec le flag manifest)
- Corrige l'avertissement au lancement : "lib/arm64-v8a/libimage_processing_util_jni.so non alignée en 16ko"

### Fichiers modifiés
- `app/build.gradle.kts` (+packaging jniLibs, CameraX 1.4.1)
- `AndroidManifest.xml` (+extractNativeLibs)

---

## 2026-02-09 — v1.5

### Overlay alarme pour MIUI/Xiaomi
- Fenêtre overlay (`SYSTEM_ALERT_WINDOW` + `TYPE_APPLICATION_OVERLAY`) affichée par-dessus l'écran verrouillé quand une alarme sonne
- Contourne le blocage MIUI de `startActivity` et `fullScreenIntent` depuis un service
- WakeLock `ACQUIRE_CAUSES_WAKEUP` pour allumer l'écran automatiquement
- Retour visuel du snooze : message "Rappel dans X min" affiché 1.5s avant fermeture

### Bouton STOP dans l'app
- Bouton rouge visible dans le dashboard et la section alarmes quand une alarme sonne
- Polling de `AlarmService.ringingAlarmName` toutes les 500ms
- Solution de secours si l'overlay ne s'affiche pas

### Sonnerie personnalisée par alarme
- Champ `ringtoneUri` dans `Alarm` (null = défaut système)
- Sélecteur de sonnerie système (`ACTION_RINGTONE_PICKER`) dans le dialog d'édition
- Bouton play/pause pour prévisualiser (MediaPlayer + fallback Ringtone API)
- Permission URI persistante (`takePersistableUriPermission`) pour les sonneries non-système

### Avertissement permission overlay
- Carte d'avertissement dans la section alarmes si `SYSTEM_ALERT_WINDOW` non accordée
- Lien direct vers les paramètres système

### Fichiers modifiés
- `alarm/Alarm.kt` (+ringtoneUri), `alarm/AlarmService.kt` (+overlay, +ringingAlarmName, +sonnerie custom)
- `alarm/AlarmReceiver.kt` (+WakeLock), `alarm/AlarmRingingActivity.kt` (nettoyage)
- `ui/AlarmEditDialog.kt` (+sélecteur sonnerie, +preview play/pause)
- `ui/AlarmSection.kt` (+bouton STOP, +avertissement overlay)
- `ui/MainScreen.kt` (+bouton STOP dashboard)
- `AndroidManifest.xml` (+WAKE_LOCK, +SYSTEM_ALERT_WINDOW)
- `res/values/strings.xml` (+4 strings)
- `app/build.gradle.kts` (v1.5, versionCode 6)

---

### Visibilité des fonctionnalités dans le dashboard
- Toggles dans l'écran Configuration pour masquer/afficher chaque fonctionnalité (Filtrage appels, Mode silencieux, Alarmes, Caméra, Aide)
- Préférences persistées via `SharedPreferences` dans `SettingsManager`
- Dashboard adaptatif : cartes en demi-largeur (Mode silencieux + Alarmes, Configuration + Aide) avec layout vertical (icône au-dessus, texte centré)
- Si une seule carte d'une paire est visible, elle passe en pleine largeur avec layout horizontal classique
- Configuration reste toujours visible

### Fichiers modifiés
- `settings/SettingsManager.kt` (+5 propriétés booléennes de visibilité)
- `ui/MainScreen.kt` (+conditionnels dashboard, +carte Fonctionnalités dans SetupScreen, +MenuCard vertical, +FeatureToggleRow)
- `res/values/strings.xml` (+2 strings : `setup_features`, `setup_features_desc`)

### Réveil indépendant (alarm/)
- Nouvelle fonctionnalité : alarmes qui sonnent même en mode silencieux (bypass DND via `USAGE_ALARM`)
- Si le mode silencieux est actif quand l'alarme sonne, il est automatiquement désactivé d'abord
- Foreground service (`mediaPlayback`) avec MediaPlayer en boucle + vibration
- Activité plein écran sur écran verrouillé (`setShowWhenLocked`, `setTurnScreenOn`) avec boutons Arrêter / Rappel
- Notification `IMPORTANCE_HIGH` + `setBypassDnd(true)` + `setFullScreenIntent()`
- Snooze configurable (5/10/15 min) via re-planification AlarmManager
- Re-planification automatique au boot (`BOOT_COMPLETED`) et au lancement de l'app
- Chaque alarme affiche "Désactive : Nuit" si elle se déclenche pendant une plage silencieuse (logique overnight + plages sans fin + détection d'alarme antérieure)

### Sélecteur d'heure à roulette (WheelPicker)
- Remplace les champs texte par un scroll vertical avec snap (`rememberSnapFlingBehavior`)
- Scroll infini (100 répétitions), valeur centrale en gros/gras, valeurs adjacentes estompées
- Lignes de sélection horizontales autour de la valeur active
- S'applique aux deux dialogues (alarmes et mode silencieux)

### Fichiers créés
- `alarm/Alarm.kt`, `alarm/AlarmRepository.kt`, `alarm/AlarmScheduler.kt`
- `alarm/AlarmReceiver.kt`, `alarm/AlarmService.kt`, `alarm/AlarmRingingActivity.kt`
- `ui/AlarmSection.kt`, `ui/AlarmEditDialog.kt`

### Fichiers modifiés
- `AndroidManifest.xml` (+3 permissions, +receiver, +service, +activity)
- `res/values/strings.xml` (+17 strings françaises)
- `ui/AppNavigation.kt` (+Screen.Alarm)
- `ui/MainScreen.kt` (+MenuCard alarmes, +params alarm)
- `MainActivity.kt` (+AlarmRepository, +AlarmScheduler)
- `ui/SilentModeEditDialog.kt` (TimePickerRow public, remplacé par WheelPicker)

## 2026-02-06 — v1.4

### Caméra réseau (MJPEG streaming)
- Nouvelle fonctionnalité : le téléphone sert de caméra réseau via flux MJPEG sur HTTP
- Serveur HTTP brut (ServerSocket) sur port 8080 : `/` sert une page HTML viewer, `/stream` sert le flux MJPEG
- Foreground service avec `foregroundServiceType="camera"` (requis SDK 34) et notification avec action Arrêter
- CameraX ImageAnalysis (YUV_420_888) → conversion NV21 → compression JPEG → envoi multi-clients
- UI Compose : preview caméra, boutons start/stop, switch avant/arrière, sélecteur résolution (480p/720p/1080p)
- Carte URL avec IP:port en monospace + compteur clients connectés (refresh 2s)
- Page HTML desktop autonome (`camera-viewer/index.html`) avec champ adresse + affichage flux

### Fichiers créés
- `camera/NetworkUtils.kt`, `camera/CameraManager.kt`, `camera/MjpegServer.kt`, `camera/MjpegStreamingService.kt`
- `ui/CameraScreen.kt`
- `camera-viewer/index.html`

### Fichiers modifiés
- `app/build.gradle.kts` (+CameraX deps, +lifecycle-service, version 1.4)
- `AndroidManifest.xml` (+6 permissions, +service declaration)
- `res/values/strings.xml` (+22 strings françaises)
- `ui/AppNavigation.kt`, `ui/MainScreen.kt`, `MainActivity.kt`

## 2026-02-06 — v1.3

### Page Aide
- Nouvelle page Aide accessible depuis le dashboard (4e carte, icone Info)
- Contenu organise en 3 sections avec titres colores : Filtrage des appels, Mode silencieux, Configuration
- Explications detaillees sur les volumes, modes DND et permissions

### Aide contextuelle volumes dans le dialog mode silencieux
- Texte explicatif general sous le label "Volumes"
- Description sous chaque volume (Sonnerie, Notifications, Media)
- Indication visuelle DND : volumes sans effet grises (alpha 0.4) + texte "Sans effet dans ce mode" en rouge selon le mode choisi

### Plein ecran et orientation
- Mode immersif : barres systeme cachees, reapparaissent au swipe
- Orientation portrait forcee (pas de rotation)

### Header dashboard
- Layout refait : image a gauche agrandie (requiredSize pour contourner le padding adaptive icon), titre centre a droite
- Affichage heure et pourcentage batterie au-dessus du titre (mise a jour toutes les 30s)

### Fichiers modifies
- `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`
- `ui/AppNavigation.kt`, `ui/MainScreen.kt`, `ui/SilentModeEditDialog.kt`
- `res/values/strings.xml` (18 nouvelles strings)
