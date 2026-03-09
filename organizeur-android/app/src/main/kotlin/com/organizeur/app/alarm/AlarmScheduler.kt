package com.organizeur.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.organizeur.app.MainActivity
import java.util.Calendar
import kotlin.math.abs

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAllAlarms(repository: AlarmRepository) {
        val alarms = repository.getAlarms()
        for (alarm in alarms) {
            if (alarm.enabled) {
                scheduleAlarm(alarm)
            } else {
                cancelAlarm(alarm)
            }
        }
    }

    fun scheduleAlarm(alarm: Alarm) {
        if (!alarm.enabled) return
        if (!canScheduleExactAlarms()) return

        if (alarm.recurring) {
            for (day in alarm.days) {
                val triggerTime = getNextTriggerTime(day, alarm.hour, alarm.minute)
                val pendingIntent = createPendingIntent(alarm.id, day)
                val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, createShowIntent())
                alarmManager.setAlarmClock(alarmInfo, pendingIntent)
            }
        } else {
            val triggerTime = getNextOneShotTriggerTime(alarm.hour, alarm.minute)
            val pendingIntent = createPendingIntent(alarm.id, -1) // -1 = ponctuel
            val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, createShowIntent())
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        }
    }

    fun cancelAlarm(alarm: Alarm) {
        for (day in 1..7) {
            val pendingIntent = createPendingIntent(alarm.id, day)
            alarmManager.cancel(pendingIntent)
        }
        // Also cancel one-shot pending intent
        val oneShotIntent = createPendingIntent(alarm.id, -1)
        alarmManager.cancel(oneShotIntent)
        // Also cancel any pending snooze
        val snoozeIntent = createPendingIntent(alarm.id, 0)
        alarmManager.cancel(snoozeIntent)
    }

    fun scheduleSnooze(alarm: Alarm) {
        if (!canScheduleExactAlarms()) return

        val triggerTime = System.currentTimeMillis() + alarm.snoozeDurationMinutes * 60_000L
        val pendingIntent = createPendingIntent(alarm.id, 0) // day=0 as snooze sentinel
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, createShowIntent())
        alarmManager.setAlarmClock(alarmInfo, pendingIntent)
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Auto-granted on API 31-32
        }
    }

    private fun getNextOneShotTriggerTime(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If the time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun getNextTriggerTime(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }

        // If the time has already passed this week, schedule for next week
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    private fun createShowIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPendingIntent(alarmId: String, dayOfWeek: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_DAY_OF_WEEK, dayOfWeek)
        }
        val requestCode = 100_000 + abs(alarmId.hashCode() * 10 + dayOfWeek) % 90_000
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_ALARM_FIRE = "com.organizeur.app.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_DAY_OF_WEEK = "alarm_day_of_week"
    }
}
