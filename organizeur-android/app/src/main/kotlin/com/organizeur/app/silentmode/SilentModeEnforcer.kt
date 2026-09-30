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
            val exceptions = schedule.priorityExceptions
            if (schedule.dndMode == DndMode.PRIORITY_ONLY && exceptions != null) {
                // Keep the first original policy if a schedule already replaced it
                if (manager.getPreviousPolicy() == null) {
                    manager.savePreviousPolicy(notificationManager.notificationPolicy)
                }
                notificationManager.notificationPolicy = buildPolicy(exceptions)
            }
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
                restorePreviousPolicy()
            }

            manager.clearSavedState()
        }

        manager.setActiveScheduleId(null)
    }

    private fun restorePreviousPolicy() {
        val policy = manager.getPreviousPolicy() ?: return
        notificationManager.notificationPolicy = policy
        manager.clearSavedPolicy()
    }

    private fun buildPolicy(exceptions: PriorityExceptions): NotificationManager.Policy {
        var categories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
            NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA
        if (exceptions.repeatCallers) {
            categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
        }
        if (exceptions.calls != PrioritySenders.NONE) {
            categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
        }
        if (exceptions.messages != PrioritySenders.NONE) {
            categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES
        }
        if (exceptions.conversations) {
            categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_CONVERSATIONS
        }
        return NotificationManager.Policy(
            categories,
            exceptions.calls.toPolicySenders(),
            exceptions.messages.toPolicySenders(),
            notificationManager.notificationPolicy.suppressedVisualEffects,
            if (exceptions.conversations) NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT
            else NotificationManager.Policy.CONVERSATION_SENDERS_NONE
        )
    }

    private fun PrioritySenders.toPolicySenders(): Int = when (this) {
        // Ignored by the system when the matching category is off
        PrioritySenders.NONE, PrioritySenders.STARRED -> NotificationManager.Policy.PRIORITY_SENDERS_STARRED
        PrioritySenders.CONTACTS -> NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS
        PrioritySenders.ANYONE -> NotificationManager.Policy.PRIORITY_SENDERS_ANY
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
