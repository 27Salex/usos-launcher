package com.usos.launcher.data.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicEditTest {
    private fun p(key: String, col: Int, row: Int) = Placement(key, col, row, 1, 1)
    private val row = listOf(p("a", 0, 0), p("b", 1, 0), p("c", 2, 0))
    private val order = listOf("a", "b", "c", "d")

    @Test fun cellAtFindsCellsAndRejectsOutside() {
        assertEquals(Cell(1, 1), MosaicEdit.cellAt(110f, 120f, 100f, 100f, 3, 2))
        assertNull(MosaicEdit.cellAt(-1f, 10f, 100f, 100f, 3, 2))
        assertNull(MosaicEdit.cellAt(10f, 200f, 100f, 100f, 3, 2))
        assertNull(MosaicEdit.cellAt(300f, 10f, 100f, 100f, 3, 2))
    }

    @Test fun forwardDropSwapsWithTargetPiece() {
        assertEquals(listOf("b", "a", "c", "d"), MosaicEdit.reorder(order, row, "a", Cell(1, 0), 3))
        assertEquals(listOf("b", "c", "a", "d"), MosaicEdit.reorder(order, row, "a", Cell(2, 0), 3))
    }

    @Test fun backwardDropGoesBeforeTargetPiece() {
        assertEquals(listOf("c", "a", "b", "d"), MosaicEdit.reorder(order, row, "c", Cell(0, 0), 3))
        assertEquals(listOf("a", "c", "b", "d"), MosaicEdit.reorder(order, row, "c", Cell(1, 0), 3))
    }

    @Test fun dropOnEmptyCellAfterAllGoesAfterLastVisible() {
        assertEquals(listOf("b", "c", "a", "d"), MosaicEdit.reorder(order, row, "a", Cell(2, 1), 3))
    }

    @Test fun newPieceIsInsertedBeforeTarget() {
        assertEquals(listOf("a", "d", "b", "c"), MosaicEdit.reorder(listOf("a", "b", "c", "d"), row, "d", Cell(1, 0), 3))
    }

    @Test fun infoPieceIsIgnoredAsAnchor() {
        val withInfo = listOf(Placement(SectionMosaic.INFO_KEY, 0, 0, 2, 1), p("a", 2, 0), p("b", 0, 1))
        assertEquals(listOf("b", "a"), MosaicEdit.reorder(listOf("a", "b"), withInfo, "b", Cell(2, 0), 3))
    }

    @Test fun snapSizeRoundsToCellsAndClamps() {
        assertEquals(TileSize.S2X1, MosaicEdit.snapSize(TileSize.S1X1, 60f, 10f, 100f, 100f))
        assertEquals(TileSize.S2X2, MosaicEdit.snapSize(TileSize.S1X1, 120f, 130f, 100f, 100f))
        assertEquals(TileSize.S1X1, MosaicEdit.snapSize(TileSize.S2X2, -90f, -100f, 100f, 100f))
        assertEquals(TileSize.S2X2, MosaicEdit.snapSize(TileSize.S2X2, 500f, 500f, 100f, 100f))
        assertEquals(TileSize.S1X2, MosaicEdit.snapSize(TileSize.S2X2, -80f, 20f, 100f, 100f))
    }

    @Test fun resizeIsRejectedWhenItHidesAnotherPiece() {
        val before = MosaicPacker.pack(3, 1, listOf(PackItem("a", 1, 1), PackItem("b", 1, 1), PackItem("c", 1, 1)))
        val big = MosaicPacker.pack(3, 1, listOf(PackItem("a", 2, 1), PackItem("b", 1, 1), PackItem("c", 1, 1)))
        assertTrue(before.skipped.isEmpty())
        assertEquals(listOf("c"), big.skipped)
        assertFalse(MosaicEdit.noNewlySkipped(before, big))
        assertTrue(MosaicEdit.noNewlySkipped(big, big))
    }

    @Test fun hoverCellKeepsPreviousNearBorderAndSwitchesPastMargin() {
        // Dedo en la celda 0 a punto de cruzar a la 1: con histéresis se queda en la 0.
        assertEquals(Cell(0, 0), MosaicEdit.hoverCell(Cell(0, 0), 110f, 50f, 100f, 100f, 3, 2))
        // Lo mismo al revés: viniendo de la 1, 90 sigue siendo la 1.
        assertEquals(Cell(1, 0), MosaicEdit.hoverCell(Cell(1, 0), 90f, 50f, 100f, 100f, 3, 2))
        // Pasado el margen cambia.
        assertEquals(Cell(1, 0), MosaicEdit.hoverCell(Cell(0, 0), 130f, 50f, 100f, 100f, 3, 2))
        // Sin celda previa no hay histéresis ni fuera de la rejilla.
        assertEquals(Cell(1, 0), MosaicEdit.hoverCell(null, 110f, 50f, 100f, 100f, 3, 2))
        assertNull(MosaicEdit.hoverCell(null, 350f, 50f, 100f, 100f, 3, 2))
        // El borde exterior tolera un pequeño desvío del dedo.
        assertEquals(Cell(2, 0), MosaicEdit.hoverCell(Cell(2, 0), 310f, 50f, 100f, 100f, 3, 2))
    }

    private fun e(k: String, size: TileSize = TileSize.S1X1, inMosaic: Boolean = true) = MosaicEntry(k, size, inMosaic)

    @Test fun previewMoveReordersAndRepacks() {
        val entries = listOf(e("a"), e("b"), e("c"), e("d"))
        val current = SectionMosaic.layout(3, 2, null, entries).placements
        val pv = MosaicEdit.previewMove(3, 2, null, entries, current, "a", Cell(2, 0), false)!!
        assertEquals(listOf("b", "c", "a", "d"), pv.order)
        val at = pv.layout.placements.associateBy { it.key }
        assertEquals(Cell(2, 0), Cell(at["a"]!!.col, at["a"]!!.row))
        assertEquals(Cell(0, 0), Cell(at["b"]!!.col, at["b"]!!.row))
    }

    @Test fun previewMoveOfPieceThatCannotFitIsNull() {
        // b (2x2) está oculta porque a (2x2) ocupa el mosaico; ponerla delante deja fuera a la otra, no a ella: cabe.
        val entries = listOf(e("a", TileSize.S2X2), e("b", TileSize.S2X2), e("c"))
        val current = SectionMosaic.layout(3, 2, null, entries).placements
        val pv = MosaicEdit.previewMove(3, 2, null, entries, current, "c", Cell(0, 0), false)!!
        assertEquals(listOf("c", "a", "b"), pv.order)
        // Una pieza 3x? no existe; con una sola fila una pieza 1x2 jamás cabe: sin vista previa.
        val tall = listOf(e("a"), e("t", TileSize.S1X2))
        assertNull(MosaicEdit.previewMove(3, 1, null, tall, SectionMosaic.layout(3, 1, null, tall).placements, "t", Cell(0, 0), false))
    }

    @Test fun previewMoveFromEmptyCellAppendsAndDoesNotMutateInput() {
        val entries = listOf(e("a"), e("b"))
        val current = SectionMosaic.layout(3, 2, null, entries).placements
        val before = entries.map { it.key }
        val pv = MosaicEdit.previewMove(3, 2, null, entries, current, "a", Cell(2, 1), false)!!
        assertEquals(listOf("b", "a"), pv.order)
        assertEquals(before, entries.map { it.key })
    }

    @Test fun previewResizeRepacksOthersAndRejectsMisfit() {
        val entries = listOf(e("a"), e("b"), e("c"))
        val bigger = MosaicEdit.previewResize(3, 1, null, entries, "a", TileSize.S2X1, false)!!
        val at = bigger.placements.associateBy { it.key }
        assertEquals(2, at["a"]!!.colSpan)
        assertEquals(listOf("c"), bigger.skipped) // una fila: c deja de caber, pero a sí
        assertNull(MosaicEdit.previewResize(3, 1, null, entries, "a", TileSize.S2X2, false)) // no cabe en una fila
        val info = MosaicEdit.previewResize(3, 2, TileSize.S2X1, entries, SectionMosaic.INFO_KEY, TileSize.S1X2, false)!!
        assertEquals(2, info.placements.first { it.key == SectionMosaic.INFO_KEY }.rowSpan)
    }
}
