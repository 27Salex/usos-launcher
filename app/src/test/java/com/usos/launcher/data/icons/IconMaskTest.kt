package com.usos.launcher.data.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IconMaskTest {
    private val white = 0xFFFFFFFF.toInt()
    private val green = 0xFF1DB954.toInt()
    private val black = 0xFF000000.toInt()

    private val transparent = 0
    private val orange = 0xFFF26522.toInt()

    /** Lado del icono de prueba en píxeles. */
    private val iconSize = 20

    /** Columnas/filas que ocupa el logo: un cuadrado central de 8x8. */
    private val logoArea = 6..13

    /** Índice del píxel central (dentro del logo). */
    private val centerPixel = (iconSize / 2) * iconSize + iconSize / 2

    /** Índice de la esquina superior izquierda (siempre plato). */
    private val cornerPixel = 0

    private val fullyOpaque = 255
    private val fullyTransparent = 0

    private fun alphaOf(argb: Int) = argb ushr 24

    /** Icono [iconSize]x[iconSize]: plato [plate] con el logo [logo] en [logoArea]. */
    private fun icon(plate: Int, logo: Int) = IntArray(iconSize * iconSize) { index ->
        val x = index % iconSize
        val y = index / iconSize
        if (x in logoArea && y in logoArea) logo else plate
    }

    @Test fun squareIconUsesItsPlateNotTheWhiteBackground() {
        // Icono cuadrado completo naranja con logo blanco: el fondo es el naranja y queda solo el logo.
        val pixels = icon(orange, white)
        assertEquals(orange, IconMask.plateColor(pixels))
        val mask = IconMask.contrastMask(pixels, IconMask.plateColor(pixels))!!
        assertEquals(fullyTransparent, alphaOf(mask[cornerPixel]))
        assertEquals(fullyOpaque, alphaOf(mask[centerPixel]))
    }

    @Test fun smallLogoWithoutPlateHasNoPlateColor() {
        // Logo negro sin plato (el resto transparente): no hay color de plato.
        assertNull(IconMask.plateColor(icon(transparent, black)))
    }

    @Test fun darkLogoOnWhitePlateKeepsLogoOnly() {
        val px = icon(white, black)
        val bg = IconMask.borderColor(px, iconSize)
        assertEquals(white, bg)
        val m = IconMask.contrastMask(px, bg)!!
        assertEquals(fullyTransparent, alphaOf(m[cornerPixel])) // plato fuera
        assertEquals(fullyOpaque, alphaOf(m[centerPixel])) // logo dentro
    }

    @Test fun whiteLogoOnColourPlate() {
        val m = IconMask.contrastMask(icon(green, white), green)!!
        assertEquals(fullyTransparent, alphaOf(m[cornerPixel]))
        assertEquals(fullyOpaque, alphaOf(m[centerPixel]))
    }

    @Test fun transparentIconHasNoPlate() = assertNull(IconMask.borderColor(IntArray(iconSize * iconSize), iconSize))

    @Test fun flatPlateIsRejected() = assertNull(IconMask.contrastMask(IntArray(iconSize * iconSize) { green }, green))

    @Test fun silhouetteWithoutBackground() = assertNotNull(IconMask.contrastMask(icon(transparent, white), null))
}
