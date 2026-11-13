package com.usos.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test
    fun contrastPicksReadableText() {
        assertEquals(21.0, ColorMath.contrast(ColorMath.BLACK, ColorMath.WHITE), 0.01)
        assertEquals(ColorMath.BLACK, ColorMath.onColor(0xFFFFFFFF.toInt()))
        assertEquals(ColorMath.BLACK, ColorMath.onColor(0xFFD7FF3F.toInt())) // acento lima
        assertEquals(ColorMath.BLACK, ColorMath.onColor(0xFFFF6A3D.toInt())) // acento por defecto
        assertEquals(ColorMath.WHITE, ColorMath.onColor(0xFF000000.toInt()))
        assertEquals(ColorMath.WHITE, ColorMath.onColor(0xFF1A237E.toInt())) // azul oscuro
        for (c in listOf(0xFF777777, 0xFF0000FF, 0xFF00AA00, 0xFFCC3300)) {
            val bg = c.toInt()
            assertTrue(ColorMath.contrast(bg, ColorMath.onColor(bg)) >= 4.5)
        }
    }

    @Test
    fun hsvRoundTrip() {
        for (c in listOf(0xFFFF6A3D, 0xFF123456, 0xFFFFFFFF, 0xFF000000, 0xFF00FF00)) {
            val argb = c.toInt()
            val h = ColorMath.argbToHsv(argb)
            assertEquals(argb, ColorMath.hsvToArgb(h[0], h[1], h[2]))
        }
        assertEquals(0xFFFF0000.toInt(), ColorMath.hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ColorMath.hsvToArgb(240f, 1f, 1f))
    }

    @Test
    fun hex() {
        assertEquals(0xFFFF6A3D.toInt(), ColorMath.parseHex("#ff6a3d"))
        assertEquals(0xFFAABBCC.toInt(), ColorMath.parseHex("abc"))
        assertNull(ColorMath.parseHex("12345"))
        assertNull(ColorMath.parseHex("GGGGGG"))
        assertEquals("FF6A3D", ColorMath.toHex(0xFFFF6A3D.toInt()))
    }
}
