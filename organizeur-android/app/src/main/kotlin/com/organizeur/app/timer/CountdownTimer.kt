package com.organizeur.app.timer

import org.json.JSONObject
import java.util.UUID

/**
 * Un minuteur (compte à rebours).
 *
 * Trois états, déduits des champs :
 * - à l'arrêt : endAtMillis == null && pausedRemainingSeconds == null
 * - en cours  : endAtMillis != null (instant de fin absolu, survit à la fermeture de l'appli)
 * - en pause  : pausedRemainingSeconds != null
 */
data class CountdownTimer(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val durationSeconds: Int,
    val endAtMillis: Long? = null,
    val pausedRemainingSeconds: Int? = null,
    val ringtoneUri: String? = null  // null = sonnerie d'alarme par défaut du système
) {
    val isRunning: Boolean get() = endAtMillis != null
    val isPaused: Boolean get() = endAtMillis == null && pausedRemainingSeconds != null
    val isIdle: Boolean get() = endAtMillis == null && pausedRemainingSeconds == null

    /** Secondes restantes à l'instant [now], jamais négatif. */
    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int = when {
        endAtMillis != null -> (((endAtMillis - now) + 999L) / 1000L).coerceAtLeast(0L).toInt()
        pausedRemainingSeconds != null -> pausedRemainingSeconds
        else -> durationSeconds
    }

    /** Remet le minuteur à l'arrêt, prêt à repartir sur sa durée complète. */
    fun stopped(): CountdownTimer = copy(endAtMillis = null, pausedRemainingSeconds = null)

    /** Démarre (ou reprend) le minuteur à partir de [now]. */
    fun started(now: Long = System.currentTimeMillis()): CountdownTimer {
        val seconds = (pausedRemainingSeconds ?: durationSeconds).coerceAtLeast(1)
        return copy(endAtMillis = now + seconds * 1000L, pausedRemainingSeconds = null)
    }

    /** Met en pause en figeant le temps restant. */
    fun paused(now: Long = System.currentTimeMillis()): CountdownTimer =
        copy(endAtMillis = null, pausedRemainingSeconds = remainingSeconds(now).coerceAtLeast(1))

    /** Ajoute [seconds] au temps restant (minuteur en cours ou en pause). */
    fun extendedBy(seconds: Int, now: Long = System.currentTimeMillis()): CountdownTimer = when {
        endAtMillis != null -> copy(endAtMillis = maxOf(endAtMillis, now) + seconds * 1000L)
        pausedRemainingSeconds != null -> copy(pausedRemainingSeconds = pausedRemainingSeconds + seconds)
        else -> this
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("durationSeconds", durationSeconds)
        if (endAtMillis != null) put("endAtMillis", endAtMillis)
        if (pausedRemainingSeconds != null) put("pausedRemainingSeconds", pausedRemainingSeconds)
        if (ringtoneUri != null) put("ringtoneUri", ringtoneUri)
    }

    companion object {
        fun fromJson(json: JSONObject): CountdownTimer = CountdownTimer(
            id = json.getString("id"),
            label = json.getString("label"),
            durationSeconds = json.getInt("durationSeconds"),
            endAtMillis = if (json.has("endAtMillis")) json.getLong("endAtMillis") else null,
            pausedRemainingSeconds = if (json.has("pausedRemainingSeconds"))
                json.getInt("pausedRemainingSeconds") else null,
            ringtoneUri = if (json.has("ringtoneUri")) json.getString("ringtoneUri") else null
        )
    }
}

/** Formate une durée en secondes : "1:05:09" au delà d'une heure, "05:09" sinon. */
fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
