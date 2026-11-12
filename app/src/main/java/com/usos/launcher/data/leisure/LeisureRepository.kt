package com.usos.launcher.data.leisure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Fuente de verdad del ocio, sobre DataStore. Se usa desde el hilo principal.
 * [status] es null hasta que [load] termina.
 */
class LeisureRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    private val budgetKey = intPreferencesKey("leisure_budget_min")
    private val spentKey = intPreferencesKey("leisure_spent_min")
    private val dayKey = longPreferencesKey("leisure_day")
    private val startedKey = longPreferencesKey("leisure_session_started_at")
    private val endsKey = longPreferencesKey("leisure_session_ends_at")
    private val waitKey = intPreferencesKey("wait_seconds")

    private var machine: LeisureMachine? = null
    var waitSeconds: Int = LeisureLimits.DEFAULT_WAIT_SECONDS
        private set

    private val _status = MutableStateFlow<LeisureStatus?>(null)
    val status: StateFlow<LeisureStatus?> = _status

    private val writes = Channel<Pair<LeisureState, Int>>(Channel.CONFLATED)

    init {
        scope.launch {
            for ((state, wait) in writes) {
                dataStore.edit { p ->
                    p[budgetKey] = state.dailyBudgetMin
                    p[spentKey] = state.spentTodayMin
                    p[dayKey] = state.day
                    val started = state.sessionStartedAt
                    val ends = state.sessionEndsAt
                    if (started != null) p[startedKey] = started else p.remove(startedKey)
                    if (ends != null) p[endsKey] = ends else p.remove(endsKey)
                    p[waitKey] = wait
                }
            }
        }
    }

    suspend fun load() {
        val p = dataStore.data.first()
        val state = LeisureState(
            dailyBudgetMin = LeisureLimits.clampBudget(p[budgetKey] ?: LeisureLimits.DEFAULT_BUDGET_MIN),
            spentTodayMin = p[spentKey] ?: 0,
            day = p[dayKey] ?: 0L,
            sessionStartedAt = p[startedKey],
            sessionEndsAt = p[endsKey],
        )
        waitSeconds = LeisureLimits.clampWait(p[waitKey] ?: LeisureLimits.DEFAULT_WAIT_SECONDS)
        machine = LeisureMachine(clock, state)
        tick() // cierra y contabiliza una sesion ya vencida (p. ej. tras reiniciar)
    }

    /** Aplica transiciones pendientes y publica el estado. */
    fun tick(): LeisureStatus? = commit { it.tick() }

    /** El ocio esta abierto ahora mismo (comprobacion fresca, no la del ultimo tick). */
    fun isOpen(): Boolean = tick()?.isOpen == true

    fun startThaw(durationMin: Int): Boolean {
        val m = machine ?: return false
        var ok = false
        commit { ok = m.requestThaw(durationMin, waitSeconds); it.tick() }
        return ok
    }

    fun cancelThaw() { commit { it.cancelThaw(); it.tick() } }

    fun freeze() { commit { it.freeze() } }

    fun setBudget(min: Int) { commit { it.setBudget(min); it.tick() } }

    fun setWaitSeconds(seconds: Int) {
        waitSeconds = LeisureLimits.clampWait(seconds)
        commit { it.tick() }
    }

    private fun commit(block: (LeisureMachine) -> LeisureStatus): LeisureStatus? {
        val m = machine ?: return null
        val before = m.state
        val status = block(m)
        if (m.state != before || waitChangedSinceWrite()) writes.trySend(m.state to waitSeconds)
        lastWrittenWait = waitSeconds
        _status.value = status
        return status
    }

    private var lastWrittenWait = waitSeconds
    private fun waitChangedSinceWrite() = lastWrittenWait != waitSeconds
}
