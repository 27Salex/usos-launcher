package com.usos.launcher.ui.widgets

import com.usos.launcher.data.PersistJson
import com.usos.launcher.data.info.CalEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnWidgetsTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun oldSavedWidgetsAreAndroid() {
        val old = PersistJson.decodeFromString(HostedWidget.serializer(), """{"id":5,"cols":2,"rows":1,"col":0,"row":0,"extra":1}""")
        assertEquals(WidgetKind.ANDROID, old.widgetKind)
        assertEquals("", old.config)
        val own = PersistJson.decodeFromString(HostedWidget.serializer(), """{"id":-1,"kind":"USOS_MUSIC","config":"bars=accent"}""")
        assertEquals(WidgetKind.USOS_MUSIC, own.widgetKind)
        assertEquals(WidgetKind.ANDROID, HostedWidget(1, kind = "FUTURE").widgetKind)
    }

    @Test fun ownIdsAreNegativeAndUnique() {
        assertEquals(-1, OwnWidgets.nextId(listOf(3, 7)))
        assertEquals(-3, OwnWidgets.nextId(listOf(-1, -2, 4)))
    }

    @Test fun configRoundTrip() {
        var c = OwnWidgets.with("", "bars", "accent")
        c = OwnWidgets.with(c, "tz", "Asia/Tokyo")
        assertEquals("accent", OwnWidgets.get(c, "bars"))
        assertEquals("Asia/Tokyo", OwnWidgets.get(c, "tz"))
        assertEquals("tz=Asia/Tokyo", OwnWidgets.with(c, "bars", null))
    }

    @Test fun barsAreSmoothDeterministicAndQuietWhenPaused() {
        for (i in 0 until 16) {
            assertEquals(MusicBars.height(i, 16, 3.2f, true), MusicBars.height(i, 16, 3.2f, true), 0f)
            assertTrue(MusicBars.height(i, 16, 1f, false) <= 0.25f)
            assertTrue(MusicBars.height(i, 16, 1f, true) in 0f..1f)
            assertTrue(Math.abs(MusicBars.height(i, 16, 1.00f, true) - MusicBars.height(i, 16, 1.03f, true)) < 0.2f)
        }
        assertTrue((0 until 16).any { MusicBars.height(it, 16, 0f, true) != MusicBars.height(it, 16, 0.7f, true) })
    }

    @Test fun monthGridCoversMonthFromFirstDay() {
        val g = CalendarLogic.monthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), g.first().first().date) // 1 nov 2026 es domingo
        assertEquals(LocalDate.of(2026, 11, 30), g.last().first().date)
        assertEquals(6, g.size)
        assertEquals(30, g.flatten().count { it.inMonth })
    }

    @Test fun allDayEventUsesUtcDaysAndMultiDaySpans() {
        val d = LocalDate.of(2026, 11, 13)
        val utc = d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val allDay = CalEvent("x", utc, utc + 86_400_000L, true)
        assertEquals(listOf(d), CalendarLogic.daysOf(allDay, zone))
        val multi = CalEvent("y", ms(d, 22), ms(d.plusDays(1), 2), false)
        assertEquals(listOf(d, d.plusDays(1)), CalendarLogic.daysOf(multi, zone))
    }

    @Test fun agendaSkipsEndedAndLimitsDays() {
        val today = LocalDate.of(2026, 11, 13)
        val now = ms(today, 12)
        val ended = CalEvent("ended", ms(today, 9), ms(today, 10), false)
        val running = CalEvent("running", ms(today, 11), ms(today, 13), false)
        val tomorrow = CalEvent("tomorrow", ms(today.plusDays(1), 9), ms(today.plusDays(1), 10), false)
        val later = CalEvent("later", ms(today.plusDays(2), 9), ms(today.plusDays(2), 10), false)
        val r = CalendarLogic.agenda(listOf(later, tomorrow, ended, running), now, zone, 2)
        assertEquals(listOf("running", "tomorrow"), r.map { it.event.title })
        assertEquals(listOf(today, today.plusDays(1)), r.map { it.date })
    }
}
