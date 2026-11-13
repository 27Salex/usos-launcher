package com.usos.launcher.data.sections

import com.usos.launcher.domain.Section

/** Tamaño de una pieza en celdas (columnas x filas). */
enum class TileSize(val colSpan: Int, val rowSpan: Int, val label: String) {
    S1X1(1, 1, "1×1"), S2X1(2, 1, "2×1"), S1X2(1, 2, "1×2"), S2X2(2, 2, "2×2");

    companion object {
        fun fromName(name: String?): TileSize = entries.firstOrNull { it.name == name } ?: S1X1
    }
}

data class PackItem(val key: String, val colSpan: Int, val rowSpan: Int)
data class Placement(val key: String, val col: Int, val row: Int, val colSpan: Int, val rowSpan: Int)
data class Cell(val col: Int, val row: Int)

/**
 * [placements] en orden de entrada; [skipped] = piezas que no caben; [reserved] = celda 1x1 libre para la
 * pieza CONGELAR (solo si se pidió reservar).
 */
data class PackResult(val placements: List<Placement>, val skipped: List<String>, val reserved: Cell?) {
    val rowsUsed: Int
        get() = maxOf(placements.maxOfOrNull { it.row + it.rowSpan } ?: 0, (reserved?.row ?: -1) + 1)
}

/** Empaquetado denso por orden en una rejilla. Función pura. */
object MosaicPacker {
    const val DEFAULT_ROWS = 2
    const val MIN_ROWS = 1
    const val MAX_ROWS = 4
    val COLUMN_CHOICES = listOf(3, 4)

    /**
     * Cada pieza va a la primera celda libre en orden de lectura donde quepa; si no cabe en ninguna se
     * salta y se prueba la siguiente. Con [reserveFreeze] la última celda de la rejilla queda bloqueada
     * mientras se colocan las piezas y la reservada pasa a ser la primera celda libre posterior a la última pieza.
     */
    fun pack(columns: Int, rows: Int, items: List<PackItem>, reserveFreeze: Boolean = false): PackResult {
        val taken = Array(rows) { BooleanArray(columns) }
        if (reserveFreeze) taken[rows - 1][columns - 1] = true
        val placements = mutableListOf<Placement>()
        val skipped = mutableListOf<String>()
        for (item in items) {
            val cs = item.colSpan.coerceAtMost(columns)
            var spot: Cell? = null
            search@ for (r in 0..rows - item.rowSpan) for (c in 0..columns - cs) {
                if (fits(taken, c, r, cs, item.rowSpan)) { spot = Cell(c, r); break@search }
            }
            if (spot == null) { skipped += item.key; continue }
            for (r in spot.row until spot.row + item.rowSpan) for (c in spot.col until spot.col + cs) taken[r][c] = true
            placements += Placement(item.key, spot.col, spot.row, cs, item.rowSpan)
        }
        var reserved: Cell? = null
        if (reserveFreeze) {
            taken[rows - 1][columns - 1] = false
            val lastIdx = placements.maxOfOrNull { it.row * columns + it.col } ?: -1
            reserved = (lastIdx + 1 until rows * columns).map { Cell(it % columns, it / columns) }
                .firstOrNull { !taken[it.row][it.col] } ?: Cell(columns - 1, rows - 1)
        }
        return PackResult(placements, skipped, reserved)
    }

    private fun fits(taken: Array<BooleanArray>, c: Int, r: Int, cs: Int, rs: Int): Boolean {
        for (rr in r until r + rs) for (cc in c until c + cs) if (taken[rr][cc]) return false
        return true
    }

    /** Alto de celda: 95 dp con 3 columnas; con más columnas, proporcional al ancho de celda y mínimo 76 dp. */
    fun tileHeightDp(columns: Int, usableWidthDp: Float): Float {
        if (columns <= 3) return 95f
        val cell3 = (usableWidthDp - 22f) / 3f
        val cell = (usableWidthDp - 11f * (columns - 1)) / columns
        return maxOf(76f, 95f * cell / cell3)
    }
}

/** Lo mínimo de una app para colocarla. */
data class MosaicEntry(val key: String, val size: TileSize, val inMosaic: Boolean)

fun PlacedApp.entry() = MosaicEntry(config.key, config.size, config.inMosaic)

/** Mosaico de una sección: pieza de información (opcional) primero y luego las apps con inMosaic, en orden. */
object SectionMosaic {
    const val INFO_KEY = "\u0000info"

    fun layout(
        columns: Int, rows: Int, info: TileSize?, entries: List<MosaicEntry>, reserveFreeze: Boolean = false,
    ): PackResult {
        val items = buildList {
            info?.let { add(PackItem(INFO_KEY, it.colSpan, it.rowSpan)) }
            entries.filter { it.inMosaic }.forEach { add(PackItem(it.key, it.size.colSpan, it.size.rowSpan)) }
        }
        return MosaicPacker.pack(columns, rows, items, reserveFreeze)
    }

    /** true si [key], activada, se vería en el mosaico (capacidad real con tamaños, pieza de info y CONGELAR). */
    fun fits(
        columns: Int, rows: Int, info: TileSize?, entries: List<MosaicEntry>, key: String, reserveFreeze: Boolean = false,
    ): Boolean {
        val forced = entries.map { if (it.key == key) it.copy(inMosaic = true) else it }
        return key !in layout(columns, rows, info, forced, reserveFreeze).skipped
    }

    fun reservesFreeze(section: Section) = section == Section.LEISURE
}
