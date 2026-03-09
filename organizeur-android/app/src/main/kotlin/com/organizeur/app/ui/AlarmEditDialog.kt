package com.organizeur.app.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.organizeur.app.R
import com.organizeur.app.alarm.Alarm
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditDialog(
    alarm: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(alarm?.name ?: "") }
    var hour by remember { mutableIntStateOf(alarm?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var days by remember { mutableStateOf(alarm?.days ?: emptySet()) }
    var snoozeDuration by remember { mutableIntStateOf(alarm?.snoozeDurationMinutes ?: 10) }
    var ringtoneUri by remember { mutableStateOf(alarm?.ringtoneUri) }
    var recurring by remember { mutableStateOf(alarm?.recurring ?: true) }
    var deactivatesSilentMode by remember { mutableStateOf(alarm?.deactivatesSilentMode ?: true) }

    val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val defaultLabel = stringResource(R.string.alarm_ringtone_default)

    val ringtoneName = remember(ringtoneUri) {
        if (ringtoneUri != null) {
            try {
                RingtoneManager.getRingtone(context, Uri.parse(ringtoneUri))
                    ?.getTitle(context) ?: defaultLabel
            } catch (_: Exception) {
                defaultLabel
            }
        } else {
            defaultLabel
        }
    }

    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    fun stopPreview() {
        previewPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            it.release()
        }
        previewPlayer = null
        previewRingtone?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
        }
        previewRingtone = null
        isPlaying = false
    }

    fun startPreview() {
        stopPreview()
        val uri = if (ringtoneUri != null) Uri.parse(ringtoneUri) else defaultAlarmUri
        try {
            previewPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener { isPlaying = false }
                prepare()
                start()
            }
            isPlaying = true
        } catch (_: Exception) {
            try {
                previewRingtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    play()
                }
                isPlaying = previewRingtone != null
            } catch (_: Exception) {
                isPlaying = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null && uri != defaultAlarmUri) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            ringtoneUri = uri.toString()
        } else {
            ringtoneUri = null
        }
    }

    val dayEntries = listOf(
        Calendar.MONDAY to R.string.day_mon,
        Calendar.TUESDAY to R.string.day_tue,
        Calendar.WEDNESDAY to R.string.day_wed,
        Calendar.THURSDAY to R.string.day_thu,
        Calendar.FRIDAY to R.string.day_fri,
        Calendar.SATURDAY to R.string.day_sat,
        Calendar.SUNDAY to R.string.day_sun
    )

    val snoozeOptions = listOf(5, 10, 15)

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
                text = if (alarm != null)
                    stringResource(R.string.alarm_edit_title)
                else
                    stringResource(R.string.alarm_new_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.alarm_name_label)) },
                placeholder = { Text(stringResource(R.string.alarm_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Time
            Text(
                text = stringResource(R.string.alarm_time_label),
                style = MaterialTheme.typography.labelLarge
            )
            TimePickerRow(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it }
            )

            // Type: Ponctuel / Récurrent
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = !recurring,
                    onClick = { recurring = false },
                    label = { Text(stringResource(R.string.alarm_one_time)) }
                )
                FilterChip(
                    selected = recurring,
                    onClick = { recurring = true },
                    label = { Text(stringResource(R.string.alarm_recurring)) }
                )
            }

            // Days (only for recurring alarms)
            if (recurring) {
                Text(
                    text = stringResource(R.string.alarm_days_label),
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
            }

            // Snooze duration
            Text(
                text = stringResource(R.string.alarm_snooze_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                snoozeOptions.forEach { minutes ->
                    FilterChip(
                        selected = snoozeDuration == minutes,
                        onClick = { snoozeDuration = minutes },
                        label = { Text(stringResource(R.string.alarm_snooze_min, minutes)) }
                    )
                }
            }

            // Ringtone picker
            Text(
                text = stringResource(R.string.alarm_ringtone_label),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ringtoneName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            stopPreview()
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    if (ringtoneUri != null) Uri.parse(ringtoneUri) else defaultAlarmUri
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultAlarmUri)
                            }
                            ringtoneLauncher.launch(intent)
                        }
                        .padding(vertical = 8.dp)
                )
                IconButton(
                    onClick = { if (isPlaying) stopPreview() else startPreview() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        ),
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Deactivate silent mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.alarm_deactivates_silent_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = deactivatesSilentMode,
                    onCheckedChange = { deactivatesSilentMode = it }
                )
            }

            // Delete button for existing alarms
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.alarm_delete),
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
                    Text(stringResource(R.string.alarm_cancel))
                }
                Button(
                    onClick = {
                        val result = Alarm(
                            id = alarm?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { "Sans nom" },
                            enabled = alarm?.enabled ?: true,
                            hour = hour,
                            minute = minute,
                            days = if (recurring) days else emptySet(),
                            snoozeDurationMinutes = snoozeDuration,
                            ringtoneUri = ringtoneUri,
                            recurring = recurring,
                            deactivatesSilentMode = deactivatesSilentMode
                        )
                        onSave(result)
                    },
                    enabled = name.isNotBlank() && (!recurring || days.isNotEmpty())
                ) {
                    Text(stringResource(R.string.alarm_save))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
