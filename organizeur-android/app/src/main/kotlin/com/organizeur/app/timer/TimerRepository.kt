package com.organizeur.app.timer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Persistance des minuteurs et de l'état du chronomètre. */
class TimerRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Minuteurs ---

    fun getTimers(): List<CountdownTimer> {
        val json = prefs.getString(KEY_TIMERS, null) ?: return defaultTimers()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { CountdownTimer.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTimers(timers: List<CountdownTimer>) {
        val array = JSONArray()
        timers.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_TIMERS, array.toString()).apply()
    }

    fun addTimer(timer: CountdownTimer) {
        saveTimers(getTimers() + timer)
    }

    fun updateTimer(timer: CountdownTimer) {
        val timers = getTimers().toMutableList()
        val index = timers.indexOfFirst { it.id == timer.id }
        if (index >= 0) {
            timers[index] = timer
            saveTimers(timers)
        } else {
            saveTimers(timers + timer)
        }
    }

    fun deleteTimer(id: String) {
        saveTimers(getTimers().filter { it.id != id })
    }

    fun getTimerById(id: String): CountdownTimer? = getTimers().find { it.id == id }

    /**
     * Minuteurs proposés au premier lancement, tant que rien n'a été enregistré.
     * Leurs identifiants sont fixes : sans cela chaque lecture en produirait de nouveaux
     * et une modification créerait un doublon au lieu de mettre à jour le minuteur.
     */
    private fun defaultTimers(): List<CountdownTimer> = listOf(
        CountdownTimer(id = "default-eggs", label = "Œufs", durationSeconds = 3 * 60),
        CountdownTimer(id = "default-pasta", label = "Pâtes", durationSeconds = 10 * 60)
    )

    // --- Chronomètre ---

    fun getStopwatch(): StopwatchState {
        val json = prefs.getString(KEY_STOPWATCH, null) ?: return StopwatchState()
        return try {
            StopwatchState.fromJson(JSONObject(json))
        } catch (e: Exception) {
            StopwatchState()
        }
    }

    fun saveStopwatch(state: StopwatchState) {
        prefs.edit().putString(KEY_STOPWATCH, state.toJson().toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "timer_prefs"
        private const val KEY_TIMERS = "timers"
        private const val KEY_STOPWATCH = "stopwatch"
    }
}
