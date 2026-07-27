package com.organizeur.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.organizeur.app.MainActivity
import kotlin.math.abs

/**
 * Planification du déclenchement des minuteurs via AlarmManager.
 *
 * Request codes dans la plage 200_000..289_999 pour ne pas entrer en collision
 * avec ceux des alarmes (100_000..189_999).
 */
class TimerScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(timer: CountdownTimer) {
        val endAt = timer.endAtMillis ?: return
        if (!canScheduleExactAlarms()) return
        val alarmInfo = AlarmManager.AlarmClockInfo(endAt, createShowIntent())
        alarmManager.setAlarmClock(alarmInfo, createPendingIntent(timer.id))
    }

    fun cancel(timerId: String) {
        alarmManager.cancel(createPendingIntent(timerId))
    }

    /**
     * Replanifie les minuteurs en cours (démarrage de l'appli, redémarrage du téléphone).
     * Les minuteurs dont l'échéance est déjà passée (téléphone éteint au moment du top)
     * sont remis à l'arrêt.
     */
    fun rescheduleAll(repository: TimerRepository, notifier: TimerNotifier) {
        val now = System.currentTimeMillis()
        val timers = repository.getTimers()
        var changed = false
        val updated = timers.map { timer ->
            val endAt = timer.endAtMillis
            when {
                endAt == null -> timer
                endAt <= now -> {
                    changed = true
                    notifier.cancelTimer(timer.id)
                    timer.stopped()
                }
                else -> {
                    schedule(timer)
                    notifier.showTimer(timer)
                    timer
                }
            }
        }
        if (changed) repository.saveTimers(updated)

        val stopwatch = repository.getStopwatch()
        if (stopwatch.isRunning) notifier.showStopwatch(stopwatch)
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // accordé d'office sur API 31-32
        }
    }

    private fun createShowIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPendingIntent(timerId: String): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = ACTION_TIMER_FIRE
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        val requestCode = 200_000 + abs(timerId.hashCode()) % 90_000
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_TIMER_FIRE = "com.organizeur.app.TIMER_FIRE"
        const val EXTRA_TIMER_ID = "timer_id"
    }
}
