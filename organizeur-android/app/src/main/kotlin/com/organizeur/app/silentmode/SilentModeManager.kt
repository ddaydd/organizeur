package com.organizeur.app.silentmode

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class SilentModeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSchedules(): List<SilentModeSchedule> {
        val json = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { SilentModeSchedule.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSchedules(schedules: List<SilentModeSchedule>) {
        val array = JSONArray()
        schedules.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SCHEDULES, array.toString()).apply()
    }

    fun addSchedule(schedule: SilentModeSchedule) {
        val schedules = getSchedules().toMutableList()
        schedules.add(schedule)
        saveSchedules(schedules)
    }

    fun updateSchedule(schedule: SilentModeSchedule) {
        val schedules = getSchedules().toMutableList()
        val index = schedules.indexOfFirst { it.id == schedule.id }
        if (index >= 0) {
            schedules[index] = schedule
            saveSchedules(schedules)
        }
    }

    fun deleteSchedule(id: String) {
        val schedules = getSchedules().filter { it.id != id }
        saveSchedules(schedules)
    }

    fun getScheduleById(id: String): SilentModeSchedule? {
        return getSchedules().find { it.id == id }
    }

    // Saved state for volume/DND restoration
    fun savePreviousState(
        ringtoneVolume: Int,
        notificationVolume: Int,
        mediaVolume: Int,
        interruptionFilter: Int
    ) {
        prefs.edit()
            .putInt(KEY_PREV_RINGTONE, ringtoneVolume)
            .putInt(KEY_PREV_NOTIFICATION, notificationVolume)
            .putInt(KEY_PREV_MEDIA, mediaVolume)
            .putInt(KEY_PREV_INTERRUPTION, interruptionFilter)
            .putBoolean(KEY_HAS_SAVED_STATE, true)
            .apply()
    }

    fun hasSavedState(): Boolean = prefs.getBoolean(KEY_HAS_SAVED_STATE, false)

    fun getPreviousState(): SavedAudioState? {
        if (!hasSavedState()) return null
        return SavedAudioState(
            ringtoneVolume = prefs.getInt(KEY_PREV_RINGTONE, 0),
            notificationVolume = prefs.getInt(KEY_PREV_NOTIFICATION, 0),
            mediaVolume = prefs.getInt(KEY_PREV_MEDIA, 0),
            interruptionFilter = prefs.getInt(KEY_PREV_INTERRUPTION, 0)
        )
    }

    fun clearSavedState() {
        prefs.edit()
            .remove(KEY_PREV_RINGTONE)
            .remove(KEY_PREV_NOTIFICATION)
            .remove(KEY_PREV_MEDIA)
            .remove(KEY_PREV_INTERRUPTION)
            .putBoolean(KEY_HAS_SAVED_STATE, false)
            .apply()
    }

    fun setActiveScheduleId(id: String?) {
        if (id != null) {
            prefs.edit().putString(KEY_ACTIVE_SCHEDULE, id).apply()
        } else {
            prefs.edit().remove(KEY_ACTIVE_SCHEDULE).apply()
        }
    }

    fun getActiveScheduleId(): String? = prefs.getString(KEY_ACTIVE_SCHEDULE, null)

    companion object {
        private const val PREFS_NAME = "silent_mode_prefs"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_HAS_SAVED_STATE = "has_saved_state"
        private const val KEY_PREV_RINGTONE = "prev_ringtone"
        private const val KEY_PREV_NOTIFICATION = "prev_notification"
        private const val KEY_PREV_MEDIA = "prev_media"
        private const val KEY_PREV_INTERRUPTION = "prev_interruption"
        private const val KEY_ACTIVE_SCHEDULE = "active_schedule"
    }
}

data class SavedAudioState(
    val ringtoneVolume: Int,
    val notificationVolume: Int,
    val mediaVolume: Int,
    val interruptionFilter: Int
)
