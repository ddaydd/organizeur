package com.organizeur.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.organizeur.app.R
import com.organizeur.app.alarm.AlarmService
import com.organizeur.app.timer.CountdownTimer
import com.organizeur.app.timer.StopwatchState
import com.organizeur.app.timer.TimerController
import com.organizeur.app.timer.formatDuration
import com.organizeur.app.timer.formatStopwatch
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    controller: TimerController,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var timers by remember { mutableStateOf(controller.getTimers()) }
    var stopwatch by remember { mutableStateOf(controller.getStopwatch()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ringingName by remember { mutableStateOf(AlarmService.ringingAlarmName) }
    var alarmGranted by remember { mutableStateOf(controller.canScheduleExactAlarms()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingTimer by remember { mutableStateOf<CountdownTimer?>(null) }
    var showInfo by remember { mutableStateOf(controller.getTimers().isEmpty()) }

    // Les notifications de décompte ne s'affichent pas sans cette permission (Android 13+)
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

    // Tick d'affichage : le temps n'est jamais décompté, il est recalculé à chaque frame
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            ringingName = AlarmService.ringingAlarmName
            // Un minuteur arrivé à zéro a été remis à l'arrêt par TimerReceiver : on resynchronise
            if (timers.any { it.isRunning && it.remainingSeconds(now) == 0 }) {
                timers = controller.getTimers()
            }
            delay(100L)
        }
    }

    // Les actions des notifications modifient l'état hors de l'écran
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                timers = controller.getTimers()
                stopwatch = controller.getStopwatch()
                alarmGranted = controller.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_timer)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) InfoToggleAction(onToggle = { showInfo = !showInfo })
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = {
                        editingTimer = null
                        showDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.timer_add))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.timer_tab_countdown)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.timer_tab_stopwatch)) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Bouton d'arrêt quand un minuteur (ou une alarme) sonne
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

                if (selectedTab == 0) {
                    if (!alarmGranted) {
                        ExactAlarmWarningCard(onGrant = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                            alarmGranted = controller.canScheduleExactAlarms()
                        })
                    }

                    ScreenDescription(
                        text = stringResource(R.string.timer_description),
                        visible = showInfo
                    )

                    if (timers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.timer_no_timers),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    } else {
                        timers.forEach { timer ->
                            TimerItem(
                                timer = timer,
                                now = now,
                                onToggle = {
                                    if (timer.isRunning) controller.pause(timer)
                                    else controller.start(timer)
                                    timers = controller.getTimers()
                                },
                                onAddMinute = {
                                    controller.addMinute(timer)
                                    timers = controller.getTimers()
                                },
                                onReset = {
                                    controller.stop(timer)
                                    timers = controller.getTimers()
                                },
                                onEdit = {
                                    editingTimer = timer
                                    showDialog = true
                                }
                            )
                        }
                    }
                } else {
                    StopwatchTab(
                        state = stopwatch,
                        now = now,
                        onToggle = {
                            stopwatch = if (stopwatch.isRunning) controller.pauseStopwatch()
                            else controller.startStopwatch()
                        },
                        onLap = { stopwatch = controller.lapStopwatch() },
                        onReset = { stopwatch = controller.resetStopwatch() }
                    )
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    if (showDialog) {
        TimerEditDialog(
            timer = editingTimer,
            onDismiss = { showDialog = false },
            onSave = { result ->
                controller.save(result)
                timers = controller.getTimers()
                showDialog = false
            },
            onDelete = editingTimer?.let { target ->
                {
                    controller.delete(target)
                    timers = controller.getTimers()
                    showDialog = false
                }
            }
        )
    }
}

@Composable
private fun ExactAlarmWarningCard(onGrant: () -> Unit) {
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
                text = stringResource(R.string.timer_perm_alarm_desc),
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

@Composable
private fun TimerItem(
    timer: CountdownTimer,
    now: Long,
    onToggle: () -> Unit,
    onAddMinute: () -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit
) {
    val remaining = timer.remainingSeconds(now)
    val active = timer.isRunning || timer.isPaused

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = timer.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(remaining),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (timer.isPaused) {
                        Text(
                            text = stringResource(R.string.timer_state_paused),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (timer.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (timer.isRunning) R.string.timer_pause else R.string.timer_start
                        ),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (timer.isRunning && timer.durationSeconds > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { remaining.toFloat() / timer.durationSeconds.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onAddMinute) {
                        Text(stringResource(R.string.timer_add_minute))
                    }
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.timer_reset))
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchTab(
    state: StopwatchState,
    now: Long,
    onToggle: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit
) {
    val elapsed = state.elapsedMillis(now)

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = formatStopwatch(elapsed),
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onToggle,
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(
                    if (state.isRunning) R.string.timer_pause
                    else if (state.isReset) R.string.timer_start
                    else R.string.timer_resume
                )
            )
        }
        FilledTonalButton(
            onClick = onLap,
            enabled = state.isRunning,
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
        ) {
            Icon(Icons.Default.Flag, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.timer_lap))
        }
    }

    OutlinedButton(
        onClick = onReset,
        enabled = !state.isReset,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text(stringResource(R.string.timer_reset))
    }

    if (state.laps.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                // Du tour le plus récent au plus ancien
                state.laps.indices.reversed().forEach { index ->
                    val total = state.laps[index]
                    val lapDuration = total - (if (index > 0) state.laps[index - 1] else 0L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.timer_lap_number, index + 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatStopwatch(lapDuration),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatStopwatch(total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index > 0) HorizontalDivider()
                }
            }
        }
    }
}
