package com.organizeur.app.silentmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SilentModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = SilentModeManager(context)
        val enforcer = SilentModeEnforcer(context)
        val scheduler = SilentModeAlarmScheduler(context)

        when (intent.action) {
            SilentModeAlarmScheduler.ACTION_START -> {
                val scheduleId = intent.getStringExtra(SilentModeAlarmScheduler.EXTRA_SCHEDULE_ID)
                    ?: return
                val dayOfWeek = intent.getIntExtra(SilentModeAlarmScheduler.EXTRA_DAY_OF_WEEK, -1)
                val schedule = manager.getScheduleById(scheduleId) ?: return

                if (schedule.enabled) {
                    enforcer.applySilentMode(schedule)
                }

                // Re-schedule this alarm for next week
                if (dayOfWeek > 0) {
                    scheduler.scheduleAlarms(schedule)
                }
            }

            SilentModeAlarmScheduler.ACTION_END -> {
                val scheduleId = intent.getStringExtra(SilentModeAlarmScheduler.EXTRA_SCHEDULE_ID)
                    ?: return
                val dayOfWeek = intent.getIntExtra(SilentModeAlarmScheduler.EXTRA_DAY_OF_WEEK, -1)

                // Only restore if this schedule is the one that's active
                if (manager.getActiveScheduleId() == scheduleId) {
                    enforcer.restorePreviousState()
                }

                // Re-schedule this alarm for next week
                val schedule = manager.getScheduleById(scheduleId)
                if (schedule != null && dayOfWeek > 0) {
                    scheduler.scheduleAlarms(schedule)
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-schedule all alarms after boot
                scheduler.scheduleAllAlarms(manager)

                // Re-apply active schedule if one was running before reboot
                val activeSchedule = scheduler.findActiveScheduleAfterBoot(manager)
                if (activeSchedule != null) {
                    enforcer.applySilentMode(activeSchedule)
                }
            }
        }
    }
}
