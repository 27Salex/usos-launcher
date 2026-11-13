package com.usos.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class DateTextTest {
    private val es = Locale.forLanguageTag("es-ES")
    private val en = Locale.forLanguageTag("en-US")
    private val thursday = LocalDate.of(2026, 11, 12)

    @Test fun formatsSpanish() {
        assertEquals("JUEVES 12 NOVIEMBRE", DateText.format(thursday, es))
        assertEquals("MIÉRCOLES 1 ENERO", DateText.format(LocalDate.of(2025, 1, 1), es))
    }

    @Test fun formatsEnglish() {
        assertEquals("THURSDAY NOVEMBER 12", DateText.format(thursday, en, "%1\$s %3\$s %2\$d"))
    }

    @Test fun weekLettersSpanishAndEnglish() {
        val week = DateText.week(thursday, DateText.firstDayOfWeek(es))
        assertEquals("LMXJVSD", week.joinToString("") { DateText.weekLetter(it, es) })
        val weekEn = DateText.week(thursday, DateText.firstDayOfWeek(en))
        assertEquals("SMTWTFS", weekEn.joinToString("") { DateText.weekLetter(it, en) })
    }

    @Test fun firstDayFollowsLocale() {
        assertEquals(DayOfWeek.MONDAY, DateText.firstDayOfWeek(es))
        assertEquals(DayOfWeek.SUNDAY, DateText.firstDayOfWeek(en))
    }

    @Test fun weekContainsDateAndStartsOnFirstDay() {
        val mon = DateText.week(thursday, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 9), mon.first())
        assertEquals(LocalDate.of(2026, 11, 15), mon.last())
        val sun = DateText.week(LocalDate.of(2026, 11, 15), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 15), sun.first())
        assertEquals(7, sun.size)
    }
}
