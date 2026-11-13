package com.usos.launcher.ui.widgets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.usos.launcher.data.sections.MosaicPacker
import com.usos.launcher.data.sections.PackItem
import com.usos.launcher.data.sections.PackResult
import kotlin.math.roundToInt

/**
 * Widget colocado: el id lo asigna AppWidgetHost. `cols`/`rows` = tamaño en celdas (colSpan/rowSpan; 0 = sin migrar:
 * ancho completo, alto del `heightDp` antiguo). `col`/`row` = posición de la esquina superior izquierda en la
 * rejilla; -1 = sin posición (migración o alta nueva: la página la calcula una vez y la persiste).
 */
@Serializable
data class HostedWidget(
    val id: Int, val heightDp: Int = 0, val cols: Int = 0, val rows: Int = 0, val col: Int = -1, val row: Int = -1,
    /** [WidgetKind] por nombre; los guardados sin `kind` (anteriores a 1.0.6) son ANDROID. */
    val kind: String = WidgetKind.ANDROID.name,
    /** Ajustes de una pieza propia de Usos como `clave=valor;clave=valor` (vacío en las de Android). */
    val config: String = "",
) {
    val widgetKind: WidgetKind get() = WidgetKind.fromName(kind)
}

/** Caja de un widget en la rejilla: esquina [col],[row] y tamaño [w]x[h] en celdas. */
data class GBox(val id: Int, val col: Int, val row: Int, val w: Int, val h: Int) {
    val bottom get() = row + h
    fun overlaps(o: GBox) = col < o.col + o.w && o.col < col + w && row < o.bottom && o.row < bottom
}

/**
 * Rejilla libre de widgets (pura): columnas fijas y filas ilimitadas hacia abajo. Regla única de colocación:
 * el widget movido o redimensionado se queda donde se pide (acotado a la rejilla) y los que se solapan con él se
 * EMPUJAN HACIA ABAJO justo por debajo del que los empuja, en cascada (en orden de fila y columna). Nunca se mueven
 * hacia arriba ni de lado. No se compacta nada: los huecos vacíos se quedan (más predecible).
 */
object WidgetGrid {
    /** Acota tamaños y columna a [columns], fila >= 0; luego resuelve solapes empujando hacia abajo; [anchor] no se mueve. */
    fun normalize(boxes: List<GBox>, columns: Int, anchor: Int? = null): List<GBox> {
        val cols = columns.coerceAtLeast(1)
        val clamped = boxes.map {
            val w = it.w.coerceIn(1, cols)
            it.copy(w = w, h = it.h.coerceAtLeast(1), col = it.col.coerceIn(0, cols - w), row = it.row.coerceAtLeast(0))
        }
        val settled = ArrayList<GBox>()
        clamped.firstOrNull { it.id == anchor }?.let { settled += it }
        clamped.filter { it.id != anchor }.sortedWith(compareBy({ it.row }, { it.col }))
            .forEach { b0 ->
                var b = b0
                while (true) {
                    val hit = settled.filter { it.overlaps(b) }
                    if (hit.isEmpty()) break
                    b = b.copy(row = hit.maxOf { it.bottom })
                }
                settled += b
            }
        val byId = settled.associateBy { it.id }
        return clamped.map { byId.getValue(it.id) }
    }

    /** Pone [id] con esquina en ([col],[row]) (acotada a la rejilla) y empuja hacia abajo a los solapados. */
    fun place(boxes: List<GBox>, id: Int, col: Int, row: Int, columns: Int): List<GBox> =
        normalize(boxes.map { if (it.id == id) it.copy(col = col, row = row) else it }, columns, id)

    /** Cambia [id] a [w]x[h] (acotado al ancho de la rejilla) sin mover su esquina y empuja hacia abajo a los afectados. Nunca se rechaza. */
    fun resize(boxes: List<GBox>, id: Int, w: Int, h: Int, columns: Int): List<GBox> =
        normalize(boxes.map { if (it.id == id) it.copy(w = w, h = h) else it }, columns, id)

    /** Primera posición libre en orden de lectura donde cabe [w]x[h]; las filas son ilimitadas, así que siempre hay. */
    fun firstFree(boxes: List<GBox>, w: Int, h: Int, columns: Int): Pair<Int, Int> {
        val ww = w.coerceIn(1, columns.coerceAtLeast(1))
        var row = 0
        while (true) {
            for (col in 0..(columns - ww).coerceAtLeast(0)) {
                val t = GBox(-1, col, row, ww, h)
                if (boxes.none { it.overlaps(t) }) return col to row
            }
            row++
        }
    }
}


/** Tamaño de un widget en celdas. */
data class WidgetCells(val cols: Int, val rows: Int)

/** Calculos puros de tamano, colocacion y lista. */
object WidgetSizing {
    const val MIN_BASE_DP = 48

    /** Alto inicial en dp: el mayor entre minHeight declarado y el de targetCellHeight (n*70-30 dp). */
    fun defaultDp(minHeightDp: Int, targetCellHeight: Int): Int =
        maxOf(minHeightDp, MIN_BASE_DP, if (targetCellHeight > 0) targetCellHeight * 70 - 30 else 0)

    const val CELL_DP = 95
    const val GAP_DP = 10
    const val MIN_ROWS = 1
    const val MAX_ROWS = 6

    /** Filas de celda mas cercanas a un alto en dp (1..6). */
    fun dpToRows(dp: Int): Int = ((dp + GAP_DP).toFloat() / (CELL_DP + GAP_DP)).roundToInt().coerceIn(MIN_ROWS, MAX_ROWS)

