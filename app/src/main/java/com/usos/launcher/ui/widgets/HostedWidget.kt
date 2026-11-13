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

/** Widget colocado: el id lo asigna AppWidgetHost. Tamaño en celdas del mosaico; cols/rows 0 = sin migrar (ancho completo, alto del `heightDp` antiguo). El orden es el de la lista. */
@Serializable
data class HostedWidget(val id: Int, val heightDp: Int = 0, val cols: Int = 0, val rows: Int = 0)

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

    /** Orden tentativo y colocación si [dragged] (id) se soltara sobre [target]; sirve para la vista previa. */
    fun previewMove(
        columns: Int, sizes: List<Pair<Int, WidgetCells>>, placements: List<com.usos.launcher.data.sections.Placement>,
        dragged: Int, target: com.usos.launcher.data.sections.Cell,
    ): Pair<List<Int>, PackResult> {
        val order = com.usos.launcher.data.sections.MosaicEdit
            .reorder(sizes.map { it.first.toString() }, placements, dragged.toString(), target, columns).map { it.toInt() }
        val byId = sizes.toMap()
        return order to layout(columns, order.mapNotNull { id -> byId[id]?.let { id to it } })
    }

    /** Colocación densa por orden (MosaicPacker); las filas disponibles son la suma de alturas, así que todo cabe. */
    fun layout(columns: Int, sizes: List<Pair<Int, WidgetCells>>): PackResult =
        MosaicPacker.pack(columns, sizes.sumOf { it.second.rows }.coerceAtLeast(1), sizes.map { (id, c) -> PackItem(id.toString(), c.cols, c.rows) })

    /** Ids que el host tiene asignados y que ya no estan guardados: se borran al arrancar. */
    fun orphans(hostIds: Collection<Int>, saved: List<HostedWidget>): List<Int> {
        val keep = saved.map { it.id }.toSet()
        return hostIds.filter { it !in keep }
    }

    /** Reordena según [order] (ids); los que falten conservan su posición relativa al final. */
    fun reorder(list: List<HostedWidget>, order: List<Int>): List<HostedWidget> {
        val byId = list.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        return ordered + list.filter { it.id !in order }
    }

    fun withCells(list: List<HostedWidget>, id: Int, cols: Int, rows: Int): List<HostedWidget> =
        list.map { if (it.id == id) it.copy(cols = cols, rows = rows, heightDp = 0) else it }
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
