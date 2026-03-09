package com.organizeur.app.alarm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class AlarmRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAlarms(): List<Alarm> {
        val json = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { Alarm.fromJson(array.getJSONObject(it)) }
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

    companion object {
        private const val PREFS_NAME = "alarm_prefs"
        private const val KEY_ALARMS = "alarms"
    }
}
