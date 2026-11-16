package com.usos.launcher.data.sections

import kotlin.math.roundToInt

/**
 * Tirador de redimensionado: hacia dónde estira. [dirX] -1 = borde izquierdo, +1 = derecho, 0 = no cambia el
 * ancho; [dirY] igual con arriba (-1) y abajo (+1). Las esquinas mueven los dos ejes.
 */
enum class ResizeHandle(val dirX: Int, val dirY: Int) {
    LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1),
    TOP_LEFT(-1, -1), TOP_RIGHT(1, -1), BOTTOM_LEFT(-1, 1), BOTTOM_RIGHT(1, 1),
}

/** Caja en celdas: esquina superior izquierda ([col], [row]) y tamaño [width] x [height]. */
data class CellBox(val col: Int, val row: Int, val width: Int, val height: Int)

/** Límites del tamaño de una pieza y de la rejilla. [rows] = null: filas ilimitadas (página de widgets). */
data class ResizeLimits(
    val columns: Int,
    val rows: Int?,
    val minWidth: Int = 1,
    val maxWidth: Int = columns,
    val minHeight: Int = 1,
    val maxHeight: Int,
)

/** Cálculos puros del redimensionado por los cuatro lados. */
object ResizeMath {
    /**
     * Tiradores que se muestran para [box]. Un borde que toca el límite de la rejilla no tiene tirador (no se puede
     * estirar hacia fuera); se estira desde el lado contrario. Si una pieza toca los dos límites de un eje (ocupa
     * todo el ancho), se deja el tirador derecho/inferior para poder encogerla. Las esquinas, solo si sus dos bordes
     * tienen tirador.
     */
    fun handlesFor(box: CellBox, limits: ResizeLimits): Set<ResizeHandle> {
        val touchesLeft = box.col == 0
        val touchesRight = box.col + box.width >= limits.columns
        val touchesTop = box.row == 0
        val touchesBottom = limits.rows != null && box.row + box.height >= limits.rows
        val canChangeWidth = limits.maxWidth > limits.minWidth
        val canChangeHeight = limits.maxHeight > limits.minHeight

        val left = canChangeWidth && !touchesLeft
        val right = canChangeWidth && (!touchesRight || touchesLeft)
        val top = canChangeHeight && !touchesTop
        val bottom = canChangeHeight && (!touchesBottom || touchesTop)

        return buildSet {
            if (left) add(ResizeHandle.LEFT)
            if (right) add(ResizeHandle.RIGHT)
            if (top) add(ResizeHandle.TOP)
            if (bottom) add(ResizeHandle.BOTTOM)
            if (top && left) add(ResizeHandle.TOP_LEFT)
            if (top && right) add(ResizeHandle.TOP_RIGHT)
            if (bottom && left) add(ResizeHandle.BOTTOM_LEFT)
            if (bottom && right) add(ResizeHandle.BOTTOM_RIGHT)
        }
    }

    /**
     * Nueva caja al arrastrar [handle] [dxPx], [dyPx] desde [start]. El borde opuesto al tirador se queda quieto:
     * estirar a la izquierda o arriba mueve la esquina. Respeta tamaños mínimo/máximo y los bordes de la rejilla.
     */
    fun resize(start: CellBox, handle: ResizeHandle, dxPx: Float, dyPx: Float, stepX: Float, stepY: Float, limits: ResizeLimits): CellBox {
        val stepsX = (dxPx / stepX).roundToInt()
        val stepsY = (dyPx / stepY).roundToInt()
        var col = start.col
        var width = start.width
        when (handle.dirX) {
            1 -> {
                // Borde derecho: crece hacia la derecha sin pasar del final de la rejilla.
                val maxByGrid = limits.columns - start.col
                width = (start.width + stepsX).coerceIn(limits.minWidth, minOf(limits.maxWidth, maxByGrid))
            }
            -1 -> {
                // Borde izquierdo: el borde derecho queda fijo; crece hacia la izquierda sin pasar de la columna 0.
                val rightEdge = start.col + start.width
                width = (start.width - stepsX).coerceIn(limits.minWidth, minOf(limits.maxWidth, rightEdge))
                col = rightEdge - width
            }
        }
        var row = start.row
        var height = start.height
        when (handle.dirY) {
            1 -> {
                val maxByGrid = limits.rows?.let { it - start.row } ?: Int.MAX_VALUE
                height = (start.height + stepsY).coerceIn(limits.minHeight, minOf(limits.maxHeight, maxByGrid))
            }
            -1 -> {
                val bottomEdge = start.row + start.height
                height = (start.height - stepsY).coerceIn(limits.minHeight, minOf(limits.maxHeight, bottomEdge))
                row = bottomEdge - height
            }
        }
        return CellBox(col, row, width, height)
    }
}
