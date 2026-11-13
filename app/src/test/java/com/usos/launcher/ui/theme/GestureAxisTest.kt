package com.usos.launcher.ui.theme

import com.usos.launcher.ui.theme.GestureAxis.Axis
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureAxisTest {
    @Test fun axisDecision() {
        assertEquals(Axis.UNDECIDED, GestureAxis.decide(3f, -4f, 8f))
        assertEquals(Axis.HORIZONTAL, GestureAxis.decide(30f, 10f, 8f))
        assertEquals(Axis.HORIZONTAL, GestureAxis.decide(-20f, 20f, 8f))
        assertEquals(Axis.VERTICAL, GestureAxis.decide(5f, -30f, 8f))
    }
}
