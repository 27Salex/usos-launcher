package com.usos.launcher.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizingTest {
    private val a = HostedWidget(1)
    private val b = HostedWidget(2)
    private val c = HostedWidget(3)

    @Test fun heights() {
        assertEquals(110, WidgetSizing.heightDp(110, WidgetSize.LOW))
        assertEquals(165, WidgetSizing.heightDp(110, WidgetSize.MEDIUM))
        assertEquals(220, WidgetSizing.heightDp(110, WidgetSize.HIGH))
        assertEquals(72, WidgetSizing.heightDp(40, WidgetSize.LOW))
        assertEquals(144, WidgetSizing.heightDp(0, WidgetSize.HIGH))
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

    @Test fun setsSize() {
        assertEquals(listOf(a, b.copy(size = WidgetSize.HIGH)), WidgetSizing.withSize(listOf(a, b), 2, WidgetSize.HIGH))
    }
}
