package com.organizeur.app.alarm

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizeur.app.R
import com.organizeur.app.ui.theme.OrganizeurTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent?.getStringExtra(AlarmService.EXTRA_ALARM_ID) ?: ""
        val repository = AlarmRepository(this)
        val alarm = repository.getAlarmById(alarmId)
        val alarmName = alarm?.name ?: ""

        setContent {
            // Auto-finish when alarm is stopped from overlay
            LaunchedEffect(Unit) {
                delay(1000)
                while (true) {
                    if (AlarmService.ringingAlarmName == null) {
                        this@AlarmRingingActivity.finish()
                        break
                    }
                    delay(500)
                }
            }
            OrganizeurTheme {
                AlarmRingingScreen(
                    alarmName = alarmName,
                    onDismiss = {
                        stopService(Intent(this@AlarmRingingActivity, AlarmService::class.java))
                        finish()
                    },
                    onSnooze = {
                        // Schedule snooze from the activity (more reliable than sending action to service)
                        val currentAlarm = repository.getAlarmById(alarmId)
                        if (currentAlarm != null) {
                            AlarmScheduler(this@AlarmRingingActivity).scheduleSnooze(currentAlarm)
                        }
                        stopService(Intent(this@AlarmRingingActivity, AlarmService::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun AlarmRingingScreen(
    alarmName: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(timeFormat.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormat.format(Date())
            delay(1_000L)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentTime,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (alarmName.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = alarmName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(R.string.alarm_dismiss),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.alarm_snooze),
                    fontSize = 18.sp
                )
            }
        }
    }
}