    /** Tamaño efectivo: ancho = todas las columnas si no se ha migrado; filas = las del alto antiguo o [defaultRows]. */
    fun cells(w: HostedWidget, columns: Int, defaultRows: Int): WidgetCells {
        val c = if (w.cols <= 0) columns else w.cols.coerceIn(1, columns)
        val r = when {
            w.rows > 0 -> w.rows
            w.heightDp > 0 -> dpToRows(w.heightDp)
            else -> defaultRows
        }
        return WidgetCells(c, r.coerceIn(MIN_ROWS, MAX_ROWS))
    }

    /**
     * Celdas mínimas reales de un widget en un eje: el mínimo declarado ([minDp]: minResizeWidth/Height si existe,
     * si no minWidth/Height) redondeado hacia abajo a celdas de [cellDp] con separación [gapDp]; 1..[max].
     */
    fun minCells(minDp: Int, cellDp: Float, gapDp: Float, max: Int): Int =
        if (minDp <= 0) 1 else ((minDp + gapDp) / (cellDp + gapDp)).toInt().coerceIn(1, max.coerceAtLeast(1))

    /**
     * Tamaño encajado a celdas tras arrastrar el tirador [dxPx], [dyPx]: [minCols]..[columns] de ancho y
     * [minRows]..6 de alto. Cualquier ancho en celdas por encima del mínimo es válido.
     */
    fun snapCells(
        cur: WidgetCells, dxPx: Float, dyPx: Float, stepX: Float, stepY: Float, columns: Int,
        minCols: Int = 1, minRows: Int = MIN_ROWS,
    ): WidgetCells =
        WidgetCells(
            (cur.cols + (dxPx / stepX).roundToInt()).coerceIn(minCols.coerceIn(1, columns), columns),
            (cur.rows + (dyPx / stepY).roundToInt()).coerceIn(minRows.coerceIn(MIN_ROWS, MAX_ROWS), MAX_ROWS),
        )

    /**
     * Cajas actuales: las guardadas con posición se respetan (acotadas y sin solapes); las que no la tienen
     * ([HostedWidget.col] < 0) se colocan: si ninguna tiene posición, con el empaquetado denso antiguo (el mismo
     * resultado que se veía, migración única); si no, en la primera posición libre en orden de lectura.
     */
    fun resolve(widgets: List<HostedWidget>, columns: Int, defaultRows: (Int) -> Int): List<GBox> {
        val sizes = widgets.map { it.id to cells(it, columns, defaultRows(it.id)) }
        if (widgets.none { it.col >= 0 && it.row >= 0 }) {
            val byId = layout(columns, sizes).placements.associateBy { it.key.toInt() }
            return sizes.map { (id, c) -> byId[id]?.let { GBox(id, it.col, it.row, c.cols, c.rows) } ?: GBox(id, 0, 0, c.cols, c.rows) }
                .let { WidgetGrid.normalize(it, columns) }
        }
        val placed = ArrayList<GBox>()
        val pending = ArrayList<Pair<HostedWidget, WidgetCells>>()
        widgets.forEachIndexed { i, w ->
            if (w.col >= 0 && w.row >= 0) placed += GBox(w.id, w.col, w.row, sizes[i].second.cols, sizes[i].second.rows)
            else pending += w to sizes[i].second
        }
        var cur = WidgetGrid.normalize(placed, columns)
        pending.forEach { (w, c) ->
            val (col, row) = WidgetGrid.firstFree(cur, c.cols, c.rows, columns)
            cur = cur + GBox(w.id, col, row, c.cols.coerceAtMost(columns), c.rows)
        }
        val byId = cur.associateBy { it.id }
        return widgets.map { byId.getValue(it.id) }
    }

    /** Colocación densa por orden (MosaicPacker); las filas disponibles son la suma de alturas, así que todo cabe. */
    fun layout(columns: Int, sizes: List<Pair<Int, WidgetCells>>): PackResult =
        MosaicPacker.pack(columns, sizes.sumOf { it.second.rows }.coerceAtLeast(1), sizes.map { (id, c) -> PackItem(id.toString(), c.cols, c.rows) })

    /** Ids que el host tiene asignados y que ya no estan guardados: se borran al arrancar. */
    fun orphans(hostIds: Collection<Int>, saved: List<HostedWidget>): List<Int> {
        val keep = saved.map { it.id }.toSet()
        return hostIds.filter { it !in keep }
    }

    /** Aplica posición y tamaño de [boxes] a los widgets guardados (los demás quedan igual). */
    fun withBoxes(list: List<HostedWidget>, boxes: List<GBox>): List<HostedWidget> {
        val byId = boxes.associateBy { it.id }
        return list.map { w -> byId[w.id]?.let { w.copy(col = it.col, row = it.row, cols = it.w, rows = it.h, heightDp = 0) } ?: w }
    }
}

/** Widgets colocados, como un JSON en DataStore. */
class WidgetStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("hosted_widgets")
    private val json = com.usos.launcher.data.PersistJson
    private val serializer = ListSerializer(HostedWidget.serializer())

    val widgets: Flow<List<HostedWidget>> = dataStore.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun current(): List<HostedWidget> = widgets.first()

    suspend fun update(transform: (List<HostedWidget>) -> List<HostedWidget>) {
        dataStore.edit { prefs ->
            val cur = decode(prefs[key])
            val next = transform(cur)
            if (next != cur) prefs[key] = json.encodeToString(serializer, next)
        }
    }

    private fun decode(raw: String?): List<HostedWidget> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
