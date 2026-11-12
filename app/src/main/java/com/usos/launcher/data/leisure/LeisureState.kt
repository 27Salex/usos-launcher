package com.usos.launcher.data.leisure

/** Estado persistido del ocio. `day` es el día local (epochDay) al que cuenta `spentTodayMin`. */
data class LeisureState(
    val dailyBudgetMin: Int = LeisureLimits.DEFAULT_BUDGET_MIN,
    val spentTodayMin: Int = 0,
    val day: Long = 0L,
    val sessionStartedAt: Long? = null,
    val sessionEndsAt: Long? = null,
)

object LeisureLimits {
    const val DEFAULT_BUDGET_MIN = 60
    const val MIN_BUDGET_MIN = 30
    const val MAX_BUDGET_MIN = 120
    const val BUDGET_STEP_MIN = 15
    const val DEFAULT_WAIT_SECONDS = 20
    const val MIN_WAIT_SECONDS = 5
    const val MAX_WAIT_SECONDS = 60

    /** Duraciones de sesión ofrecidas, en minutos. */
    val durations: List<Int> = listOf(15, 30, 60)
    const val DEFAULT_DURATION_MIN = 30

    fun clampBudget(min: Int): Int {
        val stepped = Math.round(min / BUDGET_STEP_MIN.toFloat()) * BUDGET_STEP_MIN
        return stepped.coerceIn(MIN_BUDGET_MIN, MAX_BUDGET_MIN)
    }

    fun clampWait(seconds: Int): Int = seconds.coerceIn(MIN_WAIT_SECONDS, MAX_WAIT_SECONDS)

    /** Duraciones que caben en el presupuesto restante. */
    fun available(remainingMin: Int): List<Int> = durations.filter { it <= remainingMin }

    /** 30 si cabe; si no, la mayor que quepa. */
    fun defaultDuration(options: List<Int>): Int =
        if (DEFAULT_DURATION_MIN in options) DEFAULT_DURATION_MIN else options.lastOrNull() ?: DEFAULT_DURATION_MIN
}

/** Estado visible del ocio. */
sealed interface LeisureStatus {
    val isOpen: Boolean get() = this is Open

    data class Frozen(val remainingMin: Int) : LeisureStatus {
        val durations: List<Int> get() = LeisureLimits.available(remainingMin)
    }

    data class Thawing(val secondsLeft: Int, val totalSeconds: Int, val durationMin: Int) : LeisureStatus

    data class Open(val minutesLeft: Int, val endsAt: Long) : LeisureStatus

    data object NoBudget : LeisureStatus
}
