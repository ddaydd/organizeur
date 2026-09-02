package com.organizeur.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = AlarmRepository(context)
        val scheduler = AlarmScheduler(context)

        when (intent.action) {
            AlarmScheduler.ACTION_ALARM_FIRE -> {
                val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
                val dayOfWeek = intent.getIntExtra(AlarmScheduler.EXTRA_DAY_OF_WEEK, -1)
                val alarm = repository.getAlarmById(alarmId) ?: return

                // Le rappel éventuellement en attente vient d'arriver à échéance
                repository.clearSnooze(alarmId)
                SnoozeNotifier(context).cancel(alarmId)

                if (alarm.enabled) {
                    // Acquire wake lock to turn screen on (needed for MIUI)
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    @Suppress("DEPRECATION")
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                        "organizeur:alarm"
                    )
                    wakeLock.acquire(30_000L) // 30 seconds max

                    // Start the alarm ringing service
                    val serviceIntent = Intent(context, AlarmService::class.java).apply {
                        action = AlarmService.ACTION_START_ALARM
                        putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                    }
                    context.startForegroundService(serviceIntent)

                    // Launch ringing activity from the receiver (has alarm clock exemption)
                    val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                    }
                    try {
                        context.startActivity(activityIntent)
                    } catch (_: Exception) {}
                }

                // Re-schedule or disable
                if (dayOfWeek > 0) {
                    // Recurring alarm: re-schedule for next week
                    scheduler.scheduleAlarm(alarm)
                } else if (dayOfWeek == -1) {
                    // One-shot alarm: disable after firing
                    val disabledAlarm = alarm.copy(enabled = false)
                    repository.updateAlarm(disabledAlarm)
                }
            }

            AlarmScheduler.ACTION_CANCEL_SNOOZE -> {
                val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
                scheduler.cancelSnooze(alarmId)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                scheduler.scheduleAllAlarms(repository)
            }
        }
    }
}
