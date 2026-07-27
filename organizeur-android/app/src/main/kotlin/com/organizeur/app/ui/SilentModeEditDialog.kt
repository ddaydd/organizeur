package com.organizeur.app.ui

import android.media.AudioManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizeur.app.R
import com.organizeur.app.silentmode.DndMode
import com.organizeur.app.silentmode.SilentModeSchedule
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SilentModeEditDialog(
    schedule: SilentModeSchedule?,
    onDismiss: () -> Unit,
    onSave: (SilentModeSchedule) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val audioManager = context.getSystemService(AudioManager::class.java)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val maxRingtone = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
    val maxNotification = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
    val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    var name by remember { mutableStateOf(schedule?.name ?: "") }
    var days by remember { mutableStateOf(schedule?.days ?: emptySet()) }
    var startHour by remember { mutableIntStateOf(schedule?.startHour ?: 22) }
    var startMinute by remember { mutableIntStateOf(schedule?.startMinute ?: 0) }
    var hasEndTime by remember { mutableStateOf(schedule?.hasEndTime() ?: false) }
    var endHour by remember { mutableIntStateOf(schedule?.endHour ?: 7) }
    var endMinute by remember { mutableIntStateOf(schedule?.endMinute ?: 0) }
    var dndMode by remember { mutableStateOf(schedule?.dndMode ?: DndMode.TOTAL_SILENCE) }

    var modifyRingtone by remember { mutableStateOf(schedule?.ringtoneVolume != null) }
    var ringtoneVol by remember { mutableIntStateOf(schedule?.ringtoneVolume ?: 0) }
    var modifyNotification by remember { mutableStateOf(schedule?.notificationVolume != null) }
    var notificationVol by remember { mutableIntStateOf(schedule?.notificationVolume ?: 0) }
    var modifyMedia by remember { mutableStateOf(schedule?.mediaVolume != null) }
    var mediaVol by remember { mutableIntStateOf(schedule?.mediaVolume ?: 0) }

    val dayEntries = listOf(
        Calendar.MONDAY to R.string.day_mon,
        Calendar.TUESDAY to R.string.day_tue,
        Calendar.WEDNESDAY to R.string.day_wed,
        Calendar.THURSDAY to R.string.day_thu,
        Calendar.FRIDAY to R.string.day_fri,
        Calendar.SATURDAY to R.string.day_sat,
        Calendar.SUNDAY to R.string.day_sun
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = if (schedule != null)
                    stringResource(R.string.silent_mode_edit_title)
                else
                    stringResource(R.string.silent_mode_new_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.silent_mode_name_label)) },
                placeholder = { Text(stringResource(R.string.silent_mode_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Days
            Text(
                text = stringResource(R.string.silent_mode_days_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dayEntries.forEach { (day, labelRes) ->
                    FilterChip(
                        selected = day in days,
                        onClick = {
                            days = if (day in days) days - day else days + day
                        },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            // Activation time
            Text(
                text = stringResource(R.string.silent_mode_start_time),
                style = MaterialTheme.typography.labelLarge
            )
            TimePickerRow(
                hour = startHour,
                minute = startMinute,
                onHourChange = { startHour = it },
                onMinuteChange = { startMinute = it }
            )

            // Optional end time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hasEndTime,
                    onCheckedChange = { hasEndTime = it }
                )
                Text(
                    text = stringResource(R.string.silent_mode_end_time),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (hasEndTime) {
                TimePickerRow(
                    hour = endHour,
                    minute = endMinute,
                    onHourChange = { endHour = it },
                    onMinuteChange = { endMinute = it }
                )
            }

            // DND Mode
            Text(
                text = stringResource(R.string.silent_mode_dnd_mode),
                style = MaterialTheme.typography.labelLarge
            )
            DndModeSelector(
                selected = dndMode,
                onSelected = { dndMode = it }
            )

            // Volumes
            Text(
                text = stringResource(R.string.silent_mode_volumes),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.volume_help_general),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            VolumeSliderRow(
                label = stringResource(R.string.silent_mode_volume_ringtone),
                hint = stringResource(R.string.volume_help_ringtone),
                relevant = dndMode == DndMode.PRIORITY_ONLY,
                noEffectLabel = stringResource(R.string.volume_no_effect),
                enabled = modifyRingtone,
                onEnabledChange = { modifyRingtone = it },
                value = ringtoneVol,
                maxValue = maxRingtone,
                onValueChange = { ringtoneVol = it }
            )

            VolumeSliderRow(
                label = stringResource(R.string.silent_mode_volume_notification),
                hint = stringResource(R.string.volume_help_notification),
                relevant = dndMode == DndMode.PRIORITY_ONLY,
                noEffectLabel = stringResource(R.string.volume_no_effect),
                enabled = modifyNotification,
                onEnabledChange = { modifyNotification = it },
                value = notificationVol,
                maxValue = maxNotification,
                onValueChange = { notificationVol = it }
            )

            VolumeSliderRow(
                label = stringResource(R.string.silent_mode_volume_media),
                hint = stringResource(R.string.volume_help_media),
                relevant = dndMode != DndMode.TOTAL_SILENCE,
                noEffectLabel = stringResource(R.string.volume_no_effect),
                enabled = modifyMedia,
                onEnabledChange = { modifyMedia = it },
                value = mediaVol,
                maxValue = maxMedia,
                onValueChange = { mediaVol = it }
            )

            // Delete button for existing schedules
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.silent_mode_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.silent_mode_cancel))
                }
                Button(
                    onClick = {
                        val result = SilentModeSchedule(
                            id = schedule?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { "Sans nom" },
                            enabled = schedule?.enabled ?: true,
                            days = days,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = if (hasEndTime) endHour else null,
                            endMinute = if (hasEndTime) endMinute else null,
                            dndMode = dndMode,
                            ringtoneVolume = if (modifyRingtone) ringtoneVol else null,
                            notificationVolume = if (modifyNotification) notificationVol else null,
                            mediaVolume = if (modifyMedia) mediaVol else null
                        )
                        onSave(result)
                    },
                    enabled = name.isNotBlank() && days.isNotEmpty()
                ) {
                    Text(stringResource(R.string.silent_mode_save))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TimePickerRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        WheelPicker(value = hour, range = 0..23, onValueChange = onHourChange)
        Text(
            ":",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        WheelPicker(value = minute, range = 0..59, onValueChange = onMinuteChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val itemCount = range.last - range.first + 1
    val itemHeight = 40.dp
    val visibleCount = 3
    val repetitions = 100
    val centerRepStart = (repetitions / 2) * itemCount
    val targetIndex = centerRepStart + (value - range.first)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = targetIndex - visibleCount / 2
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val centerIndex = listState.firstVisibleItemIndex + visibleCount / 2
                    val newValue = range.first + (centerIndex % itemCount)
                    if (newValue != value) {
                        onValueChange(newValue)
                    }
                }
            }
    }

    Box(
        modifier = Modifier
            .height(itemHeight * visibleCount)
            .width(64.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(itemCount * repetitions) { index ->
                val itemValue = range.first + (index % itemCount)
                val centerIndex = listState.firstVisibleItemIndex + visibleCount / 2
                val isCenter = index == centerIndex

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(itemValue),
                        fontSize = if (isCenter) 28.sp else 16.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Light,
                        color = if (isCenter)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
        // Selection indicator lines
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = -(itemHeight / 2))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = itemHeight / 2)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun DndModeSelector(
    selected: DndMode,
    onSelected: (DndMode) -> Unit
) {
    Column {
        val modes = listOf(
            DndMode.TOTAL_SILENCE to R.string.silent_mode_dnd_total,
            DndMode.ALARMS_ONLY to R.string.silent_mode_dnd_alarms,
            DndMode.PRIORITY_ONLY to R.string.silent_mode_dnd_priority
        )
        modes.forEach { (mode, labelRes) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == mode,
                    onClick = { onSelected(mode) }
                )
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    hint: String,
    relevant: Boolean,
    noEffectLabel: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.alpha(if (relevant) 1f else 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(text = label)
                if (relevant) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = noEffectLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (enabled && maxValue > 0) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..maxValue.toFloat(),
                steps = maxValue - 1,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
