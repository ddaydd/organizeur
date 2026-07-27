package com.organizeur.app.timer

import org.json.JSONArray
import org.json.JSONObject

/**
 * État du chronomètre (compte à l'endroit).
 *
 * Le temps écoulé n'est jamais compté par un timer : on stocke l'instant de départ
 * et le cumul des périodes précédentes, l'affichage recalcule à chaque frame.
 */
data class StopwatchState(
    /** Instant de démarrage de la période en cours, null si à l'arrêt. */
    val startedAtMillis: Long? = null,
    /** Cumul des périodes déjà écoulées (avant la pause en cours). */
    val accumulatedMillis: Long = 0L,
    /** Temps total écoulé au moment de chaque tour, du plus ancien au plus récent. */
    val laps: List<Long> = emptyList()
) {
    val isRunning: Boolean get() = startedAtMillis != null
    val isReset: Boolean get() = startedAtMillis == null && accumulatedMillis == 0L

    fun elapsedMillis(now: Long = System.currentTimeMillis()): Long =
        accumulatedMillis + if (startedAtMillis != null) (now - startedAtMillis).coerceAtLeast(0L) else 0L

    fun started(now: Long = System.currentTimeMillis()): StopwatchState =
        if (isRunning) this else copy(startedAtMillis = now)

    fun paused(now: Long = System.currentTimeMillis()): StopwatchState =
        if (!isRunning) this else StopwatchState(null, elapsedMillis(now), laps)

    fun lapped(now: Long = System.currentTimeMillis()): StopwatchState =
        if (!isRunning) this else copy(laps = laps + elapsedMillis(now))

    fun toJson(): JSONObject = JSONObject().apply {
        if (startedAtMillis != null) put("startedAtMillis", startedAtMillis)
        put("accumulatedMillis", accumulatedMillis)
        put("laps", JSONArray(laps))
    }

    companion object {
        fun fromJson(json: JSONObject): StopwatchState {
            val lapsArray = json.optJSONArray("laps")
            val laps = mutableListOf<Long>()
            if (lapsArray != null) {
                for (i in 0 until lapsArray.length()) laps.add(lapsArray.getLong(i))
            }
            return StopwatchState(
                startedAtMillis = if (json.has("startedAtMillis")) json.getLong("startedAtMillis") else null,
                accumulatedMillis = json.optLong("accumulatedMillis", 0L),
                laps = laps
            )
        }
    }
}

/** Formate un temps de chronomètre : "1:05:09,3" au delà d'une heure, "05:09,32" sinon. */
fun formatStopwatch(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d,%d".format(hours, minutes, seconds, (millis % 1000) / 100)
    } else {
        "%02d:%02d,%02d".format(minutes, seconds, (millis % 1000) / 10)
    }
}
