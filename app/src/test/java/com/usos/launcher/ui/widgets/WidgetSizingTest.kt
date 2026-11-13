package com.usos.launcher.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizingTest {
    private val a = HostedWidget(1)
    private val b = HostedWidget(2)
    private val c = HostedWidget(3)

    @Test fun defaultUsesCellsWhenBigger() {
        assertEquals(110, WidgetSizing.defaultDp(110, 0))
        assertEquals(180, WidgetSizing.defaultDp(110, 3))
        assertEquals(110, WidgetSizing.defaultDp(110, 1))
    }

    @Test fun dpToRowsClamps() {
        assertEquals(1, WidgetSizing.dpToRows(30))
        assertEquals(2, WidgetSizing.dpToRows(190))
        assertEquals(6, WidgetSizing.dpToRows(2000))
    }

    @Test fun migrationUsesAllColumnsAndOldHeight() {
        assertEquals(WidgetCells(3, 2), WidgetSizing.cells(HostedWidget(1, heightDp = 200), 3, 1))
        assertEquals(WidgetCells(4, 3), WidgetSizing.cells(HostedWidget(1, heightDp = 305), 4, 1))
        assertEquals(WidgetCells(4, 2), WidgetSizing.cells(HostedWidget(1), 4, 2)) // nuevo: por defecto
        assertEquals(WidgetCells(3, 1), WidgetSizing.cells(HostedWidget(1, cols = 9, rows = 1), 3, 4)) // se acota a las columnas
    }

    @Test fun snapCellsFollowsDragAndClamps() {
        val cur = WidgetCells(2, 2)
        assertEquals(WidgetCells(3, 3), WidgetSizing.snapCells(cur, 100f, 100f, 100f, 100f, 3))
        assertEquals(WidgetCells(2, 2), WidgetSizing.snapCells(cur, 40f, -40f, 100f, 100f, 3))
        assertEquals(WidgetCells(1, 1), WidgetSizing.snapCells(cur, -900f, -900f, 100f, 100f, 3))
        assertEquals(WidgetCells(3, 6), WidgetSizing.snapCells(cur, 900f, 900f, 100f, 100f, 3))
    }

    @Test fun layoutPacksDenselyInOrder() {
        val r = WidgetSizing.layout(3, listOf(1 to WidgetCells(2, 1), 2 to WidgetCells(2, 1), 3 to WidgetCells(1, 2)))
        assertEquals(emptyList<String>(), r.skipped)
        val p = r.placements.associateBy { it.key }
        assertEquals(0 to 0, p["1"]!!.col to p["1"]!!.row)
        assertEquals(2 to 0, p["3"]!!.col to p["3"]!!.row) // ocupa el hueco de la derecha
        assertEquals(0 to 1, p["2"]!!.col to p["2"]!!.row)
    }

    @Test fun orphansAreHostIdsNotSaved() {
        assertEquals(listOf(5, 9), WidgetSizing.orphans(listOf(1, 5, 2, 9), listOf(a, b)))
        assertEquals(emptyList<Int>(), WidgetSizing.orphans(listOf(1), listOf(a, b)))
    }

    @Test fun reorderAndResize() {
        assertEquals(listOf(c, a, b), WidgetSizing.reorder(listOf(a, b, c), listOf(3, 1, 2)))
        assertEquals(listOf(b, a, c), WidgetSizing.reorder(listOf(a, b, c), listOf(2)))
        assertEquals(listOf(a, b.copy(cols = 2, rows = 3)), WidgetSizing.withCells(listOf(a, b), 2, 2, 3))
    }

    @Test fun minCellsRoundsDownAndAllowsAnyWiderWidth() {
        // Celda de 100 dp con separación 10: un mínimo de 250 dp son 2 celdas (no 3); 90 dp son 1.
        assertEquals(2, WidgetSizing.minCells(250, 100f, 10f, 3))
        assertEquals(1, WidgetSizing.minCells(90, 100f, 10f, 3))
        assertEquals(1, WidgetSizing.minCells(0, 100f, 10f, 3))
        assertEquals(3, WidgetSizing.minCells(2000, 100f, 10f, 3)) // nunca más que las columnas
        // Con ese mínimo se puede elegir 2 o 3 de ancho, y no 1.
        val cur = WidgetCells(3, 1)
        assertEquals(WidgetCells(2, 1), WidgetSizing.snapCells(cur, -100f, 0f, 100f, 100f, 3, minCols = 2))
        assertEquals(WidgetCells(2, 1), WidgetSizing.snapCells(cur, -900f, 0f, 100f, 100f, 3, minCols = 2))
    }

    @Test fun previewMoveGivesOrderAndLayout() {
        val sizes = listOf(1 to WidgetCells(1, 1), 2 to WidgetCells(1, 1), 3 to WidgetCells(1, 1))
        val cur = WidgetSizing.layout(3, sizes).placements
        val (order, layout) = WidgetSizing.previewMove(3, sizes, cur, 1, com.usos.launcher.data.sections.Cell(2, 0))
        assertEquals(listOf(2, 3, 1), order)
        assertEquals(2, layout.placements.first { it.key == "1" }.col)
    }
}
