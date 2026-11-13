package com.usos.launcher.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizingTest {
    private val a = HostedWidget(1)
    private val b = HostedWidget(2)
    private val c = HostedWidget(3)

    @Test fun baseAndMax() {
        assertEquals(110, WidgetSizing.baseDp(110, 0))
        assertEquals(80, WidgetSizing.baseDp(110, 80))
        assertEquals(48, WidgetSizing.baseDp(10, 0))
        assertEquals(440, WidgetSizing.maxDp(110, 0))
        assertEquals(300, WidgetSizing.maxDp(110, 300))
        assertEquals(110, WidgetSizing.maxDp(110, 50))
    }

    @Test fun defaultUsesCellsWhenBigger() {
        assertEquals(110, WidgetSizing.defaultDp(110, 0))
        assertEquals(180, WidgetSizing.defaultDp(110, 3))
        assertEquals(110, WidgetSizing.defaultDp(110, 1))
    }

    @Test fun heightIsClamped() {
        assertEquals(110, WidgetSizing.heightDp(0, 110, 440, 110))
        assertEquals(440, WidgetSizing.heightDp(900, 110, 440, 110))
        assertEquals(110, WidgetSizing.heightDp(20, 110, 440, 110))
        assertEquals(200, WidgetSizing.heightDp(200, 110, 440, 110))
    }

    @Test fun rowsAndDp() {
        assertEquals(95, WidgetSizing.rowsToDp(1))
        assertEquals(200, WidgetSizing.rowsToDp(2))
        assertEquals(95 * 5 + 40, WidgetSizing.rowsToDp(9))
        assertEquals(1, WidgetSizing.dpToRows(30))
        assertEquals(2, WidgetSizing.dpToRows(190))
        assertEquals(5, WidgetSizing.dpToRows(2000))
    }

    @Test fun snapRowsFollowsDragAndClamps() {
        assertEquals(3, WidgetSizing.snapRows(2, 100f))
        assertEquals(2, WidgetSizing.snapRows(2, 40f))
        assertEquals(1, WidgetSizing.snapRows(2, -500f))
        assertEquals(5, WidgetSizing.snapRows(4, 500f))
    }

    @Test fun targetIndexFromDragOffset() {
        val h = listOf(100f, 100f, 100f)
        assertEquals(1, WidgetSizing.targetIndex(h, 0f, 0, 120f))
        assertEquals(2, WidgetSizing.targetIndex(h, 0f, 0, 260f))
        assertEquals(0, WidgetSizing.targetIndex(h, 0f, 0, 20f))
        assertEquals(1, WidgetSizing.targetIndex(h, 0f, 2, -120f))
        assertEquals(0, WidgetSizing.targetIndex(h, 0f, 2, -400f))
    }

    @Test fun orphansAreHostIdsNotSaved() {
        assertEquals(listOf(5, 9), WidgetSizing.orphans(listOf(1, 5, 2, 9), listOf(a, b)))
        assertEquals(emptyList<Int>(), WidgetSizing.orphans(listOf(1), listOf(a, b)))
    }

    @Test fun moveWithinBounds() {
        val l = listOf(a, b, c)
        assertEquals(listOf(b, a, c), WidgetSizing.move(l, 2, -1))
        assertEquals(listOf(a, c, b), WidgetSizing.move(l, 2, 1))
        assertEquals(l, WidgetSizing.move(l, 1, -1))
        assertEquals(l, WidgetSizing.move(l, 3, 1))
        assertEquals(l, WidgetSizing.move(l, 99, 1))
    }

    @Test fun setsHeight() {
        assertEquals(listOf(a, b.copy(heightDp = 200)), WidgetSizing.withHeight(listOf(a, b), 2, 200))
    }
}
