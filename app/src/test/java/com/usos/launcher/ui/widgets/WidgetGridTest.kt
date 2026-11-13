package com.usos.launcher.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGridTest {
    private val a = GBox(1, 0, 0, 2, 4)
    private val b = GBox(2, 2, 0, 2, 2)
    private val c = GBox(3, 2, 2, 2, 2)
    private val start = listOf(a, b, c)

    @Test fun authorCaseMoveCUnderA() {
        val r = WidgetGrid.place(start, 3, 0, 4, 4)
        assertEquals(listOf(GBox(1, 0, 0, 2, 4), GBox(2, 2, 0, 2, 2), GBox(3, 0, 4, 2, 2)), r)
    }

    @Test fun moveCUnderB() {
        val away = WidgetGrid.place(start, 3, 0, 4, 4)
        val r = WidgetGrid.place(away, 3, 2, 2, 4)
        assertEquals(start, r)
        assertEquals(GBox(3, 2, 5, 2, 2), WidgetGrid.place(start, 3, 2, 5, 4)[2]) // hueco libre debajo
    }

    @Test fun growBPushesCDown() {
        val r = WidgetGrid.resize(start, 2, 2, 3, 4)
        assertEquals(GBox(2, 2, 0, 2, 3), r[1])
        assertEquals(GBox(3, 2, 3, 2, 2), r[2])
        assertEquals(a, r[0])
    }

    @Test fun movingOverPushesCascadeDownNeverUpOrSideways() {
        // C sobre B: B baja justo debajo de C; no se mueve nada más.
        val r = WidgetGrid.place(start, 3, 2, 0, 4)
        assertEquals(GBox(3, 2, 0, 2, 2), r[2])
        assertEquals(GBox(2, 2, 2, 2, 2), r[1])
        assertEquals(a, r[0])
        // Cascada: al empujar B sobre C(2,2)... A en 0,0 4 alto empuja a B y C hacia abajo en cadena.
        val chain = WidgetGrid.place(listOf(GBox(1, 0, 0, 2, 2), GBox(2, 0, 2, 2, 2), GBox(3, 0, 4, 2, 2)), 1, 0, 1, 4)
        assertEquals(listOf(1, 3, 5), chain.map { it.row })
    }

    @Test fun placeIsClampedToTheRightEdge() {
        assertEquals(GBox(3, 2, 4, 2, 2), WidgetGrid.place(start, 3, 3, 4, 4)[2])
    }

    @Test fun resizeIsClampedToColumnsAndNeverRejected() {
        val r = WidgetGrid.resize(start, 2, 9, 2, 4)
        assertEquals(GBox(2, 0, 0, 4, 2), r[1]) // se acota al ancho y se desplaza para caber
        assertEquals(true, r.none { x -> r.any { y -> x.id < y.id && x.overlaps(y) } })
    }

    @Test fun firstFreeIsReadingOrder() {
        assertEquals(2 to 2, WidgetGrid.firstFree(listOf(a, b), 2, 2, 4))
        assertEquals(0 to 4, WidgetGrid.firstFree(start, 4, 1, 4))
    }
}
