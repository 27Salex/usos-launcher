package com.usos.launcher.data.info

import com.usos.launcher.domain.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class InfoTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(day: Int, h: Int, m: Int = 0) =
        ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun ev(title: String, begin: Long, endMin: Int = 60, allDay: Boolean = false) =
        CalEvent(title, begin, begin + endMin * 60_000L, allDay)

    @Test
    fun nextEvent_picksEarliestNotFinished() {
        val now = at(13, 10)
        val events = listOf(ev("Pasado", at(13, 8)), ev("Tarde", at(13, 17)), ev("Pronto", at(13, 11)))
        assertEquals("Pronto", InfoPieces.nextEvent(events, now, zone)?.title)
    }

    @Test
    fun nextEvent_includesTomorrowButNotDayAfter() {
        val now = at(13, 20)
        assertEquals("Mañana", InfoPieces.nextEvent(listOf(ev("Mañana", at(14, 9))), now, zone)?.title)
        assertNull(InfoPieces.nextEvent(listOf(ev("Pasado mañana", at(15, 9))), now, zone))
    }

    @Test
    fun nextEvent_ignoresAllDayAndEmpty() {
        assertNull(InfoPieces.nextEvent(listOf(ev("Festivo", at(13, 0), 1440, allDay = true)), at(13, 9), zone))
        assertNull(InfoPieces.nextEvent(emptyList(), at(13, 9), zone))
    }

    @Test
    fun agendaPiece_labels() {
        val now = at(13, 9)
        val today = InfoPieces.agendaPiece(ev("Reunión", at(13, 10, 30)), now, zone)!!
        assertEquals(InfoWhen.TODAY, today.whenAt)
        assertEquals("10:30", today.time)
        val tomorrow = InfoPieces.agendaPiece(ev("Dentista", at(14, 9, 5)), now, zone)!!
        assertEquals(InfoWhen.TOMORROW, tomorrow.whenAt)
        assertEquals("09:05", tomorrow.time)
        assertEquals(InfoWhen.NOW, InfoPieces.agendaPiece(ev("En curso", at(13, 8, 30)), now, zone)?.whenAt)
        assertEquals("Reunión", InfoPieces.agendaPiece(ev("Reunión", at(13, 10)), now, zone)?.title)
        assertNull(InfoPieces.agendaPiece(null, now, zone))
    }

    @Test
    fun noData_noPiece() {
        val pieces = InfoPieces.bySection(listOf(null, InfoPieces.mapsPiece(false)))
        assertTrue(pieces.isEmpty())
    }

    @Test
    fun piecesLiveInTheirSection() {
        val agenda = InfoPieces.agendaPiece(ev("X", at(13, 10)), at(13, 9), zone)
        val pieces = InfoPieces.bySection(listOf(agenda, InfoPieces.mapsPiece(true)))
        assertEquals(InfoKind.AGENDA, pieces[Section.WORK]?.kind)
        assertEquals(InfoKind.MAPS, pieces[Section.MOVE]?.kind)
    }
}
