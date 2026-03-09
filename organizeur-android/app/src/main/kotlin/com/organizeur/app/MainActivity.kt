package com.organizeur.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.organizeur.app.alarm.AlarmRepository
import com.organizeur.app.alarm.AlarmScheduler
import com.organizeur.app.camera.CameraManager
import com.organizeur.app.settings.SettingsManager
import com.organizeur.app.silentmode.SilentModeAlarmScheduler
import com.organizeur.app.silentmode.SilentModeEnforcer
import com.organizeur.app.silentmode.SilentModeManager
import com.organizeur.app.ui.MainScreen
import com.organizeur.app.ui.theme.OrganizeurTheme
import com.organizeur.app.wearable.miband.MiBand4Manager
import com.organizeur.app.wearable.gear.sap.GearManager

class MainActivity : ComponentActivity() {

    private lateinit var bandManager: MiBand4Manager
    private lateinit var gearManager: GearManager

    private val btPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val btPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                if (bandManager.savedDeviceAddress != null && bandManager.savedAuthKey != null) {
                    bandManager.autoConnect()
                } else {
                    bandManager.startScan()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val settingsManager = SettingsManager(this)
        val silentModeManager = SilentModeManager(this)
        val silentModeScheduler = SilentModeAlarmScheduler(this)
        val silentModeEnforcer = SilentModeEnforcer(this)
        val cameraManager = CameraManager(this)
        val alarmRepository = AlarmRepository(this)
        val alarmScheduler = AlarmScheduler(this)

        bandManager = MiBand4Manager(applicationContext)
        gearManager = GearManager(applicationContext)

        // Re-schedule alarms on app start (in case they were lost)
        silentModeScheduler.scheduleAllAlarms(silentModeManager)
        alarmScheduler.scheduleAllAlarms(alarmRepository)

        setContent {
            OrganizeurTheme {
                MainScreen(
                    settingsManager = settingsManager,
                    silentModeManager = silentModeManager,
                    silentModeScheduler = silentModeScheduler,
                    silentModeEnforcer = silentModeEnforcer,
                    cameraManager = cameraManager,
                    alarmRepository = alarmRepository,
                    alarmScheduler = alarmScheduler,
                    bandManager = bandManager,
                    gearManager = gearManager,
                    onRequestBtPermissions = ::requestBtPermissions
                )
            }
        }
    }

    private fun requestBtPermissions() {
        val missing = btPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            if (bandManager.savedDeviceAddress != null && bandManager.savedAuthKey != null) {
                bandManager.autoConnect()
            } else {
                bandManager.startScan()
            }
        } else {
            btPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}
