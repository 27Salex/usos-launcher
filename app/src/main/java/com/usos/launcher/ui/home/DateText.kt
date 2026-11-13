package com.usos.launcher.ui.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Fecha y semana según el idioma activo (nombres de java.time; el orden lo da el recurso `clock_date_format`). */
object DateText {
    /** Orden por defecto «JUEVES 12 NOVIEMBRE»; %1 = día de la semana, %2 = día del mes, %3 = mes. */
    const val DEFAULT_PATTERN = "%1\$s %2\$d %3\$s"

    /** «JUEVES 12 NOVIEMBRE» / «THURSDAY NOVEMBER 12», en mayúsculas de la locale. */
    fun format(date: LocalDate, locale: Locale, pattern: String = DEFAULT_PATTERN): String =
        pattern.format(
            locale,
            date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(locale),
            date.dayOfMonth,
            date.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale),
        )

    /** Primer día de la semana de la locale (lunes en España, domingo en EE. UU.). */
    fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** Los 7 días de la semana de [date], empezando en [first]. */
    fun week(date: LocalDate, first: DayOfWeek): List<LocalDate> {
        val start = date.minusDays(((date.dayOfWeek.value - first.value + 7) % 7).toLong())
        return List(7) { start.plusDays(it.toLong()) }
    }

    /** Letra de cada día (inicial en la locale: L M X J V S D / M T W T F S S). */
    fun weekLetter(day: LocalDate, locale: Locale): String =
        day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
}
