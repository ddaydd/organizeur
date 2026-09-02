package com.organizeur.app.alarm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AlarmRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAlarms(): List<Alarm> {
        val json = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length())
                .map { Alarm.fromJson(array.getJSONObject(it)) }
                .sortedWith(compareBy({ it.hour }, { it.minute }, { it.name }))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAlarms(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    fun addAlarm(alarm: Alarm) {
        val alarms = getAlarms().toMutableList()
        alarms.add(alarm)
        saveAlarms(alarms)
    }

    fun updateAlarm(alarm: Alarm) {
        val alarms = getAlarms().toMutableList()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            alarms[index] = alarm
            saveAlarms(alarms)
        }
    }

    fun deleteAlarm(id: String) {
        val alarms = getAlarms().filter { it.id != id }
        saveAlarms(alarms)
    }

    fun getAlarmById(id: String): Alarm? {
        return getAlarms().find { it.id == id }
    }

    // --- Rappels (snooze) en attente : alarmId -> timestamp de déclenchement ---

    /** Rappels encore à venir, les entrées échues étant purgées au passage. */
    fun getSnoozes(): Map<String, Long> {
        val json = prefs.getString(KEY_SNOOZES, null) ?: return emptyMap()
        val stored = try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getLong(it) }
        } catch (e: Exception) {
            return emptyMap()
        }
        val now = System.currentTimeMillis()
        val pending = stored.filterValues { it > now }
        if (pending.size != stored.size) saveSnoozes(pending)
        return pending
    }

    fun getSnoozeAt(alarmId: String): Long? = getSnoozes()[alarmId]

    fun setSnooze(alarmId: String, triggerAt: Long) {
        saveSnoozes(getSnoozes() + (alarmId to triggerAt))
    }

    fun clearSnooze(alarmId: String) {
        val snoozes = getSnoozes()
        if (snoozes.containsKey(alarmId)) saveSnoozes(snoozes - alarmId)
    }

    private fun saveSnoozes(snoozes: Map<String, Long>) {
        val obj = JSONObject()
        snoozes.forEach { (id, at) -> obj.put(id, at) }
        prefs.edit().putString(KEY_SNOOZES, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "alarm_prefs"
        private const val KEY_ALARMS = "alarms"
        private const val KEY_SNOOZES = "snoozes"
    }
}
