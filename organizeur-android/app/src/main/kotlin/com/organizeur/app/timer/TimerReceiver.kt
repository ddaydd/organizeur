package com.organizeur.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.organizeur.app.alarm.AlarmService

/**
 * Déclenchement des minuteurs et actions des notifications (minuteur + chronomètre).
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val controller = TimerController(context)

        fun timerFromIntent(): CountdownTimer? =
            intent.getStringExtra(TimerScheduler.EXTRA_TIMER_ID)?.let { controller.getTimerById(it) }

        when (intent.action) {
            TimerScheduler.ACTION_TIMER_FIRE -> {
                val timerId = intent.getStringExtra(TimerScheduler.EXTRA_TIMER_ID) ?: return
                val timer = controller.expire(timerId) ?: return

                // Wake lock pour allumer l'écran (nécessaire sur MIUI)
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "organizeur:timer"
                )
                wakeLock.acquire(30_000L)

                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_START_TIMER
                    putExtra(AlarmService.EXTRA_TIMER_LABEL, timer.label)
                    putExtra(AlarmService.EXTRA_RINGTONE_URI, timer.ringtoneUri)
                }
                context.startForegroundService(serviceIntent)
            }

            ACTION_TIMER_START -> timerFromIntent()?.let { controller.start(it) }

            ACTION_TIMER_STOP -> timerFromIntent()?.let { controller.stop(it) }

            ACTION_TIMER_ADD_MINUTE -> timerFromIntent()?.let {
                if (it.isRunning) controller.addMinute(it)
            }

            ACTION_STOPWATCH_START -> controller.startStopwatch()

            ACTION_STOPWATCH_PAUSE -> controller.pauseStopwatch()

            ACTION_STOPWATCH_RESET -> controller.resetStopwatch()

            Intent.ACTION_BOOT_COMPLETED -> controller.rescheduleAll()
        }
    }

    companion object {
        const val ACTION_TIMER_START = "com.organizeur.app.TIMER_START"
        const val ACTION_TIMER_STOP = "com.organizeur.app.TIMER_STOP"
        const val ACTION_TIMER_ADD_MINUTE = "com.organizeur.app.TIMER_ADD_MINUTE"
        const val ACTION_STOPWATCH_START = "com.organizeur.app.STOPWATCH_START"
        const val ACTION_STOPWATCH_PAUSE = "com.organizeur.app.STOPWATCH_PAUSE"
        const val ACTION_STOPWATCH_RESET = "com.organizeur.app.STOPWATCH_RESET"
    }
}
