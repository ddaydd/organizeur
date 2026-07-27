package com.organizeur.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.organizeur.app.MainActivity
import com.organizeur.app.R
import kotlin.math.abs

/**
 * Notifications persistantes des minuteurs et du chronomètre.
 *
 * Le décompte est rendu par le chronomètre natif des notifications
 * (`setUsesChronometer`), donc aucun service ni tick n'est nécessaire pour
 * garder l'affichage à jour quand l'appli est fermée.
 */
class TimerNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    // --- Minuteurs ---

    fun showTimer(timer: CountdownTimer) {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(timer.label)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

        if (timer.isRunning) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(timer.endAtMillis!!)
                .setShowWhen(true)
                .addAction(
                    action(
                        context.getString(R.string.timer_notif_add_minute),
                        TimerReceiver.ACTION_TIMER_ADD_MINUTE, timer.id, 1
                    )
                )
        } else {
            builder.setShowWhen(false)
                .setContentText(
                    context.getString(
                        R.string.timer_notif_paused,
                        formatDuration(timer.remainingSeconds())
                    )
                )
                .addAction(
                    action(
                        context.getString(R.string.timer_resume),
                        TimerReceiver.ACTION_TIMER_START, timer.id, 2
                    )
                )
        }

        builder.addAction(
            action(context.getString(R.string.timer_stop), TimerReceiver.ACTION_TIMER_STOP, timer.id, 0)
        )

        manager.notify(timerNotificationId(timer.id), builder.build())
    }

    fun cancelTimer(timerId: String) {
        manager.cancel(timerNotificationId(timerId))
    }

    // --- Chronomètre ---

    fun showStopwatch(state: StopwatchState) {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.timer_tab_stopwatch))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

        if (state.isRunning) {
            builder.setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - state.elapsedMillis())
                .setShowWhen(true)
                .addAction(
                    action(
                        context.getString(R.string.timer_pause),
                        TimerReceiver.ACTION_STOPWATCH_PAUSE, null, 10
                    )
                )
        } else {
            builder.setShowWhen(false)
                .setContentText(formatStopwatch(state.elapsedMillis()))
                .addAction(
                    action(
                        context.getString(R.string.timer_resume),
                        TimerReceiver.ACTION_STOPWATCH_START, null, 11
                    )
                )
        }

        builder.addAction(
            action(context.getString(R.string.timer_reset), TimerReceiver.ACTION_STOPWATCH_RESET, null, 12)
        )

        manager.notify(STOPWATCH_NOTIFICATION_ID, builder.build())
    }

    fun cancelStopwatch() {
        manager.cancel(STOPWATCH_NOTIFICATION_ID)
    }

    // --- Utilitaires ---

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Les extras ne comptant pas dans l'égalité des Intent, chaque action doit avoir
     * son propre request code, sinon deux minuteurs partageraient le même PendingIntent.
     */
    private fun action(label: String, action: String, timerId: String?, slot: Int): Notification.Action {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            this.action = action
            if (timerId != null) putExtra(TimerScheduler.EXTRA_TIMER_ID, timerId)
        }
        val base = if (timerId != null) 210_000 + (abs(timerId.hashCode()) % 1000) * 20 else 300_000
        val pendingIntent = PendingIntent.getBroadcast(
            context, base + slot, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(null, label, pendingIntent).build()
    }

    private fun timerNotificationId(timerId: String): Int = 4000 + abs(timerId.hashCode()) % 1000

    companion object {
        const val CHANNEL_ID = "timer_progress"
        const val STOPWATCH_NOTIFICATION_ID = 3999
    }
}
