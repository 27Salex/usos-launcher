package com.usos.launcher.data.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class UsageEventKind { RESUMED, PAUSED, SHUTDOWN }

/** Evento de uso ya reducido a lo que importa (sin Android). `className` puede ir vacio. */
data class UsageEventRecord(
    val packageName: String,
    val className: String,
    val kind: UsageEventKind,
    val timeMs: Long,
)

data class DayUsage(val foregroundMs: Long = 0, val opens: Int = 0)

/**
 * Agregacion pura de UsageEvents a tiempo en primer plano y aperturas por dia local.
 * El primer plano de un paquete empieza cuando una de sus actividades pasa a RESUMED y termina
 * cuando la ultima activa pasa a PAUSED, como hace Bienestar digital. Las sesiones se cortan en
 * cada medianoche local.
 */
object UsageAggregator {
    fun aggregate(
        events: List<UsageEventRecord>,
        windowStart: Long,
        windowEnd: Long,
        zone: ZoneId,
    ): Map<String, Map<LocalDate, DayUsage>> {
        val ms = HashMap<String, HashMap<LocalDate, Long>>()
        val opens = HashMap<String, HashMap<LocalDate, Int>>()
        val active = HashMap<String, MutableSet<String>>()
        val sessionStart = HashMap<String, Long>()
        val seen = HashSet<String>()

        fun dateOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

        fun addInterval(pkg: String, from: Long, to: Long) {
            var cur = maxOf(from, windowStart)
            val end = minOf(to, windowEnd)
            val perDay = ms.getOrPut(pkg) { HashMap() }
            while (cur < end) {
                val date = dateOf(cur)
                val midnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val seg = minOf(end, midnight)
                perDay.merge(date, seg - cur, Long::plus)
                cur = seg
            }
        }

        fun closeAll(at: Long) {
            for ((pkg, set) in active) {
                if (set.isNotEmpty()) {
                    addInterval(pkg, sessionStart.getValue(pkg), at)
                    set.clear()
                }
            }
        }

        for (e in events.sortedBy { it.timeMs }) {
            if (e.timeMs > windowEnd) break
            val pkg = e.packageName
            when (e.kind) {
                UsageEventKind.RESUMED -> {
                    seen += pkg
                    val set = active.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) {
                        sessionStart[pkg] = e.timeMs
                        if (e.timeMs >= windowStart) {
                            opens.getOrPut(pkg) { HashMap() }.merge(dateOf(e.timeMs), 1, Int::plus)
                        }
                    }
                    set += e.className
                }
                UsageEventKind.PAUSED -> {
                    val set = active[pkg]
                    if (set != null && set.isNotEmpty()) {
                        set -= e.className
                        if (set.isEmpty()) addInterval(pkg, sessionStart.getValue(pkg), e.timeMs)
                    } else if (pkg !in seen) {
                        // Pausa sin reanudacion previa: la app venia abierta desde antes de la ventana.
                        addInterval(pkg, windowStart, e.timeMs)
                    }
                    seen += pkg
                }
                UsageEventKind.SHUTDOWN -> closeAll(e.timeMs)
            }
        }
        closeAll(windowEnd)

        return (ms.keys + opens.keys).associateWith { pkg ->
            val days = ms[pkg].orEmpty().keys + opens[pkg].orEmpty().keys
            days.associateWith { d -> DayUsage(ms[pkg]?.get(d) ?: 0L, opens[pkg]?.get(d) ?: 0) }
        }
    }
}

/** Resumen de una app para la ficha: 7 barras (la ultima es hoy), minutos de hoy y semana, aperturas en 7 dias. */
data class UsageSummary(
    val barsMs: List<Long>,
    val todayMin: Int,
    val weekMin: Int,
    val opens7: Int,
) {
    companion object {
        const val DAYS = 7

        /** La semana es la natural (desde el lunes); las barras y las aperturas, los ultimos 7 dias. */
        fun build(days: Map<LocalDate, DayUsage>, today: LocalDate): UsageSummary {
            val range = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
            val bars = range.map { days[it]?.foregroundMs ?: 0L }
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weekMs = days.filterKeys { !it.isBefore(monday) && !it.isAfter(today) }.values.sumOf { it.foregroundMs }
            return UsageSummary(
                barsMs = bars,
                todayMin = (bars.last() / 60_000).toInt(),
                weekMin = (weekMs / 60_000).toInt(),
                opens7 = range.sumOf { days[it]?.opens ?: 0 },
            )
        }

        /** "12 MIN" o "1 H 05". */
        fun format(min: Int): String =
            if (min < 60) "$min MIN" else "${min / 60} H ${"%02d".format(min % 60)}"
    }
}
