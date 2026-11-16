package com.usos.launcher.ui.widgets

import com.usos.launcher.data.info.CalEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnCalendarWeekTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun weekStartsOnFirstDay() {
        val wed = LocalDate.of(2026, 11, 18)
        val mon = CalendarLogic.weekOf(wed, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 16), mon.first())
        assertEquals(LocalDate.of(2026, 11, 22), mon.last())
        val sun = CalendarLogic.weekOf(wed, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 15), sun.first())
        assertEquals(7, sun.size)
        assertEquals(LocalDate.of(2026, 11, 16), CalendarLogic.weekOf(LocalDate.of(2026, 11, 16), DayOfWeek.MONDAY).first())
    }

    @Test fun eventsOnDayAllDayFirstAndRows() {
        val d = LocalDate.of(2026, 11, 18)
        val late = CalEvent("b", ms(d, 15), ms(d, 16), false)
        val early = CalEvent("a", ms(d, 9), ms(d, 10), false)
        val allDay = CalEvent("c", d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), d.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), true)
        val other = CalEvent("x", ms(d.plusDays(1), 9), ms(d.plusDays(1), 10), false)
        val on = CalendarLogic.eventsOn(listOf(late, other, early, allDay), d, zone)
        assertEquals(listOf("c", "a", "b"), on.map { it.title })
        assertTrue(CalendarLogic.isOngoing(early, ms(d, 9, 30)))
        assertFalse(CalendarLogic.isOngoing(early, ms(d, 10)))
        assertFalse(CalendarLogic.isOngoing(allDay, ms(d, 12)))
        val rows = CalendarLogic.agendaRows(CalendarLogic.agenda(listOf(early, other), ms(d, 8), zone, 3))
        assertEquals(4, rows.size)
        assertTrue(rows[0] is CalendarLogic.AgendaRow.Header && rows[2] is CalendarLogic.AgendaRow.Header)
    }

    @Test fun modeParsing() {
        assertEquals(CalMode.AUTO, CalMode.fromKey(null))
        assertEquals(CalMode.AUTO, CalMode.fromKey("zzz"))
        assertEquals(CalMode.WEEK, CalMode.fromKey("week"))
        assertEquals("agenda", CalMode.AGENDA.key)
        assertEquals(null, CalMode.AUTO.key)
        assertFalse(OwnWidgets.bordered("border=off"))
        assertTrue(OwnWidgets.bordered(""))
    }
}
