package com.usos.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DotMatrixTest {
    private val expected = listOf(
        "01110 10001 10011 10101 11001 10001 01110",
        "00100 01100 00100 00100 00100 00100 01110",
        "01110 10001 00001 00010 00100 01000 11111",
        "11110 00001 00001 01110 00001 00001 11110",
        "00010 00110 01010 10010 11111 00010 00010",
        "11111 10000 11110 00001 00001 10001 01110",
        "00110 01000 10000 11110 10001 10001 01110",
        "11111 00001 00010 00100 01000 01000 01000",
        "01110 10001 10001 01110 10001 10001 01110",
        "01110 10001 10001 01111 00001 00010 01100",
    )

    private fun render(digit: Int): String {
        val on = DotMatrix.dots(digit).toSet()
        return (0 until 7).joinToString(" ") { r ->
            (0 until 5).joinToString("") { c -> if (Dot(r, c) in on) "1" else "0" }
        }
    }

    @Test
    fun everyDigitMatchesItsMatrix() {
        for (d in 0..9) assertEquals("dígito $d", expected[d], render(d))
    }

    @Test
    fun dotCountsMatchBits() {
        for (d in 0..9) assertEquals(expected[d].count { it == '1' }, DotMatrix.dots(d).size)
    }

    @Test
    fun colonUsesRows2And4() {
        val pos = DotMatrix.layout(":")
        assertEquals(listOf(DotPosition(0, 18), DotPosition(0, 36)), pos)
    }

    @Test
    fun layoutSpacingIsNineWithFourteenBetweenDigits() {
        // "11:11": el segundo dígito empieza a 41 + 14 = 55 dp; el "1" tiene un punto en fila 0, col 2.
        val pos = DotMatrix.layout("11:11")
        assertEquals(DotPosition(2 * 9, 0), pos.first())
        assertEquals(true, DotPosition(55 + 2 * 9, 0) in pos)
        assertEquals(41 * 4 + 5 + 14 * 4, DotMatrix.widthDp("11:11"))
    }

    @Test fun layoutByCharMatchesFlatLayout() {
        val flat = DotMatrix.layout("09:41")
        val byChar = DotMatrix.layoutByChar("09:41")
        assertEquals(5, byChar.size)
        assertEquals(flat, byChar.flatten())
    }
}
