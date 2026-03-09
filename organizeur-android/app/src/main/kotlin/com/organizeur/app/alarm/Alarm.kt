package com.organizeur.app.alarm

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,           // Calendar.MONDAY..SUNDAY (2..7, 1=SUNDAY)
    val snoozeDurationMinutes: Int = 10,
    val ringtoneUri: String? = null,  // null = sonnerie par défaut du système
    val recurring: Boolean = true,
    val deactivatesSilentMode: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        put("days", JSONArray(days.toList()))
        put("snoozeDurationMinutes", snoozeDurationMinutes)
        if (ringtoneUri != null) put("ringtoneUri", ringtoneUri)
        put("recurring", recurring)
        put("deactivatesSilentMode", deactivatesSilentMode)
    }

    companion object {
        fun fromJson(json: JSONObject): Alarm {
            val daysArray = json.getJSONArray("days")
            val days = mutableSetOf<Int>()
            for (i in 0 until daysArray.length()) {
                days.add(daysArray.getInt(i))
            }
            return Alarm(
                id = json.getString("id"),
                name = json.getString("name"),
                enabled = json.getBoolean("enabled"),
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
                days = days,
                snoozeDurationMinutes = json.optInt("snoozeDurationMinutes", 10),
                ringtoneUri = if (json.has("ringtoneUri")) json.getString("ringtoneUri") else null,
                recurring = json.optBoolean("recurring", true),
                deactivatesSilentMode = json.optBoolean("deactivatesSilentMode", true)
            )
        }
    }
}
