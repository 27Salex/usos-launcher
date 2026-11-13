package com.usos.launcher.data.sections

import kotlin.math.floor
import kotlin.math.roundToInt

/** Cálculos puros del modo edición del mosaico (arrastrar, soltar y redimensionar). */
object MosaicEdit {
    /** Celda bajo (x, y), relativo al origen de la rejilla; null si cae fuera. Paso = celda + separación. */
    fun cellAt(x: Float, y: Float, stepX: Float, stepY: Float, columns: Int, rows: Int): Cell? {
        if (x < 0f || y < 0f) return null
        val col = floor(x / stepX).toInt()
        val row = floor(y / stepY).toInt()
        return if (col in 0 until columns && row in 0 until rows) Cell(col, row) else null
    }

    /**
     * Nuevo orden completo de la sección al soltar [dragged] sobre [target]. [placements] es el mosaico actual
     * (la pieza de información no cuenta). Hacia delante (la pieza empieza antes que la celda) se inserta tras la
     * pieza que ocupa la celda; hacia atrás o si es nueva, delante de ella. Sin pieza a la que anclarse va tras la
     * última visible.
     */
    fun reorder(
        fullOrder: List<String>, placements: List<Placement>, dragged: String, target: Cell, columns: Int,
    ): List<String> {
        val visible = placements.filter { it.key != SectionMosaic.INFO_KEY && it.key != dragged }
        val targetIdx = target.row * columns + target.col
        val from = placements.firstOrNull { it.key == dragged }?.let { it.row * columns + it.col }
        val forward = from != null && from < targetIdx
        val anchor = visible.firstOrNull {
            val idx = it.row * columns + it.col
            if (forward) idx > targetIdx else idx >= targetIdx
        }
        val rest = fullOrder.filter { it != dragged }.toMutableList()
        val at = when {
            anchor != null -> rest.indexOf(anchor.key).takeIf { it >= 0 } ?: rest.size
            else -> (visible.maxOfOrNull { rest.indexOf(it.key) } ?: -1) + 1
        }
        rest.add(at.coerceIn(0, rest.size), dragged)
        return rest
    }

    /** Tamaño encajado tras arrastrar el tirador [dxPx], [dyPx] desde [current] (1..2 columnas y filas). */
    fun snapSize(current: TileSize, dxPx: Float, dyPx: Float, stepX: Float, stepY: Float): TileSize {
        val cols = (current.colSpan + (dxPx / stepX).roundToInt()).coerceIn(1, 2)
        val rows = (current.rowSpan + (dyPx / stepY).roundToInt()).coerceIn(1, 2)
        return TileSize.entries.first { it.colSpan == cols && it.rowSpan == rows }
    }

    /** Un cambio es válido si no oculta ninguna pieza que antes se veía. */
    fun noNewlySkipped(before: PackResult, after: PackResult): Boolean =
        after.skipped.all { it in before.skipped }
}
