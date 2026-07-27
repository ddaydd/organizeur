package com.organizeur.app.timer

import android.content.Context

/**
 * Point d'entrée unique pour piloter minuteurs et chronomètre : garde en cohérence
 * la persistance, la planification AlarmManager et les notifications.
 *
 * Utilisé aussi bien par l'UI Compose que par [TimerReceiver] (actions des notifications).
 */
class TimerController(context: Context) {

    private val repository = TimerRepository(context)
    private val scheduler = TimerScheduler(context)
    private val notifier = TimerNotifier(context)

    // --- Minuteurs ---

    fun getTimers(): List<CountdownTimer> = repository.getTimers()

    fun getTimerById(id: String): CountdownTimer? = repository.getTimerById(id)

    fun start(timer: CountdownTimer): CountdownTimer {
        val started = timer.started()
        repository.updateTimer(started)
        scheduler.schedule(started)
        notifier.showTimer(started)
        return started
    }

    fun pause(timer: CountdownTimer): CountdownTimer {
        val paused = timer.paused()
        repository.updateTimer(paused)
        scheduler.cancel(timer.id)
        notifier.showTimer(paused)
        return paused
    }

    fun stop(timer: CountdownTimer): CountdownTimer {
        val stopped = timer.stopped()
        repository.updateTimer(stopped)
        scheduler.cancel(timer.id)
        notifier.cancelTimer(timer.id)
        return stopped
    }

    fun addMinute(timer: CountdownTimer): CountdownTimer {
        val extended = timer.extendedBy(60)
        repository.updateTimer(extended)
        if (extended.isRunning) scheduler.schedule(extended)
        notifier.showTimer(extended)
        return extended
    }

    /** Le minuteur a sonné : il repart de zéro et sa notification de décompte disparaît. */
    fun expire(timerId: String): CountdownTimer? {
        val timer = repository.getTimerById(timerId) ?: return null
        repository.updateTimer(timer.stopped())
        notifier.cancelTimer(timerId)
        return timer
    }

    /** Création ou modification. Un minuteur en cours est arrêté avant d'être modifié. */
    fun save(timer: CountdownTimer) {
        val existing = repository.getTimerById(timer.id)
        if (existing != null && !existing.isIdle) {
            scheduler.cancel(timer.id)
            notifier.cancelTimer(timer.id)
        }
        repository.updateTimer(timer.stopped())
    }

    fun delete(timer: CountdownTimer) {
        scheduler.cancel(timer.id)
        notifier.cancelTimer(timer.id)
        repository.deleteTimer(timer.id)
    }

    // --- Chronomètre ---

    fun getStopwatch(): StopwatchState = repository.getStopwatch()

    fun startStopwatch(): StopwatchState = saveStopwatch(getStopwatch().started())

    fun pauseStopwatch(): StopwatchState = saveStopwatch(getStopwatch().paused())

    fun lapStopwatch(): StopwatchState = saveStopwatch(getStopwatch().lapped())

    fun resetStopwatch(): StopwatchState {
        val state = StopwatchState()
        repository.saveStopwatch(state)
        notifier.cancelStopwatch()
        return state
    }

    private fun saveStopwatch(state: StopwatchState): StopwatchState {
        repository.saveStopwatch(state)
        if (state.isReset) notifier.cancelStopwatch() else notifier.showStopwatch(state)
        return state
    }

    // --- Divers ---

    /** À appeler au démarrage de l'appli et au redémarrage du téléphone. */
    fun rescheduleAll() = scheduler.rescheduleAll(repository, notifier)

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()
}
