package com.usos.launcher.ui.home

import java.time.LocalDate

/** Fecha y semana en español, sin depender del locale del sistema. */
object SpanishDate {
    private val DAYS = listOf("LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO", "DOMINGO")
    private val MONTHS = listOf(
        "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
        "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE",
    )
    val WEEK_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

    /** «JUEVES 12 NOVIEMBRE». */
    fun format(date: LocalDate): String =
        "${DAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

    /** Lunes a domingo de la semana de [date]. */
    fun week(date: LocalDate): List<LocalDate> {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return List(7) { monday.plusDays(it.toLong()) }
    }
}
