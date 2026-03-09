package com.organizeur.app.silentmode

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class SilentModeAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAllAlarms(manager: SilentModeManager) {
        val schedules = manager.getSchedules()
        for (schedule in schedules) {
            if (schedule.enabled) {
                scheduleAlarms(schedule)
            } else {
                cancelAlarms(schedule)
            }
        }
    }

    fun scheduleAlarms(schedule: SilentModeSchedule) {
        if (!schedule.enabled) return
        if (!canScheduleExactAlarms()) return

        for (day in schedule.days) {
            scheduleStartAlarm(schedule, day)
            if (schedule.hasEndTime()) {
                scheduleEndAlarm(schedule, day)
            }
        }
    }

    fun cancelAlarms(schedule: SilentModeSchedule) {
        for (day in 1..7) {
            val startIntent = createPendingIntent(schedule.id, day, isStart = true)
            alarmManager.cancel(startIntent)
            val endIntent = createPendingIntent(schedule.id, day, isStart = false)
            alarmManager.cancel(endIntent)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Auto-granted on API 31-32
        }
    }

    private fun scheduleStartAlarm(schedule: SilentModeSchedule, dayOfWeek: Int) {
        val triggerTime = getNextTriggerTime(dayOfWeek, schedule.startHour, schedule.startMinute)
        val pendingIntent = createPendingIntent(schedule.id, dayOfWeek, isStart = true)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    private fun scheduleEndAlarm(schedule: SilentModeSchedule, dayOfWeek: Int) {
        val endHour = schedule.endHour ?: return
        val endMinute = schedule.endMinute ?: return

        var triggerTime = getNextTriggerTime(dayOfWeek, endHour, endMinute)

        // Handle overnight schedules: if end time is before start time, end is next day
        val startTime = getNextTriggerTime(dayOfWeek, schedule.startHour, schedule.startMinute)
        if (triggerTime <= startTime) {
            triggerTime += 24 * 60 * 60 * 1000L
        }

        val pendingIntent = createPendingIntent(schedule.id, dayOfWeek, isStart = false)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
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

    private fun createPendingIntent(scheduleId: String, dayOfWeek: Int, isStart: Boolean): PendingIntent {
        val intent = Intent(context, SilentModeReceiver::class.java).apply {
            action = if (isStart) ACTION_START else ACTION_END
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_DAY_OF_WEEK, dayOfWeek)
        }
        val requestCode = (scheduleId.hashCode() * 10 + dayOfWeek) * 2 + if (isStart) 0 else 1
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Check if there's an active schedule after boot. Verifies that the saved
     * active schedule ID points to an existing enabled schedule.
     */
    fun findActiveScheduleAfterBoot(manager: SilentModeManager): SilentModeSchedule? {
        val activeId = manager.getActiveScheduleId() ?: return null
        val schedule = manager.getScheduleById(activeId) ?: return null
        return if (schedule.enabled) schedule else null
    }

    companion object {
        const val ACTION_START = "com.organizeur.app.SILENT_MODE_START"
        const val ACTION_END = "com.organizeur.app.SILENT_MODE_END"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_DAY_OF_WEEK = "day_of_week"
    }
}
