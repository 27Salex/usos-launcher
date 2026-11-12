package com.usos.launcher.data.leisure

import java.time.Clock
import java.time.Instant

/**
 * Maquina de estados pura del ocio (sin Android). Todo el tiempo sale del [Clock] inyectado.
 * Hay que llamar a [tick] periodicamente; el resto de operaciones hacen tick antes de actuar.
 *
 * Estados: Congelado, Descongelando (en memoria, no se persiste), Abierto y Sin presupuesto.
 */
class LeisureMachine(private val clock: Clock, initial: LeisureState = LeisureState()) {
    private class Thaw(val durationMin: Int, val startedAt: Long, val waitMs: Long)

    var state: LeisureState = initial
        private set
    private var thaw: Thaw? = null

    private fun dayOf(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate().toEpochDay()

    /** Aplica las transiciones pendientes (fin de espera, fin de sesion, cambio de dia) y devuelve el estado. */
    fun tick(): LeisureStatus {
        val now = clock.millis()
        val ends = state.sessionEndsAt
        if (state.sessionStartedAt != null && ends != null && now >= ends) closeSession(now)
        rollDay(now)
        thaw?.let {
            if (now >= it.startedAt + it.waitMs) {
                thaw = null
                if (remaining() >= it.durationMin) openSession(it.durationMin, now)
            }
        }
        return status(now)
    }

    private fun status(now: Long): LeisureStatus {
        thaw?.let {
            val leftMs = (it.startedAt + it.waitMs - now).coerceAtLeast(0)
            return LeisureStatus.Thawing(
                secondsLeft = ceilDiv(leftMs, 1000).toInt(),
                totalSeconds = (it.waitMs / 1000).toInt(),
                durationMin = it.durationMin,
            )
        }
        val ends = state.sessionEndsAt
        if (state.sessionStartedAt != null && ends != null && now < ends) {
            return LeisureStatus.Open(ceilDiv(ends - now, 60_000).toInt().coerceAtLeast(1), ends)
        }
        val remaining = remaining()
        return if (LeisureLimits.available(remaining).isEmpty()) LeisureStatus.NoBudget
        else LeisureStatus.Frozen(remaining)
    }

    private fun remaining(): Int = (state.dailyBudgetMin - state.spentTodayMin).coerceAtLeast(0)

    /** Congelado -> Descongelando, solo si la duracion cabe en el presupuesto. */
    fun requestThaw(durationMin: Int, waitSeconds: Int): Boolean {
        val status = tick()
        if (status !is LeisureStatus.Frozen || durationMin !in status.durations) return false
        thaw = Thaw(durationMin, clock.millis(), LeisureLimits.clampWait(waitSeconds) * 1000L)
        return true
    }

    /** Descongelando -> Congelado (Cancelar o salir del launcher). Sin efectos. */
    fun cancelThaw() {
        thaw = null
    }

    /** Abierto -> Congelado: suma los minutos transcurridos, redondeados hacia arriba. */
    fun freeze(): LeisureStatus {
        tick()
        if (state.sessionStartedAt != null) closeSession(clock.millis())
        return tick()
    }

    fun setBudget(min: Int) {
        state = state.copy(dailyBudgetMin = LeisureLimits.clampBudget(min))
    }

    private fun openSession(durationMin: Int, now: Long) {
        state = state.copy(sessionStartedAt = now, sessionEndsAt = now + durationMin * 60_000L)
    }

    /** Cierra la sesion: cuenta contra el dia en que empezo y nunca mas alla de `sessionEndsAt`. */
    private fun closeSession(now: Long) {
        val started = state.sessionStartedAt ?: return
        val end = minOf(now, state.sessionEndsAt ?: now)
        val minutes = ceilDiv((end - started).coerceAtLeast(0), 60_000).toInt()
        val sessionDay = dayOf(started)
        val base = if (state.day == sessionDay) state else state.copy(day = sessionDay, spentTodayMin = 0)
        state = base.copy(
            spentTodayMin = base.spentTodayMin + minutes,
            sessionStartedAt = null,
            sessionEndsAt = null,
        )
        rollDay(now)
    }

    /** Cambio de dia: repone el presupuesto (no mientras hay una sesion abierta). */
    private fun rollDay(now: Long) {
        if (state.sessionStartedAt != null) return
        val today = dayOf(now)
        if (state.day != today) state = state.copy(day = today, spentTodayMin = 0)
    }

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
}
