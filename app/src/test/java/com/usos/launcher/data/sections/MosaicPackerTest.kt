package com.usos.launcher.data.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicPackerTest {
    private fun item(k: String, s: TileSize = TileSize.S1X1) = PackItem(k, s.colSpan, s.rowSpan)
    private fun at(r: PackResult, k: String) = r.placements.first { it.key == k }.let { it.col to it.row }

    @Test fun denseOrderWithMixedSizes() {
        val r = MosaicPacker.pack(3, 2, listOf(item("a", TileSize.S2X1), item("b"), item("c"), item("d"), item("e")))
        assertEquals(0 to 0, at(r, "a"))
        assertEquals(2 to 0, at(r, "b"))
        assertEquals(0 to 1, at(r, "c"))
        assertEquals(1 to 1, at(r, "d"))
        assertEquals(2 to 1, at(r, "e"))
    }

    @Test fun pieceThatDoesNotFitIsSkippedAndNextOneTried() {
        // 2x2 llena la mitad izquierda de 4x2... en 3x2 deja 1 columna: la 2x1 no cabe, la 1x1 sí
        val r = MosaicPacker.pack(3, 2, listOf(item("big", TileSize.S2X2), item("wide", TileSize.S2X1), item("s")))
        assertEquals(listOf("wide"), r.skipped)
        assertEquals(2 to 0, at(r, "s"))
    }

    @Test fun tallDoesNotFitInOneRow() {
        val r = MosaicPacker.pack(4, 1, listOf(item("t", TileSize.S1X2), item("a")))
        assertEquals(listOf("t"), r.skipped)
        assertEquals(0 to 0, at(r, "a"))
    }

    @Test fun capacityByColumnsAndRows() {
        for (c in listOf(3, 4)) for (rows in 1..4) {
            val r = MosaicPacker.pack(c, rows, (1..20).map { item("i$it") })
            assertEquals(c * rows, r.placements.size)
        }
    }

    @Test fun freezeKeepsLastSlotAndSitsAfterTheApps() {
        val full = MosaicPacker.pack(3, 2, (1..6).map { item("i$it") }, reserveFreeze = true)
        assertEquals(5, full.placements.size)
        assertEquals(Cell(2, 1), full.reserved)
        val few = MosaicPacker.pack(3, 2, listOf(item("a"), item("b")), reserveFreeze = true)
        assertEquals(Cell(2, 0), few.reserved)
        assertTrue(SectionMosaic.fits(3, 2, null, listOf(MosaicEntry("a", TileSize.S1X1, false)), "a", true))
    }

    @Test fun tileHeight() {
        assertEquals(95f, MosaicPacker.tileHeightDp(3, 330f), 0.01f)
        assertEquals(76f, MosaicPacker.tileHeightDp(4, 330f), 0.01f)
    }
}
