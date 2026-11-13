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
        assertEquals("AGENDA · 10:30", InfoPieces.agendaPiece(ev("Reunión", at(13, 10, 30)), now, zone)?.label)
        assertEquals("AGENDA · MAÑ 09:05", InfoPieces.agendaPiece(ev("Dentista", at(14, 9, 5)), now, zone)?.label)
        assertEquals("AGENDA · AHORA", InfoPieces.agendaPiece(ev("En curso", at(13, 8, 30)), now, zone)?.label)
        assertEquals("Reunión", InfoPieces.agendaPiece(ev("Reunión", at(13, 10)), now, zone)?.data)
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
        assertEquals("IR A CASA", pieces[Section.MOVE]?.data)
    }

    // --- Colocación en la rejilla 3 x 2 ---

    private fun coords(cells: List<MosaicCell>) = cells.map { Triple(it.col, it.row, if (it.isInfo) -1 else it.itemIndex) }

    @Test
    fun grid_noInfo_sixItems() {
        val cells = MosaicGrid.place(null, 6)
        assertEquals(6, cells.size)
        assertEquals(listOf(Triple(0, 0, 0), Triple(1, 0, 1), Triple(2, 0, 2), Triple(0, 1, 3), Triple(1, 1, 4), Triple(2, 1, 5)), coords(cells))
    }

    @Test
    fun grid_wideInfo_appsFillTheRest() {
        val cells = MosaicGrid.place(InfoShape.WIDE, 4)
        assertEquals(
            listOf(Triple(0, 0, -1), Triple(2, 0, 0), Triple(0, 1, 1), Triple(1, 1, 2), Triple(2, 1, 3)),
            coords(cells),
        )
        assertEquals(2, cells[0].colSpan)
    }

    @Test
    fun grid_tallInfo_appsFillTheRest() {
        val cells = MosaicGrid.place(InfoShape.TALL, 4)
        assertEquals(
            listOf(Triple(0, 0, -1), Triple(1, 0, 0), Triple(2, 0, 1), Triple(1, 1, 2), Triple(2, 1, 3)),
            coords(cells),
        )
        assertEquals(2, cells[0].rowSpan)
    }

    @Test
    fun grid_capacityAndOverflow() {
        assertEquals(6, MosaicGrid.itemCapacity(null))
        assertEquals(4, MosaicGrid.itemCapacity(InfoShape.WIDE))
        assertEquals(4, MosaicGrid.itemCapacity(InfoShape.TALL))
        assertEquals(5, MosaicGrid.place(InfoShape.TALL, 9).size) // info + 4 apps; el exceso no cabe
        assertEquals(1, MosaicGrid.place(InfoShape.WIDE, 0).size)
    }

    @Test
    fun grid_fewItems() {
        assertEquals(listOf(Triple(0, 0, 0), Triple(1, 0, 1)), coords(MosaicGrid.place(null, 2)))
    }
}
