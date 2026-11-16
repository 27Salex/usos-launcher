package com.usos.launcher.data.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IconMaskTest {
    private val white = 0xFFFFFFFF.toInt()
    private val green = 0xFF1DB954.toInt()
    private val black = 0xFF000000.toInt()

    /** 20x20: plato [plate] con un cuadrado central 8x8 de [logo]. */
    private fun icon(plate: Int, logo: Int) = IntArray(400) { i ->
        val x = i % 20; val y = i / 20
        if (x in 6..13 && y in 6..13) logo else plate
    }

    @Test fun darkLogoOnWhitePlateKeepsLogoOnly() {
        val px = icon(white, black)
        val bg = IconMask.borderColor(px, 20)
        assertEquals(white, bg)
        val m = IconMask.contrastMask(px, bg)!!
        assertEquals(0, m[0] ushr 24)            // plato fuera
        assertEquals(255, m[10 * 20 + 10] ushr 24) // logo dentro
    }

    @Test fun whiteLogoOnColourPlate() {
        val m = IconMask.contrastMask(icon(green, white), green)!!
        assertEquals(0, m[0] ushr 24)
        assertEquals(255, m[10 * 20 + 10] ushr 24)
    }

    @Test fun transparentIconHasNoPlate() = assertNull(IconMask.borderColor(IntArray(400), 20))

    @Test fun flatPlateIsRejected() = assertNull(IconMask.contrastMask(IntArray(400) { green }, green))

    @Test fun silhouetteWithoutBackground() = assertNotNull(IconMask.contrastMask(icon(0, white), null))
}
