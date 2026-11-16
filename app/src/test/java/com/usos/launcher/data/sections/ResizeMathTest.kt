package com.usos.launcher.data.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeMathTest {
    private val columns = 4
    private val rows = 3
    private val maxTileSpan = 2
    private val homeLimits = ResizeLimits(columns = columns, rows = rows, maxWidth = maxTileSpan, maxHeight = maxTileSpan)
    private val step = 100f
    private val oneStep = step
    private val lastColumn = columns - 1
    private val lastRow = rows - 1

    @Test fun tileOnTheRightEdgeHasLeftHandleButNoRightOne() {
        val handles = ResizeMath.handlesFor(CellBox(col = lastColumn, row = 0, width = 1, height = 1), homeLimits)
        assertTrue(ResizeHandle.LEFT in handles)
        assertFalse(ResizeHandle.RIGHT in handles)
        assertFalse(ResizeHandle.TOP in handles)
        assertTrue(ResizeHandle.BOTTOM_LEFT in handles)
    }

    @Test fun tileOnTheBottomEdgeHasTopHandleButNoBottomOne() {
        val handles = ResizeMath.handlesFor(CellBox(col = 0, row = lastRow, width = 1, height = 1), homeLimits)
        assertTrue(ResizeHandle.TOP in handles)
        assertFalse(ResizeHandle.BOTTOM in handles)
    }

    @Test fun growingLeftMovesTheCornerAndKeepsTheRightEdge() {
        val start = CellBox(col = lastColumn, row = 0, width = 1, height = 1)
        val result = ResizeMath.resize(start, ResizeHandle.LEFT, -oneStep, 0f, step, step, homeLimits)
        assertEquals(CellBox(col = lastColumn - 1, row = 0, width = 2, height = 1), result)
    }

    @Test fun growingUpMovesTheCornerAndKeepsTheBottomEdge() {
        val start = CellBox(col = 0, row = lastRow, width = 1, height = 1)
        val result = ResizeMath.resize(start, ResizeHandle.TOP, 0f, -oneStep, step, step, homeLimits)
        assertEquals(CellBox(col = 0, row = lastRow - 1, width = 1, height = 2), result)
    }

    @Test fun cannotGrowPastTheGridOrTheMaximumSize() {
        val start = CellBox(col = lastColumn, row = 0, width = 1, height = 1)
        val pastGrid = ResizeMath.resize(start, ResizeHandle.RIGHT, 5 * oneStep, 0f, step, step, homeLimits)
        assertEquals(start, pastGrid)
        val pastMax = ResizeMath.resize(CellBox(0, 0, 1, 1), ResizeHandle.BOTTOM_RIGHT, 5 * oneStep, 5 * oneStep, step, step, homeLimits)
        assertEquals(CellBox(0, 0, maxTileSpan, maxTileSpan), pastMax)
    }

    @Test fun widgetsHaveNoBottomLimit() {
        val widgetLimits = ResizeLimits(columns = columns, rows = null, maxHeight = 6)
        val handles = ResizeMath.handlesFor(CellBox(col = 0, row = 10, width = columns, height = 2), widgetLimits)
        assertTrue(ResizeHandle.BOTTOM in handles)
        assertTrue(ResizeHandle.RIGHT in handles) // ocupa todo el ancho: se deja el derecho para encoger
        assertFalse(ResizeHandle.LEFT in handles)
    }
}
