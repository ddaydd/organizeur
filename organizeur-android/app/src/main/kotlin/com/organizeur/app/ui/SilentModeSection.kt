package com.organizeur.app.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.organizeur.app.R
import com.organizeur.app.silentmode.DndMode
import com.organizeur.app.silentmode.SilentModeAlarmScheduler
import com.organizeur.app.silentmode.SilentModeEnforcer
import com.organizeur.app.silentmode.SilentModeManager
import com.organizeur.app.silentmode.SilentModeSchedule
import java.util.Calendar

private val CardShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentModeSection(
    manager: SilentModeManager,
    scheduler: SilentModeAlarmScheduler,
    enforcer: SilentModeEnforcer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    var schedules by remember { mutableStateOf(manager.getSchedules()) }
    var showInfo by remember { mutableStateOf(manager.getSchedules().isEmpty()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<SilentModeSchedule?>(null) }
    var dndGranted by remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }
    var alarmGranted by remember { mutableStateOf(scheduler.canScheduleExactAlarms()) }
    var isSilentModeActive by remember {
        mutableStateOf(manager.getActiveScheduleId() != null)
    }

    fun refreshSchedules() {
        schedules = manager.getSchedules()
    }

    fun refreshActiveState() {
        isSilentModeActive = manager.getActiveScheduleId() != null
        val systemDndActive =
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if (!systemDndActive && isSilentModeActive) {
            enforcer.restorePreviousState()
            isSilentModeActive = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshActiveState()
                dndGranted = notificationManager.isNotificationPolicyAccessGranted
                alarmGranted = scheduler.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_silent_mode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = { InfoToggleAction(onToggle = { showInfo = !showInfo }) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = {
                    editingSchedule = null
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.silent_mode_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Deactivation card when silent mode is active
            if (isSilentModeActive) {
                val activeId = manager.getActiveScheduleId()
                val activeName = schedules.find { it.id == activeId }?.name ?: ""
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${stringResource(R.string.silent_mode_active)} : $activeName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                enforcer.restorePreviousState()
                                isSilentModeActive = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.silent_mode_deactivate))
                        }
                    }
                }
            }

            // Permission warnings
            if (!dndGranted) {
                PermissionWarningCard(
                    title = stringResource(R.string.silent_mode_perm_dnd),
                    description = stringResource(R.string.silent_mode_perm_dnd_desc),
                    onGrant = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        )
                        dndGranted = notificationManager.isNotificationPolicyAccessGranted
                    }
                )
            }

            if (!alarmGranted) {
                PermissionWarningCard(
                    title = stringResource(R.string.silent_mode_perm_alarm),
                    description = stringResource(R.string.silent_mode_perm_alarm_desc),
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                        alarmGranted = scheduler.canScheduleExactAlarms()
                    }
                )
            }

            ScreenDescription(
                text = stringResource(R.string.silent_mode_description),
                visible = showInfo
            )

            // Schedule list
            if (schedules.isEmpty()) {
                Text(
                    text = stringResource(R.string.silent_mode_no_schedules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                schedules.forEach { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onClick = {
                            editingSchedule = schedule
                            showDialog = true
                        },
                        onToggle = { enabled ->
                            val updated = schedule.copy(enabled = enabled)
                            manager.updateSchedule(updated)
                            if (enabled) {
                                scheduler.scheduleAlarms(updated)
                            } else {
                                scheduler.cancelAlarms(updated)
                            }
                            refreshSchedules()
                        }
                    )
                }
            }

            // Bottom spacer for FAB
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    // Edit/Create dialog
    if (showDialog) {
        SilentModeEditDialog(
            schedule = editingSchedule,
            onDismiss = { showDialog = false },
            onSave = { result ->
                if (editingSchedule != null) {
                    manager.updateSchedule(result)
                    scheduler.cancelAlarms(result)
                } else {
                    manager.addSchedule(result)
                }
                if (result.enabled) {
                    scheduler.scheduleAlarms(result)
                }
                refreshSchedules()
                showDialog = false
            },
            onDelete = if (editingSchedule != null) {
                {
                    editingSchedule?.let {
                        scheduler.cancelAlarms(it)
                        if (manager.getActiveScheduleId() == it.id) {
                            enforcer.restorePreviousState()
                            isSilentModeActive = false
                        }
                        manager.deleteSchedule(it.id)
                    }
                    refreshSchedules()
                    showDialog = false
                }
            } else null
        )
    }
}

@Composable
private fun ScheduleItem(
    schedule: SilentModeSchedule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val timeStr = "%02d:%02d".format(schedule.startHour, schedule.startMinute)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (schedule.name.isNotBlank()) {
                        Text(
                            text = schedule.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                Text(
                    text = formatScheduleDetails(schedule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun PermissionWarningCard(
    title: String,
    description: String,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onGrant) {
                Text(stringResource(R.string.silent_mode_perm_grant))
            }
        }
    }
}

private fun formatScheduleDetails(schedule: SilentModeSchedule): String {
    val dayNames = mapOf(
        Calendar.MONDAY to "Lun",
        Calendar.TUESDAY to "Mar",
        Calendar.WEDNESDAY to "Mer",
        Calendar.THURSDAY to "Jeu",
        Calendar.FRIDAY to "Ven",
        Calendar.SATURDAY to "Sam",
        Calendar.SUNDAY to "Dim"
    )
    val daysStr = schedule.days
        .sortedBy { if (it == Calendar.SUNDAY) 8 else it }
        .mapNotNull { dayNames[it] }
        .joinToString(", ")
    val endStr = if (schedule.hasEndTime()) {
        " \u2192 %02d:%02d".format(schedule.endHour, schedule.endMinute)
    } else {
        ""
    }
    val modeStr = when (schedule.dndMode) {
        DndMode.TOTAL_SILENCE -> "Silence total"
        DndMode.ALARMS_ONLY -> "Alarmes"
        DndMode.PRIORITY_ONLY -> "Prioritaires"
    }
    return "$daysStr$endStr \u00B7 $modeStr"
}
