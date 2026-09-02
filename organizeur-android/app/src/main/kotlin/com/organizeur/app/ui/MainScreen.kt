package com.organizeur.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Watch
import com.organizeur.app.R
import com.organizeur.app.settings.FilterAction
import com.organizeur.app.settings.SettingsManager
import com.organizeur.app.alarm.AlarmRepository
import com.organizeur.app.alarm.AlarmScheduler
import com.organizeur.app.alarm.AlarmService
import com.organizeur.app.silentmode.SilentModeAlarmScheduler
import com.organizeur.app.timer.TimerController
import com.organizeur.app.camera.CameraManager
import com.organizeur.app.silentmode.SilentModeEnforcer
import com.organizeur.app.silentmode.SilentModeManager
import com.organizeur.app.wearable.miband.MiBand4Manager
import com.organizeur.app.wearable.gear.sap.GearManager
import com.organizeur.app.wearable.model.BandState
import com.organizeur.app.wearable.miband.watchface.WatchfaceUploader
import com.organizeur.app.wearable.miband.ui.MiBandScanScreen
import com.organizeur.app.wearable.miband.ui.MiBandDeviceScreen
import com.organizeur.app.wearable.miband.ui.WatchfaceScreen
import com.organizeur.app.wearable.gear.ui.GearScanScreen
import com.organizeur.app.wearable.gear.ui.GearDeviceScreen
import com.organizeur.app.wearable.gear.ui.GearWallpaperScreen
import com.organizeur.app.wearable.gear.ui.GearClocksScreen

