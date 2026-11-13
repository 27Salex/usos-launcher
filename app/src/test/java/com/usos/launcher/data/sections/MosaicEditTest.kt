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
}
