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

    /**
     * Celda bajo el dedo con histéresis: si el punto sigue dentro de la celda [prev] ampliada un [hysteresis]
     * (fracción del paso) por cada lado, se mantiene; así no parpadea entre celdas vecinas ni en los bordes.
     */
    fun hoverCell(
        prev: Cell?, x: Float, y: Float, stepX: Float, stepY: Float, columns: Int, rows: Int, hysteresis: Float = 0.2f,
    ): Cell? {
        if (prev != null && prev.col in 0 until columns && prev.row in 0 until rows) {
            val mx = stepX * hysteresis
            val my = stepY * hysteresis
            if (x >= prev.col * stepX - mx && x < (prev.col + 1) * stepX + mx &&
                y >= prev.row * stepY - my && y < (prev.row + 1) * stepY + my
            ) return prev
        }
        return cellAt(x, y, stepX, stepY, columns, rows)
    }

    /** Orden tentativo y colocación resultante si se soltara ahora. */
    class Preview(val order: List<String>, val layout: PackResult)

    /**
     * Qué pasaría al soltar [dragged] sobre [target]: el orden tentativo ([reorder] sobre el mosaico actual
     * [placements], que no cambia durante el arrastre) y su colocación con [MosaicPacker]. null si la pieza
     * arrastrada no cabría (soltar ahí no hace nada).
     */
    fun previewMove(
        columns: Int, rows: Int, info: TileSize?, entries: List<MosaicEntry>, placements: List<Placement>,
        dragged: String, target: Cell, reserveFreeze: Boolean,
    ): Preview? {
        val byKey = entries.associateBy { it.key }
        if (dragged !in byKey) return null
        val order = reorder(entries.map { it.key }, placements, dragged, target, columns)
        val next = order.mapNotNull { k -> byKey[k]?.let { if (k == dragged) it.copy(inMosaic = true) else it } }
        val layout = SectionMosaic.layout(columns, rows, info, next, reserveFreeze)
        return if (dragged in layout.skipped) null else Preview(order, layout)
    }

    /** Colocación si [key] (una app o [SectionMosaic.INFO_KEY]) pasara a [size]; null si ella misma dejaría de caber. */
    fun previewResize(
        columns: Int, rows: Int, info: TileSize?, entries: List<MosaicEntry>, key: String, size: TileSize,
        reserveFreeze: Boolean,
    ): PackResult? {
        val isInfo = key == SectionMosaic.INFO_KEY
        val layout = SectionMosaic.layout(
            columns, rows, if (isInfo) size else info,
            if (isInfo) entries else entries.map { if (it.key == key) it.copy(size = size) else it },
            reserveFreeze,
        )
        return if (key in layout.skipped) null else layout
    }

    /**
     * Redimensionar [key] a [size] con su esquina en [target] (estirar a la izquierda o arriba mueve la esquina):
     * se reordena como al soltarla en [target] y se empaqueta. Solo vale si cabe y NO oculta ninguna pieza que se
     * veía: si no hay hueco suficiente para el tamaño nuevo, null (no se redimensiona). Si lo hay, las demás se
     * recolocan.
     */
    fun previewResizeAt(
        columns: Int, rows: Int, info: TileSize?, entries: List<MosaicEntry>, placements: List<Placement>,
        key: String, size: TileSize, target: Cell, reserveFreeze: Boolean,
    ): Preview? {
        val before = SectionMosaic.layout(columns, rows, info, entries, reserveFreeze)
        val byKey = entries.associateBy { it.key }
        if (key !in byKey) return null
        val order = reorder(entries.map { it.key }, placements, key, target, columns)
        val next = order.mapNotNull { k -> byKey[k]?.let { if (k == key) it.copy(size = size, inMosaic = true) else it } }
        val layout = SectionMosaic.layout(columns, rows, info, next, reserveFreeze)
        if (key in layout.skipped || !noNewlySkipped(before, layout)) return null
        return Preview(order, layout)
    }

    /** Un cambio es válido si no oculta ninguna pieza que antes se veía. */
    fun noNewlySkipped(before: PackResult, after: PackResult): Boolean =
        after.skipped.all { it in before.skipped }
}