private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun MainScreen(
    settingsManager: SettingsManager,
    silentModeManager: SilentModeManager,
    silentModeScheduler: SilentModeAlarmScheduler,
    silentModeEnforcer: SilentModeEnforcer,
    cameraManager: CameraManager,
    alarmRepository: AlarmRepository,
    alarmScheduler: AlarmScheduler,
    timerController: TimerController,
    bandManager: MiBand4Manager,
    gearManager: GearManager,
    onRequestBtPermissions: () -> Unit,
    dynamicColors: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    // Collect wearable states
    val bandState by bandManager.state.collectAsState()
    val bandDevices by bandManager.scannedDevices.collectAsState()
    val bandData by bandManager.bandData.collectAsState()
    val deviceInfo by bandManager.deviceInfo.collectAsState()
    val bandLogs by bandManager.logs.collectAsState()

    val gearState by gearManager.state.collectAsState()
    val gearLogs by gearManager.logs.collectAsState()
    val gearClocks by gearManager.clocks.collectAsState()
    val gearActiveClock by gearManager.activeClock.collectAsState()
    val gearDeviceInfo by gearManager.deviceInfo.collectAsState()
    val gearClockSettingsConnected by gearManager.clockSettingsConnected.collectAsState()

    var uploadState by remember { mutableStateOf<WatchfaceUploader.UploadState>(WatchfaceUploader.UploadState.Idle) }

    // Auto-navigate on Mi Band connection/disconnection
    when (bandState) {
        is BandState.Connected, is BandState.Authenticating, is BandState.Authenticated ->
            if (currentScreen == Screen.MiBandScan) currentScreen = Screen.MiBandDevice
        is BandState.Disconnected ->
            if (currentScreen == Screen.MiBandDevice || currentScreen == Screen.MiBandWatchface) currentScreen = Screen.MiBandScan
        else -> {}
    }

    // Auto-navigate on Gear connection/disconnection
    when (gearState) {
        is GearManager.GearState.Connected ->
            if (currentScreen == Screen.GearScan) currentScreen = Screen.GearDevice
        is GearManager.GearState.Disconnected ->
            if (currentScreen == Screen.GearDevice || currentScreen == Screen.GearWallpaper || currentScreen == Screen.GearClocks) currentScreen = Screen.GearScan
        else -> {}
    }

    val gearFtState by gearManager.fileTransferState.collectAsState()

    when (currentScreen) {
        Screen.Dashboard -> DashboardScreen(
            settingsManager = settingsManager,
            onNavigate = { currentScreen = it }
        )
        Screen.CallFilter -> CallFilterScreen(
            settingsManager = settingsManager,
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.SilentMode -> SilentModeSection(
            manager = silentModeManager,
            scheduler = silentModeScheduler,
            enforcer = silentModeEnforcer,
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.Setup -> SetupScreen(
            settingsManager = settingsManager,
            dynamicColors = dynamicColors,
            onDynamicColorsChange = onDynamicColorsChange,
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.Help -> HelpScreen(
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.Camera -> CameraScreen(
            cameraManager = cameraManager,
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.Alarm -> AlarmSection(
            repository = alarmRepository,
            scheduler = alarmScheduler,
            silentModeManager = silentModeManager,
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.Timer -> TimerScreen(
            controller = timerController,
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.MiBandScan -> MiBandScanScreen(
            state = bandState,
            devices = bandDevices,
            onStartScan = onRequestBtPermissions,
            onStopScan = { bandManager.stopScan() },
            onDeviceSelected = { address -> bandManager.connect(address) },
            onBack = { currentScreen = Screen.Dashboard }
        )
        Screen.MiBandDevice -> MiBandDeviceScreen(
            state = bandState,
            bandData = bandData,
            deviceInfo = deviceInfo,
            logs = bandLogs,
            savedAuthKey = bandManager.savedAuthKey ?: "",
            onBack = {
                bandManager.disconnect()
                currentScreen = Screen.MiBandScan
            },
            onSetAuthKey = { bandManager.setAuthKey(it) },
            onAuthenticate = { bandManager.authenticate() },
            onReadBattery = { bandManager.readBattery() },
            onStartHeartRate = { continuous -> bandManager.startHeartRate(continuous) },
            onStopHeartRate = { bandManager.stopHeartRate() },
            onSendNotification = { bandManager.sendNotification() },
            onEnableMusicControls = { bandManager.enableMusicControls() },
            onDisconnect = { bandManager.disconnect() },
            onClearLogs = { bandManager.clearLogs() },
            onOpenWatchface = { currentScreen = Screen.MiBandWatchface }
        )
        Screen.MiBandWatchface -> WatchfaceScreen(
            isConnected = bandState is BandState.Authenticated,
            onBack = { currentScreen = Screen.MiBandDevice },
            onUpload = { data ->
                bandManager.uploadWatchface(data) { uploadState = it }
            },
            uploadState = uploadState
        )
        Screen.GearScan -> GearScanScreen(
            gearManager = gearManager,
            state = gearState,
            onBack = { currentScreen = Screen.Dashboard },
            onRequestPermissions = onRequestBtPermissions,
            onDeviceSelected = { device -> gearManager.connect(device) }
        )
        Screen.GearDevice -> GearDeviceScreen(
            state = gearState,
            deviceInfo = gearDeviceInfo,
            activeClock = gearActiveClock,
            logs = gearLogs,
            connectedServices = gearManager.getConnectedServices(),
            clockSettingsConnected = gearClockSettingsConnected,
            onBack = { currentScreen = Screen.GearScan },
            onSyncTime = { gearManager.syncTime() },
            onDisconnect = { gearManager.disconnect() },
            onClearLogs = { gearManager.clearLogs() },
            onOpenWallpaper = { currentScreen = Screen.GearWallpaper },
            onOpenClocks = { currentScreen = Screen.GearClocks },
            onSendTestMessage = { profile, json -> gearManager.sendTestMessage(profile, json) },
            onSendClockSettings = { settings ->
                val json = org.json.JSONObject()
                for ((k, v) in settings) {
                    json.put(k, v)
                }
                gearManager.sendClockSettings(json)
            },
        )
        Screen.GearWallpaper -> GearWallpaperScreen(
            transferState = gearFtState,
            onSendWallpaper = { data, name -> gearManager.sendWallpaper(data, name) },
            onBack = { currentScreen = Screen.GearDevice }
        )
        Screen.GearClocks -> GearClocksScreen(
            clocks = gearClocks,
            activeClock = gearActiveClock,
            logs = gearLogs,
            clockSettingsConnected = gearClockSettingsConnected,
            onBack = { currentScreen = Screen.GearDevice },
            onRefresh = { gearManager.requestClocksList() },
            onSetIdleClock = { gearManager.setIdleClock(it) },
            onRequestSettings = { gearManager.requestClockSettings(it) },
            onRequestPreview = { gearManager.requestClockPreview(it) },
            onSendClockSettings = { settings ->
                val json = org.json.JSONObject()
                for ((k, v) in settings) {
                    json.put(k, v)
                }
                gearManager.sendClockSettings(json)
            },
        )
    }
}

@Composable
private fun DashboardScreen(settingsManager: SettingsManager, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    var ringingName by remember { mutableStateOf(AlarmService.ringingAlarmName) }

    LaunchedEffect(Unit) {
        while (true) {
            ringingName = AlarmService.ringingAlarmName
            delay(500L)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Stop button when alarm is ringing
            if (ringingName != null) {
                Button(
                    onClick = {
                        context.stopService(Intent(context, AlarmService::class.java))
                        ringingName = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "STOP - $ringingName",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Header banner with assistante icon — edge to edge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding())
                    .background(MaterialTheme.colorScheme.primary),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(0.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(210.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Time and battery
                    val context = LocalContext.current
                    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    var currentTime by remember { mutableStateOf(timeFormat.format(Date())) }
                    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            currentTime = timeFormat.format(Date())
                            batteryLevel = getBatteryLevel(context)
                            delay(30_000L)
                        }
                    }

                    Text(
                        text = "$currentTime  \u2022  $batteryLevel%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu cards
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = padding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (settingsManager.isCallFilterVisible) {
                    MenuCard(
                        title = stringResource(R.string.menu_call_filter),
                        icon = Icons.Default.Phone,
                        onClick = { onNavigate(Screen.CallFilter) }
                    )
                }

                if (settingsManager.isSilentModeVisible && settingsManager.isAlarmVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MenuCard(
                                title = stringResource(R.string.menu_silent_mode),
                                icon = Icons.Default.NotificationsOff,
                                vertical = true,
                                onClick = { onNavigate(Screen.SilentMode) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MenuCard(
                                title = stringResource(R.string.menu_alarm),
                                icon = Icons.Default.Alarm,
                                vertical = true,
                                onClick = { onNavigate(Screen.Alarm) }
                            )
                        }
                    }
                } else if (settingsManager.isSilentModeVisible) {
                    MenuCard(
                        title = stringResource(R.string.menu_silent_mode),
                        icon = Icons.Default.NotificationsOff,
                        onClick = { onNavigate(Screen.SilentMode) }
                    )
                } else if (settingsManager.isAlarmVisible) {
                    MenuCard(
                        title = stringResource(R.string.menu_alarm),
                        icon = Icons.Default.Alarm,
                        onClick = { onNavigate(Screen.Alarm) }
                    )
                }

                if (settingsManager.isTimerVisible) {
                    MenuCard(
                        title = stringResource(R.string.menu_timer),
                        icon = Icons.Default.Timer,
                        onClick = { onNavigate(Screen.Timer) }
                    )
                }

                if (settingsManager.isCameraVisible) {
                    MenuCard(
                        title = stringResource(R.string.menu_camera),
                        icon = Icons.Default.Videocam,
                        onClick = { onNavigate(Screen.Camera) }
                    )
                }

                // Wearable cards (Mi Band + Gear)
                if (settingsManager.isMiBandVisible && settingsManager.isGearVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MenuCard(
                                title = stringResource(R.string.menu_miband),
                                icon = Icons.Default.Bluetooth,
                                vertical = true,
                                onClick = { onNavigate(Screen.MiBandScan) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MenuCard(
                                title = stringResource(R.string.menu_gear),
                                icon = Icons.Default.Watch,
                                vertical = true,
                                onClick = { onNavigate(Screen.GearScan) }
                            )
                        }
                    }
                } else if (settingsManager.isMiBandVisible) {
                    MenuCard(
                        title = stringResource(R.string.menu_miband),
                        icon = Icons.Default.Bluetooth,
                        onClick = { onNavigate(Screen.MiBandScan) }
                    )
                } else if (settingsManager.isGearVisible) {
                    MenuCard(
                        title = stringResource(R.string.menu_gear),
                        icon = Icons.Default.Watch,
                        onClick = { onNavigate(Screen.GearScan) }
                    )
                }

                if (settingsManager.isHelpVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MenuCard(
                                title = stringResource(R.string.menu_setup),
                                icon = Icons.Default.Settings,
                                vertical = true,
                                onClick = { onNavigate(Screen.Setup) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MenuCard(
                                title = stringResource(R.string.menu_help),
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                vertical = true,
                                onClick = { onNavigate(Screen.Help) }
                            )
                        }
                    }
                } else {
                    MenuCard(
                        title = stringResource(R.string.menu_setup),
                        icon = Icons.Default.Settings,
                        onClick = { onNavigate(Screen.Setup) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    icon: ImageVector,
    vertical: Boolean = false,
    onClick: () -> Unit
) {
    val iconTint = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (vertical) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = iconTint
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CallFilterScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    var filterEnabled by remember { mutableStateOf(settingsManager.isFilterEnabled) }
    var filterAction by remember { mutableStateOf(settingsManager.filterAction) }

    SubScreenScaffold(
        title = stringResource(R.string.menu_call_filter),
        onBack = onBack
    ) {
        // Filter toggle card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.filter_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.filter_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (filterEnabled)
                            stringResource(R.string.filter_enabled)
                        else
                            stringResource(R.string.filter_disabled)
                    )
                    Switch(
                        checked = filterEnabled,
                        onCheckedChange = {
                            filterEnabled = it
                            settingsManager.isFilterEnabled = it
                        }
                    )
                }
            }
        }

        // Action choice card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.action_label),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = filterAction == FilterAction.SILENCE,
                        onClick = {
                            filterAction = FilterAction.SILENCE
                            settingsManager.filterAction = FilterAction.SILENCE
                        }
                    )
                    Text(text = stringResource(R.string.action_silence))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = filterAction == FilterAction.REJECT,
                        onClick = {
                            filterAction = FilterAction.REJECT
                            settingsManager.filterAction = FilterAction.REJECT
                        }
                    )
                    Text(text = stringResource(R.string.action_reject))
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    settingsManager: SettingsManager,
    dynamicColors: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var permissionsGranted by remember {
        mutableStateOf(checkPermissions(context))
    }

    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    var isScreener by remember {
        mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isScreener = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    SubScreenScaffold(
        title = stringResource(R.string.menu_setup),
        onBack = onBack
    ) {
        // Setup as call screener
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isScreener)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.setup_screener),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isScreener) {
                    Text(
                        text = stringResource(R.string.screener_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = stringResource(R.string.setup_screener_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                            val intent = roleManager.createRequestRoleIntent(
                                RoleManager.ROLE_CALL_SCREENING
                            )
                            roleRequestLauncher.launch(intent)
                        }
                    }) {
                        Text(text = stringResource(R.string.setup_screener))
                    }
                }
            }
        }

        // Permissions card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (permissionsGranted)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.permissions_needed),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (permissionsGranted) {
                    Text(
                        text = stringResource(R.string.permissions_granted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = stringResource(R.string.permissions_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    }) {
                        Text(text = stringResource(R.string.grant_permissions))
                    }
                }
            }
        }

        // Apparence
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.setup_appearance),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.setup_dynamic_colors_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                FeatureToggleRow(
                    label = stringResource(R.string.setup_dynamic_colors),
                    checked = dynamicColors,
                    onCheckedChange = onDynamicColorsChange
                )
            }
        }

        // Features visibility toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.setup_features),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.setup_features_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                var callFilterVisible by remember { mutableStateOf(settingsManager.isCallFilterVisible) }
                var silentModeVisible by remember { mutableStateOf(settingsManager.isSilentModeVisible) }
                var alarmVisible by remember { mutableStateOf(settingsManager.isAlarmVisible) }
                var timerVisible by remember { mutableStateOf(settingsManager.isTimerVisible) }
                var cameraVisible by remember { mutableStateOf(settingsManager.isCameraVisible) }
                var helpVisible by remember { mutableStateOf(settingsManager.isHelpVisible) }
                var miBandVisible by remember { mutableStateOf(settingsManager.isMiBandVisible) }
                var gearVisible by remember { mutableStateOf(settingsManager.isGearVisible) }

                FeatureToggleRow(
                    label = stringResource(R.string.menu_call_filter),
                    checked = callFilterVisible,
                    onCheckedChange = {
                        callFilterVisible = it
                        settingsManager.isCallFilterVisible = it
                    }
                )
                FeatureToggleRow(
                    label = stringResource(R.string.menu_silent_mode),
                    checked = silentModeVisible,
                    onCheckedChange = {
                        silentModeVisible = it
                        settingsManager.isSilentModeVisible = it
                    }
                )
                FeatureToggleRow(
                    label = stringResource(R.string.menu_alarm),
                    checked = alarmVisible,
                    onCheckedChange = {
                        alarmVisible = it
                        settingsManager.isAlarmVisible = it
                    }
                )
                FeatureToggleRow(
                    label = stringResource(R.string.menu_timer),
                    checked = timerVisible,
                    onCheckedChange = {
                        timerVisible = it
                        settingsManager.isTimerVisible = it
                    }
                )
                FeatureToggleRow(
                    label = stringResource(R.string.menu_camera),
                    checked = cameraVisible,
                    onCheckedChange = {
                        cameraVisible = it
                        settingsManager.isCameraVisible = it
                    }
                )
                FeatureToggleRow(
                    label = stringResource(R.string.menu_miband),
                    checked = miBandVisible,
                    onCheckedChange = {
                        miBandVisible = it
                        settingsManager.isMiBandVisible = it
                    }
                )
                FeatureToggleRow(
                    label = stringResource(R.string.menu_gear),
                    checked = gearVisible,
                    onCheckedChange = {
                        gearVisible = it
                        settingsManager.isGearVisible = it
                    }
                )
                FeatureToggleRow(
                    label = stringResource(R.string.menu_help),
                    checked = helpVisible,
                    onCheckedChange = {
                        helpVisible = it
                        settingsManager.isHelpVisible = it
                    }
                )
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HelpScreen(onBack: () -> Unit) {
    SubScreenScaffold(
        title = stringResource(R.string.help_title),
        onBack = onBack
    ) {
        // Section Filtrage des appels
        HelpSectionHeader(stringResource(R.string.help_section_call_filter))
        HelpCard(
            title = stringResource(R.string.help_call_filter_title),
            body = stringResource(R.string.help_call_filter_body)
        )

        // Section Mode silencieux
        HelpSectionHeader(stringResource(R.string.help_section_silent_mode))
        HelpCard(
            title = stringResource(R.string.help_silent_mode_title),
            body = stringResource(R.string.help_silent_mode_body)
        )
        HelpCard(
            title = stringResource(R.string.help_volumes_title),
            body = stringResource(R.string.help_volumes_body)
        )
        HelpCard(
            title = stringResource(R.string.help_dnd_title),
            body = stringResource(R.string.help_dnd_body)
        )

        // Section Caméra
        HelpSectionHeader(stringResource(R.string.help_section_camera))
        HelpCard(
            title = stringResource(R.string.help_camera_title),
            body = stringResource(R.string.help_camera_body)
        )

        // Section Configuration
        HelpSectionHeader(stringResource(R.string.help_section_config))
        HelpCard(
            title = stringResource(R.string.help_permissions_title),
            body = stringResource(R.string.help_permissions_body)
        )

        // À propos
        HelpSectionHeader(stringResource(R.string.help_section_about))
        val context = LocalContext.current
        val versionName = remember {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }
        Text(
            text = "${stringResource(R.string.app_name)} v$versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun HelpCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getBatteryLevel(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100) / scale else 0
}

private fun checkPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
