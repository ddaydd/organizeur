package com.organizeur.app.silentmode

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class DndMode {
    TOTAL_SILENCE,
    ALARMS_ONLY,
    PRIORITY_ONLY
}

data class SilentModeSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val days: Set<Int>,           // Calendar.MONDAY..SUNDAY (2..7, 1=SUNDAY)
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val dndMode: DndMode = DndMode.TOTAL_SILENCE,
    val ringtoneVolume: Int? = null,
    val notificationVolume: Int? = null,
    val mediaVolume: Int? = null
) {
    fun hasEndTime(): Boolean = endHour != null && endMinute != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("days", JSONArray(days.toList()))
        put("startHour", startHour)
        put("startMinute", startMinute)
        put("endHour", endHour ?: JSONObject.NULL)
        put("endMinute", endMinute ?: JSONObject.NULL)
        put("dndMode", dndMode.name)
        put("ringtoneVolume", ringtoneVolume ?: JSONObject.NULL)
        put("notificationVolume", notificationVolume ?: JSONObject.NULL)
        put("mediaVolume", mediaVolume ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): SilentModeSchedule {
            val daysArray = json.getJSONArray("days")
            val days = mutableSetOf<Int>()
            for (i in 0 until daysArray.length()) {
                days.add(daysArray.getInt(i))
            }
            return SilentModeSchedule(
                id = json.getString("id"),
                name = json.getString("name"),
                enabled = json.getBoolean("enabled"),
                days = days,
                startHour = json.getInt("startHour"),
                startMinute = json.getInt("startMinute"),
                endHour = if (json.has("endHour") && !json.isNull("endHour")) json.getInt("endHour") else null,
                endMinute = if (json.has("endMinute") && !json.isNull("endMinute")) json.getInt("endMinute") else null,
                dndMode = DndMode.valueOf(json.getString("dndMode")),
                ringtoneVolume = if (json.isNull("ringtoneVolume")) null else json.getInt("ringtoneVolume"),
                notificationVolume = if (json.isNull("notificationVolume")) null else json.getInt("notificationVolume"),
                mediaVolume = if (json.isNull("mediaVolume")) null else json.getInt("mediaVolume")
            )
        }
    }
}
