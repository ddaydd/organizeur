package com.organizeur.app.ui

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.organizeur.app.R
import com.organizeur.app.timer.CountdownTimer

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimerEditDialog(
    timer: CountdownTimer?,
    onDismiss: () -> Unit,
    onSave: (CountdownTimer) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialDuration = timer?.durationSeconds ?: 300
    var label by remember { mutableStateOf(timer?.label ?: "") }
    var hours by remember { mutableIntStateOf(initialDuration / 3600) }
    var minutes by remember { mutableIntStateOf((initialDuration % 3600) / 60) }
    var seconds by remember { mutableIntStateOf(initialDuration % 60) }
    var ringtoneUri by remember { mutableStateOf(timer?.ringtoneUri) }

    val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val defaultLabel = stringResource(R.string.alarm_ringtone_default)
    val defaultTimerLabel = stringResource(R.string.timer_default_label)

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

    val totalSeconds = hours * 3600 + minutes * 60 + seconds
    val presets = listOf(1, 3, 5, 10, 15, 30)

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
            Text(
                text = if (timer != null)
                    stringResource(R.string.timer_edit_title)
                else
                    stringResource(R.string.timer_new_title),
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.alarm_name_label)) },
                placeholder = { Text(stringResource(R.string.timer_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = stringResource(R.string.timer_duration_label),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                WheelPicker(value = hours, range = 0..23, onValueChange = { hours = it })
                DurationSeparator(stringResource(R.string.timer_unit_hours))
                WheelPicker(value = minutes, range = 0..59, onValueChange = { minutes = it })
                DurationSeparator(stringResource(R.string.timer_unit_minutes))
                WheelPicker(value = seconds, range = 0..59, onValueChange = { seconds = it })
                DurationSeparator(stringResource(R.string.timer_unit_seconds))
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                presets.forEach { presetMinutes ->
                    FilterChip(
                        selected = totalSeconds == presetMinutes * 60,
                        onClick = {
                            hours = 0
                            minutes = presetMinutes
                            seconds = 0
                        },
                        label = { Text(stringResource(R.string.alarm_snooze_min, presetMinutes)) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.alarm_ringtone_label),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = ringtoneName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
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

            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.timer_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.alarm_cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            CountdownTimer(
                                id = timer?.id ?: java.util.UUID.randomUUID().toString(),
                                label = label.ifBlank { defaultTimerLabel },
                                durationSeconds = totalSeconds,
                                ringtoneUri = ringtoneUri
                            )
                        )
                    },
                    enabled = totalSeconds > 0
                ) {
                    Text(stringResource(R.string.alarm_save))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DurationSeparator(unit: String) {
    Text(
        text = unit,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}
