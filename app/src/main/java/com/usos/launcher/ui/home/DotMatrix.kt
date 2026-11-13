package com.usos.launcher.ui.home

/** Punto encendido de un dígito: fila 0..6, columna 0..4. */
data class Dot(val row: Int, val col: Int)

/** Posición de un punto del reloj en dp (esquina superior izquierda del punto). */
data class DotPosition(val x: Int, val y: Int)

/** Matriz de puntos del reloj, pura y testeable. */
object DotMatrix {
    const val COLS = 5
    const val ROWS = 7
    const val DOT_DP = 5
    const val STEP_DP = 9
    const val GAP_DP = 14

    /** Filas (contando desde 0) en que se dibujan los dos puntos del separador. */
    val COLON_ROWS = listOf(2, 4)

    const val DIGIT_WIDTH_DP = (COLS - 1) * STEP_DP + DOT_DP
    const val HEIGHT_DP = (ROWS - 1) * STEP_DP + DOT_DP

    /** 7 filas de 5 bits por dígito; 1 = punto encendido. */
    private val PATTERNS = arrayOf(
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

    /** Puntos encendidos del dígito, por filas y de izquierda a derecha. */
    fun dots(digit: Int): List<Dot> {
        require(digit in 0..9) { "Dígito fuera de rango: $digit" }
        val rows = PATTERNS[digit].split(" ")
        return buildList {
            rows.forEachIndexed { r, bits ->
                bits.forEachIndexed { c, bit -> if (bit == '1') add(Dot(r, c)) }
            }
        }
    }

    /** Anchura total en dp de un texto "HH:MM" (4 dígitos y el separador, con 14 dp entre elementos). */
    fun widthDp(text: String = "00:00"): Int {
        val widths = text.map { if (it == ':') DOT_DP else DIGIT_WIDTH_DP }
        return widths.sum() + GAP_DP * (widths.size - 1)
    }

    /** Posiciones en dp de todos los puntos encendidos de un texto como "09:41". */
    fun layout(text: String): List<DotPosition> {
        val out = ArrayList<DotPosition>()
        var x = 0
        for (ch in text) {
            if (ch == ':') {
                for (r in COLON_ROWS) out += DotPosition(x, r * STEP_DP)
                x += DOT_DP + GAP_DP
            } else {
                for (d in dots(ch - '0')) out += DotPosition(x + d.col * STEP_DP, d.row * STEP_DP)
                x += DIGIT_WIDTH_DP + GAP_DP
            }
        }
        return out
    }

    /** Como [layout], pero una lista de puntos por caracter (para animar solo los que cambian). */
    fun layoutByChar(text: String): List<List<DotPosition>> {
        val out = ArrayList<List<DotPosition>>()
        var x = 0
        for (ch in text) {
            if (ch == ':') {
                out += COLON_ROWS.map { DotPosition(x, it * STEP_DP) }
                x += DOT_DP + GAP_DP
            } else {
                out += dots(ch - '0').map { DotPosition(x + it.col * STEP_DP, it.row * STEP_DP) }
                x += DIGIT_WIDTH_DP + GAP_DP
            }
        }
        return out
    }
}
