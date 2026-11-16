package com.usos.launcher.ui.widgets

import com.usos.launcher.data.info.CalEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * Alturas (0..1) de las barras de la pieza de música. Sin audio real (no hay micrófono ni Visualizer):
 * dos senos lentos por barra con fase y frecuencia pseudoaleatorias fijas por índice, así que son suaves y
 * deterministas. En pausa, un perfil bajo y quieto.
 */
object MusicBars {
    private fun hash(i: Int, salt: Int): Float {
        var h = (i * 73856093) xor (salt * 19349663)
        h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
        return (h and 0xFFFF) / 65535f
    }

    fun height(i: Int, n: Int, tSec: Float, playing: Boolean): Float {
        if (!playing) return 0.1f + 0.14f * hash(i, 1)
        val f1 = 0.5f + 1.2f * hash(i, 2)
        val f2 = 1.1f + 1.8f * hash(i, 3)
        val twoPi = 2f * PI.toFloat()
        val a = 0.5f + 0.5f * sin(twoPi * f1 * tSec + twoPi * hash(i, 4))
        val b = 0.5f + 0.5f * sin(twoPi * f2 * tSec + twoPi * hash(i, 5))
        val envelope = 0.65f + 0.35f * sin(PI.toFloat() * (i + 0.5f) / n.coerceAtLeast(1))
        return (0.16f + 0.84f * (0.55f * a + 0.45f * b) * envelope).coerceIn(0f, 1f)
    }
}

/** Lógica pura del calendario: rejilla del mes, días de un evento y agenda. */
object CalendarLogic {
    data class MonthCell(val date: LocalDate, val inMonth: Boolean)
    data class AgendaItem(val date: LocalDate, val event: CalEvent)

    /** Semanas completas (de 7 días, empezando en [first]) que cubren el mes. */
    fun monthGrid(month: YearMonth, first: DayOfWeek): List<List<MonthCell>> {
        val d1 = month.atDay(1)
        val start = d1.minusDays(((d1.dayOfWeek.value - first.value + 7) % 7).toLong())
        val weeks = ((ChronoUnit.DAYS.between(start, month.atEndOfMonth()) + 1 + 6) / 7).toInt()
        return List(weeks) { w ->
            List(7) { d -> start.plusDays((w * 7 + d).toLong()).let { MonthCell(it, YearMonth.from(it) == month) } }
        }
    }

    /** Días (fecha local) que ocupa un evento; los de todo el día vienen en UTC. */
    fun daysOf(e: CalEvent, zone: ZoneId): List<LocalDate> {
        val z = if (e.allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(e.beginMs).atZone(z).toLocalDate()
        val last = Instant.ofEpochMilli(maxOf(e.beginMs, e.endMs - 1)).atZone(z).toLocalDate()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.take(62).toList()
    }

    fun eventDays(events: List<CalEvent>, zone: ZoneId): Set<LocalDate> = events.flatMap { daysOf(it, zone) }.toSet()

    /**
     * Agenda desde ahora durante [days] días (hoy incluido): los que no han terminado, un elemento por evento
     * en su primer día visible (hoy si ya empezó). Primero los de todo el día de cada fecha y luego por hora.
     */
    fun agenda(events: List<CalEvent>, nowMs: Long, zone: ZoneId, days: Int, start: LocalDate? = null): List<AgendaItem> {
        val today = start ?: Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val limit = today.plusDays(days.toLong())
        return events.mapNotNull { e ->
            if (!e.allDay && e.endMs <= nowMs) return@mapNotNull null
            val day = daysOf(e, zone).firstOrNull { !it.isBefore(today) } ?: return@mapNotNull null
            if (!day.isBefore(limit)) null else AgendaItem(day, e)
        }.sortedWith(compareBy({ it.date }, { !it.event.allDay }, { it.event.beginMs }))
    }

    /** Fila de la agenda agrupada: cabecera de día o evento. */
    sealed interface AgendaRow {
        data class Header(val date: LocalDate) : AgendaRow
        data class Item(val event: CalEvent) : AgendaRow
    }

    /** Agrupa la agenda (ya ordenada por fecha) con una cabecera por día. */
    fun agendaRows(items: List<AgendaItem>): List<AgendaRow> {
        val out = ArrayList<AgendaRow>()
        var last: LocalDate? = null
        for (it in items) {
            if (it.date != last) { out.add(AgendaRow.Header(it.date)); last = it.date }
            out.add(AgendaRow.Item(it.event))
        }
        return out
    }

    /** Eventos que ocupan [date]: primero los de todo el día y luego por hora de inicio. */
    fun eventsOn(events: List<CalEvent>, date: LocalDate, zone: ZoneId): List<CalEvent> =
        events.filter { date in daysOf(it, zone) }.sortedWith(compareBy({ !it.allDay }, { it.beginMs }))

    /** Evento en curso (no de todo el día). */
    fun isOngoing(e: CalEvent, nowMs: Long): Boolean = !e.allDay && e.beginMs <= nowMs && nowMs < e.endMs

    /**
     * Días que muestra el calendario desplazado [offset] páginas (semana en WEEK, mes en MONTH, 7 días desde hoy en
     * AGENDA): [inicio, fin) para la consulta.
     */
    fun window(mode: CalMode, today: LocalDate, offset: Int, first: DayOfWeek): Pair<LocalDate, LocalDate> = when (mode) {
        CalMode.MONTH -> {
            val grid = monthGrid(YearMonth.from(today).plusMonths(offset.toLong()), first)
            grid.first().first().date to grid.last().last().date.plusDays(1)
        }
        CalMode.WEEK -> weekOf(today.plusWeeks(offset.toLong()), first).let { it.first() to it.last().plusDays(1) }
        else -> today.plusDays(7L * offset) to today.plusDays(7L * offset + 7)
    }

    /** Semana (7 días) que contiene [date], empezando en [first]. */
    fun weekOf(date: LocalDate, first: DayOfWeek): List<LocalDate> {
        val start = date.minusDays(((date.dayOfWeek.value - first.value + 7) % 7).toLong())
        return List(7) { start.plusDays(it.toLong()) }
    }
}

/** Modo del calendario (config `mode`); sin clave = automático según el alto. */
enum class CalMode {
    AUTO, WEEK, MONTH, AGENDA;

    val key: String? get() = if (this == AUTO) null else name.lowercase()

    companion object {
        fun fromKey(v: String?): CalMode = entries.firstOrNull { it != AUTO && it.name.equals(v, ignoreCase = true) } ?: AUTO
    }
}
