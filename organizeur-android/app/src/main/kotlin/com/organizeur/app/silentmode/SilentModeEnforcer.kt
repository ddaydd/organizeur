package com.organizeur.app.silentmode

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

class SilentModeEnforcer(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val manager = SilentModeManager(context)

    fun applySilentMode(schedule: SilentModeSchedule) {
        // Only save state if no other schedule is already active (avoid overwriting original state)
        if (manager.getActiveScheduleId() == null) {
            manager.savePreviousState(
                ringtoneVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING),
                notificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
                mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                interruptionFilter = notificationManager.currentInterruptionFilter
            )
        }

        manager.setActiveScheduleId(schedule.id)

        // Apply DND mode
        if (notificationManager.isNotificationPolicyAccessGranted) {
            val filter = when (schedule.dndMode) {
                DndMode.TOTAL_SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
                DndMode.ALARMS_ONLY -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                DndMode.PRIORITY_ONLY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            }
            notificationManager.setInterruptionFilter(filter)
        }

        // Apply volumes
        schedule.ringtoneVolume?.let { vol ->
            audioManager.setStreamVolume(AudioManager.STREAM_RING, vol, 0)
        }
        schedule.notificationVolume?.let { vol ->
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, vol, 0)
        }
        schedule.mediaVolume?.let { vol ->
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
        }
    }

    fun restorePreviousState() {
        val savedState = manager.getPreviousState()
        if (savedState != null) {
            // Restore volumes
            audioManager.setStreamVolume(AudioManager.STREAM_RING, savedState.ringtoneVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedState.notificationVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedState.mediaVolume, 0)

            // Restore DND
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(savedState.interruptionFilter)
            }

            manager.clearSavedState()
        }

        manager.setActiveScheduleId(null)
    }

    fun isAnyScheduleActive(): Boolean = manager.getActiveScheduleId() != null

    /**
     * Force DND off (INTERRUPTION_FILTER_ALL). Called by alarm as failsafe
     * in case restorePreviousState() didn't work or wasn't called.
     */
    fun forceDisableDnd() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
    }
}
