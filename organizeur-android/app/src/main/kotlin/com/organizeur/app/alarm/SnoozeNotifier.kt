package com.organizeur.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.organizeur.app.MainActivity
import com.organizeur.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Notification persistante affichée pendant qu'un rappel (snooze) est en attente,
 * avec une action pour l'annuler. Le décompte est tenu par le chronomètre natif
 * (aucun service ni tick applicatif).
 */
class SnoozeNotifier(private val context: Context) {

    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun show(alarm: Alarm, triggerAt: Long) {
        createChannel()

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerAt))
        val name = alarm.name.ifBlank { context.getString(R.string.alarm_notification_title) }

        val contentIntent = PendingIntent.getActivity(
            context, requestCode(alarm.id, 0),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_CANCEL_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, requestCode(alarm.id, 1), cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.alarm_snooze_pending_title, name))
            .setContentText(context.getString(R.string.alarm_snooze_pending_text, timeStr))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(triggerAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(contentIntent)
            .addAction(
                android.app.Notification.Action.Builder(
                    null, context.getString(R.string.alarm_snooze_cancel), cancelPendingIntent
                ).build()
            )
            .build()

        try {
            notificationManager.notify(notificationId(alarm.id), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS non accordée : le rappel reste actif, seule la notif manque
        }
    }

    fun cancel(alarmId: String) {
        notificationManager.cancel(notificationId(alarmId))
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_snooze_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationId(alarmId: String) = 3100 + abs(alarmId.hashCode()) % 800

    private fun requestCode(alarmId: String, slot: Int) =
        300_000 + abs(alarmId.hashCode() * 10 + slot) % 90_000

    companion object {
        private const val CHANNEL_ID = "alarm_snooze"
    }
}
