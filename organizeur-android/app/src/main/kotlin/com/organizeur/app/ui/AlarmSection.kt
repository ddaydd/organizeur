package com.organizeur.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.organizeur.app.R
import com.organizeur.app.alarm.Alarm
import com.organizeur.app.alarm.AlarmRepository
import com.organizeur.app.alarm.AlarmScheduler
import com.organizeur.app.alarm.AlarmService
import com.organizeur.app.silentmode.SilentModeManager
import com.organizeur.app.silentmode.SilentModeSchedule
import kotlinx.coroutines.delay
import java.util.Calendar

private val CardShape = RoundedCornerShape(28.dp)

/** Doit rester aligné sur la valeur par défaut de [Alarm.snoozeDurationMinutes]. */
private const val DEFAULT_SNOOZE_MINUTES = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSection(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    silentModeManager: SilentModeManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var alarms by remember { mutableStateOf(repository.getAlarms()) }
    val schedules = remember { silentModeManager.getSchedules().filter { it.enabled } }
    var showDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<Alarm?>(null) }
    var alarmGranted by remember { mutableStateOf(scheduler.canScheduleExactAlarms()) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var ringingName by remember { mutableStateOf(AlarmService.ringingAlarmName) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    var snoozes by remember { mutableStateOf(repository.getSnoozes()) }
    var showInfo by remember { mutableStateOf(repository.getAlarms().isEmpty()) }

    // Sans cette permission, la notification du rappel ne s'affiche pas (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Poll ringing state every 500ms + update time tick every minute
    LaunchedEffect(Unit) {
        while (true) {
            ringingName = AlarmService.ringingAlarmName
            nowTick = System.currentTimeMillis() / 60_000
            val pending = repository.getSnoozes()
            if (pending != snoozes) snoozes = pending
            delay(500L)
        }
    }

    fun refreshAlarms() {
        alarms = repository.getAlarms()
        snoozes = repository.getSnoozes()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alarmGranted = scheduler.canScheduleExactAlarms()
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_alarm)) },
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
                    editingAlarm = null
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.alarm_add))
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
            // Stop button when alarm is ringing
            if (ringingName != null) {
                Button(
                    onClick = {
                        context.stopService(Intent(context, AlarmService::class.java))
                        ringingName = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
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

            // Permission warning for exact alarms
            if (!alarmGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.silent_mode_perm_alarm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.silent_mode_perm_alarm_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                )
                            }
                            alarmGranted = scheduler.canScheduleExactAlarms()
                        }) {
                            Text(stringResource(R.string.silent_mode_perm_grant))
                        }
                    }
                }
            }

            // Overlay permission warning
            if (!overlayGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.alarm_perm_overlay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.alarm_perm_overlay_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }) {
                            Text(stringResource(R.string.silent_mode_perm_grant))
                        }
                    }
                }
            }

            ScreenDescription(
                text = stringResource(R.string.alarm_description),
                visible = showInfo
            )

            // Alarm list
            if (alarms.isEmpty()) {
                Text(
                    text = stringResource(R.string.alarm_no_alarms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                val nextAlarmId = alarms
                    .filter { it.enabled }
                    .minByOrNull { minutesUntilNextFiring(it, nowTick) ?: Long.MAX_VALUE }
                    ?.id

                alarms.forEach { alarm ->
                    AlarmItem(
                        alarm = alarm,
                        allAlarms = alarms,
                        schedules = schedules,
                        nowTick = nowTick,
                        snoozeAt = snoozes[alarm.id],
                        isNext = alarm.id == nextAlarmId,
                        onCancelSnooze = {
                            scheduler.cancelSnooze(alarm.id)
                            refreshAlarms()
                        },
                        onClick = {
                            editingAlarm = alarm
                            showDialog = true
                        },
                        onToggle = { enabled ->
                            val updated = alarm.copy(enabled = enabled)
                            repository.updateAlarm(updated)
                            if (enabled) {
                                scheduler.scheduleAlarm(updated)
                            } else {
                                scheduler.cancelAlarm(updated)
                            }
                            refreshAlarms()
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
        AlarmEditDialog(
            alarm = editingAlarm,
            onDismiss = { showDialog = false },
            onSave = { result ->
                if (editingAlarm != null) {
                    repository.updateAlarm(result)
                    scheduler.cancelAlarm(result)
                } else {
                    repository.addAlarm(result)
                }
                if (result.enabled) {
                    scheduler.scheduleAlarm(result)
                }
                refreshAlarms()
                showDialog = false
            },
            onDelete = if (editingAlarm != null) {
                {
                    editingAlarm?.let {
                        scheduler.cancelAlarm(it)
                        repository.deleteAlarm(it.id)
                    }
                    refreshAlarms()
                    showDialog = false
                }
            } else null
        )
    }
}

@Composable
private fun AlarmItem(
    alarm: Alarm,
    allAlarms: List<Alarm>,
    schedules: List<SilentModeSchedule>,
    nowTick: Long,
    snoozeAt: Long?,
    isNext: Boolean,
    onCancelSnooze: () -> Unit,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val deactivatedSchedules = if (alarm.deactivatesSilentMode)
        findDeactivatedSchedules(alarm, allAlarms, schedules)
    else
        emptyList()
    val timeStr = "%02d:%02d".format(alarm.hour, alarm.minute)
    val timeUntil = if (alarm.enabled) formatTimeUntil(minutesUntilNextFiring(alarm, nowTick)) else null
    val secondaryColor = if (isNext)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isNext)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (alarm.enabled) 1f else 0.45f)
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
                    if (alarm.name.isNotBlank()) {
                        Text(
                            text = alarm.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                Text(
                    text = formatAlarmDetails(alarm),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
                if (timeUntil != null) {
                    Text(
                        text = if (isNext) "Prochaine \u00B7 $timeUntil" else timeUntil,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (isNext) secondaryColor else MaterialTheme.colorScheme.tertiary
                    )
                }
                if (deactivatedSchedules.isNotEmpty()) {
                    Text(
                        text = "Désactive : ${deactivatedSchedules.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isNext) secondaryColor else MaterialTheme.colorScheme.primary
                    )
                }
                if (snoozeAt != null) {
                    val remaining = ((snoozeAt - nowTick * 60_000L) / 60_000L).toInt().coerceAtLeast(0)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Rappel à %02d:%02d (dans %d min)".format(
                                snoozeHour(snoozeAt), snoozeMinute(snoozeAt), remaining
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = onCancelSnooze,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.alarm_snooze_cancel),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

private fun snoozeHour(triggerAt: Long): Int =
    Calendar.getInstance().apply { timeInMillis = triggerAt }.get(Calendar.HOUR_OF_DAY)

private fun snoozeMinute(triggerAt: Long): Int =
    Calendar.getInstance().apply { timeInMillis = triggerAt }.get(Calendar.MINUTE)

private fun formatAlarmDetails(alarm: Alarm): String {
    val typeStr = if (alarm.recurring) {
        val dayNames = mapOf(
            Calendar.MONDAY to "Lun",
            Calendar.TUESDAY to "Mar",
            Calendar.WEDNESDAY to "Mer",
            Calendar.THURSDAY to "Jeu",
            Calendar.FRIDAY to "Ven",
            Calendar.SATURDAY to "Sam",
            Calendar.SUNDAY to "Dim"
        )
        alarm.days
            .sortedBy { if (it == Calendar.SUNDAY) 8 else it }
            .mapNotNull { dayNames[it] }
            .joinToString(", ")
    } else {
        "Ponctuel"
    }
    // Le rappel n'est affiché que s'il diffère du défaut : sinon il se répète sur chaque carte
    return if (alarm.snoozeDurationMinutes == DEFAULT_SNOOZE_MINUTES)
        typeStr
    else
        "$typeStr \u00B7 Rappel ${alarm.snoozeDurationMinutes} min"
}

/**
 * Find which silent mode schedules this alarm would deactivate,
 * excluding schedules that another earlier alarm would deactivate first.
 */
private fun findDeactivatedSchedules(
    alarm: Alarm,
    allAlarms: List<Alarm>,
    schedules: List<SilentModeSchedule>
): List<SilentModeSchedule> {
    return schedules.filter { schedule ->
        isAlarmInScheduleWindow(alarm, schedule) && !hasEarlierAlarm(alarm, schedule, allAlarms)
    }
}

/** Check if an alarm fires during a schedule's active window. */
private fun isAlarmInScheduleWindow(alarm: Alarm, schedule: SilentModeSchedule): Boolean {
    val alarmMinutes = alarm.hour * 60 + alarm.minute
    val startMinutes = schedule.startHour * 60 + schedule.startMinute
    val prevDaysOfAlarm = alarm.days.map { prevDay(it) }.toSet()

    if (schedule.hasEndTime()) {
        val endMinutes = schedule.endHour!! * 60 + schedule.endMinute!!
        return if (startMinutes <= endMinutes) {
            // Same-day schedule (e.g. 09:00 → 17:00)
            alarm.days.intersect(schedule.days).isNotEmpty()
                    && alarmMinutes in startMinutes..endMinutes
        } else {
            // Overnight schedule (e.g. 22:00 → 07:00)
            val eveningMatch = alarm.days.intersect(schedule.days).isNotEmpty()
                    && alarmMinutes >= startMinutes
            val morningMatch = prevDaysOfAlarm.intersect(schedule.days).isNotEmpty()
                    && alarmMinutes <= endMinutes
            eveningMatch || morningMatch
        }
    } else {
        // No end time: active until manually deactivated
        val sameDayMatch = alarm.days.intersect(schedule.days).isNotEmpty()
                && alarmMinutes >= startMinutes
        val prevDayMatch = prevDaysOfAlarm.intersect(schedule.days).isNotEmpty()
        return sameDayMatch || prevDayMatch
    }
}

/**
 * Check if another enabled alarm fires earlier in the same schedule window.
 * Uses offset from schedule start to compare alarm positions within the window.
 */
private fun hasEarlierAlarm(
    alarm: Alarm,
    schedule: SilentModeSchedule,
    allAlarms: List<Alarm>
): Boolean {
    val startMinutes = schedule.startHour * 60 + schedule.startMinute
    val alarmOffset = ((alarm.hour * 60 + alarm.minute) - startMinutes + 1440) % 1440

    return allAlarms.any { other ->
        other.id != alarm.id
                && other.enabled
                && other.deactivatesSilentMode
                && isAlarmInScheduleWindow(other, schedule)
                && ((other.hour * 60 + other.minute) - startMinutes + 1440) % 1440 < alarmOffset
    }
}

/** Returns the previous Calendar day of week (wraps Sunday ← Monday). */
private fun prevDay(day: Int): Int {
    return if (day == Calendar.SUNDAY) Calendar.SATURDAY
    else if (day == Calendar.MONDAY) Calendar.SUNDAY
    else day - 1
}

/** Calculate minutes until the next firing of an alarm. */
@Suppress("UNUSED_PARAMETER")
private fun minutesUntilNextFiring(alarm: Alarm, nowTick: Long): Long? {
    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val alarmMinutes = alarm.hour * 60 + alarm.minute
    val todayDow = now.get(Calendar.DAY_OF_WEEK)

    if (alarm.recurring) {
        if (alarm.days.isEmpty()) return null
        var minMinutes = Long.MAX_VALUE
        for (day in alarm.days) {
            var daysUntil = (day - todayDow + 7) % 7
            if (daysUntil == 0 && alarmMinutes <= nowMinutes) {
                daysUntil = 7
            }
            val totalMinutes = daysUntil.toLong() * 1440 + (alarmMinutes - nowMinutes)
            if (totalMinutes < minMinutes) minMinutes = totalMinutes
        }
        return if (minMinutes == Long.MAX_VALUE) null else minMinutes
    } else {
        var minutesUntil = (alarmMinutes - nowMinutes).toLong()
        if (minutesUntil <= 0) minutesUntil += 1440
        return minutesUntil
    }
}

/** Format minutes until next firing as a human-readable string. */
private fun formatTimeUntil(minutes: Long?): String? {
    if (minutes == null) return null
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    val mins = minutes % 60
    // « Dans 16h 47 » se lisait comme une heure : on explicite l'unité des minutes
    return when {
        days > 0 -> "Dans $days j $hours h"
        hours > 0 -> "Dans $hours h $mins min"
        mins > 0 -> "Dans $mins min"
        else -> "Maintenant"
    }
}
