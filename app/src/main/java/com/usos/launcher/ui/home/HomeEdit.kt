package com.usos.launcher.ui.home

import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.usos.launcher.ui.theme.themed
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.usos.launcher.data.sections.Cell
import com.usos.launcher.data.sections.MosaicEdit
import com.usos.launcher.data.sections.PackResult
import com.usos.launcher.data.sections.PlacedApp
import com.usos.launcher.data.sections.SectionMosaic
import com.usos.launcher.data.sections.CellBox
import com.usos.launcher.data.sections.ResizeHandle
import com.usos.launcher.data.sections.ResizeLimits
import com.usos.launcher.data.sections.ResizeMath
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.data.info.InfoKind
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalOnAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.BackGlyph
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.dashedBorder
import com.usos.launcher.ui.theme.pressGestures

/** Acciones del modo edición de inicio. */
class EditActions(
    val onExit: () -> Unit,
    val onPlace: (key: String, section: Section, cell: Cell) -> Unit,
    val onToSection: (key: String, section: Section) -> Unit,
    val onRemove: (key: String) -> Unit,
    val onResize: (key: String, size: TileSize) -> Unit,
    val onResizeInfo: (kind: InfoKind, size: TileSize) -> Unit,
    /** Redimensiona y guarda el orden nuevo de la sección (estirar a la izquierda/arriba cambia su posición). */
    val onResizeAt: (key: String, section: Section, size: TileSize, order: List<String>) -> Unit,
    val onRows: (section: Section, delta: Int) -> Unit,
    val onDetail: (key: String) -> Unit,
)

/** Geometría de una rejilla abierta, para traducir el dedo (coordenadas de raíz) a celdas. */
class GridGeom(
    val coords: LayoutCoordinates, val columns: Int, val rows: Int,
    val stepX: Float, val stepY: Float,
)

fun LayoutCoordinates.rootRect(): Rect? = if (isAttached) Rect(positionInRoot(), size.toSize()) else null

/** Colocación en vivo de una sección mientras se arrastra o redimensiona (lo que pasaría si se soltara ahora). */
class EditPreview(val section: Section, val key: String, val layout: PackResult)

/**
 * Estado de la edición de inicio: selección (bolitas / tiradores), arrastre de una pieza y vista previa.
 * Las posiciones se guardan en coordenadas de raíz.
 */
@Stable
class EditState {
    // Selección
    var selectedKey by mutableStateOf<String?>(null)
    var selectedSection: Section? = null
    var selectedRect by mutableStateOf<Rect?>(null)
    var resizing by mutableStateOf(false)

    // Arrastre
    var dragKey by mutableStateOf<String?>(null)
    var dragSection: Section? = null
    var dragTile by mutableStateOf<TileUi?>(null)
    var dragSize by mutableStateOf(IntSize.Zero)
    var pointer by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
    var hoverSection by mutableStateOf<Section?>(null)
    var hoverRemove by mutableStateOf(false)
    var hoverCell by mutableStateOf<Cell?>(null)

    // Redimensionado
    var resizeKey by mutableStateOf<String?>(null)
    var resizeSize by mutableStateOf<TileSize?>(null)
    var frozenRect by mutableStateOf<Rect?>(null)
    private var resizeFrom = TileSize.S1X1
    private var resizeStart = CellBox(0, 0, 1, 1)
    private var resizeOrder: List<String>? = null

    // Vista previa: la de ahora y la última soltada (se mantiene hasta que llega el estado guardado, sin parpadeo)
    var preview by mutableStateOf<EditPreview?>(null)
    var held by mutableStateOf<EditPreview?>(null)

    /** Secciones tal como las dibuja la vista ahora (las actualiza HomePage en cada composición). */
    var sections: Map<Section, SectionUi> = emptyMap()
    var viewport: LayoutCoordinates? = null
    val headers = HashMap<Section, LayoutCoordinates>()
    val grids = HashMap<Section, GridGeom>()
    var removeZone: LayoutCoordinates? = null

    val dragging get() = dragKey != null

    fun select(key: String, section: Section) {
        if (selectedKey != key) { selectedKey = key; selectedSection = section; selectedRect = null; resizing = false }
    }

    fun clearSelection() {
        selectedKey = null; selectedSection = null; selectedRect = null; resizing = false
        resizeKey = null; resizeSize = null; frozenRect = null; preview = null
    }

    fun reset() { cancel(); clearSelection(); held = null }

    fun start(key: String, section: Section, tile: TileUi, tileOrigin: Offset, grab: Offset, size: IntSize) {
        dragKey = key; dragSection = section; dragTile = tile; dragSize = size
        this.grab = grab
        pointer = tileOrigin + grab
        hoverCell = null
        updateHover()
    }

    fun updateHover() {
        val p = pointer
        val inList = viewport?.rootRect()?.contains(p) != false
        hoverRemove = removeZone?.rootRect()?.contains(p) == true
        hoverSection = if (!inList) null else
            headers.entries.firstOrNull { (s, c) -> s != dragSection && c.rootRect()?.contains(p) == true }?.key
        val sec = dragSection
        val g = sec?.let { grids[it] }
        hoverCell = if (hoverRemove || hoverSection != null || g == null || !g.coords.isAttached) null else {
            val o = g.coords.positionInRoot()
            MosaicEdit.hoverCell(hoverCell, p.x - o.x, p.y - o.y, g.stepX, g.stepY, g.columns, g.rows)
        }
        preview = run {
            val key = dragKey ?: return@run null
            val cell = hoverCell ?: return@run null
            val ui = sections[sec ?: return@run null] ?: return@run null
            MosaicEdit.previewMove(
                ui.columns, ui.rows, ui.infoSize, ui.entries, ui.placements, key, cell,
                SectionMosaic.reservesFreeze(ui.section),
            )?.let { EditPreview(ui.section, key, it.layout) }
        }
    }

    fun drop(actions: EditActions) {
        val key = dragKey
        val section = dragSection
        if (key != null && section != null) {
            val toSection = hoverSection
            val cell = hoverCell
            val pv = preview
            when {
                hoverRemove -> { actions.onRemove(key); clearSelection() }
                toSection != null -> { actions.onToSection(key, toSection); clearSelection() }
                cell != null && pv != null -> { held = pv; actions.onPlace(key, section, cell) }
                // Fuera de una zona válida (o sin sitio): la pieza vuelve a su lugar sin cambios.
            }
        }
        cancel()
    }

    fun cancel() {
        dragKey = null; dragSection = null; dragTile = null
        hoverSection = null; hoverRemove = false; hoverCell = null; preview = null
    }

    // ---- Redimensionado ----

    fun beginResize() {
        val key = selectedKey ?: return
        val sec = selectedSection ?: return
        val pl = sections[sec]?.placements?.firstOrNull { it.key == key } ?: return
        resizeFrom = TileSize.entries.firstOrNull { it.colSpan == pl.colSpan && it.rowSpan == pl.rowSpan } ?: TileSize.S1X1
        resizeStart = CellBox(pl.col, pl.row, pl.colSpan, pl.rowSpan)
        resizeOrder = null
        resizeKey = key; resizeSize = resizeFrom; frozenRect = selectedRect
    }

    /** Tiradores de la pieza seleccionada según los bordes de la rejilla que toca. */
    fun handlesForSelected(): Set<ResizeHandle> {
        val key = selectedKey ?: return emptySet()
        val ui = selectedSection?.let { sections[it] } ?: return emptySet()
        val pl = ui.placements.firstOrNull { it.key == key } ?: return emptySet()
        return ResizeMath.handlesFor(CellBox(pl.col, pl.row, pl.colSpan, pl.rowSpan), limitsOf(ui))
    }

    private fun limitsOf(ui: SectionUi) = ResizeLimits(columns = ui.columns, rows = ui.rows, maxWidth = MAX_TILE_SPAN, maxHeight = MAX_TILE_SPAN)

    fun updateResize(dx: Float, dy: Float, handle: ResizeHandle) {
        val key = resizeKey ?: return
        val sec = selectedSection ?: return
        val g = grids[sec] ?: return
        val ui = sections[sec] ?: return
        val box = ResizeMath.resize(resizeStart, handle, dx, dy, g.stepX, g.stepY, limitsOf(ui))
        val size = TileSize.entries.firstOrNull { it.colSpan == box.width && it.rowSpan == box.height } ?: return
        val moved = box.col != resizeStart.col || box.row != resizeStart.row
        if (size == resizeSize && !moved) return
        resizeSize = size
        if (size == resizeFrom) { preview = null; resizeOrder = null; return }
        val reserve = SectionMosaic.reservesFreeze(sec)
        if (key == SectionMosaic.INFO_KEY) {
            // La pieza de información va siempre primera: solo cambia su tamaño (y no puede ocultar a otras).
            val before = SectionMosaic.layout(ui.columns, ui.rows, ui.infoSize, ui.entries, reserve)
            preview = MosaicEdit.previewResize(ui.columns, ui.rows, ui.infoSize, ui.entries, key, size, reserve)
                ?.takeIf { MosaicEdit.noNewlySkipped(before, it) }?.let { EditPreview(sec, key, it) }
            resizeOrder = null
        } else {
            val p = MosaicEdit.previewResizeAt(
                ui.columns, ui.rows, ui.infoSize, ui.entries, ui.placements, key, size, Cell(box.col, box.row), reserve,
            )
            preview = p?.let { EditPreview(sec, key, it.layout) }
            resizeOrder = p?.order
        }
    }

    fun endResize(actions: EditActions, completed: Boolean) {
        val key = resizeKey
        val sec = selectedSection
        val size = resizeSize
        val pv = preview
        if (completed && key != null && sec != null && size != null && size != resizeFrom && pv != null) {
            held = pv
            val order = resizeOrder
            if (key == SectionMosaic.INFO_KEY) sections[sec]?.info?.let { actions.onResizeInfo(it.kind, size) }
            else if (order != null) actions.onResizeAt(key, sec, size, order)
            else actions.onResize(key, size)
        }
        resizeKey = null; resizeSize = null; frozenRect = null; preview = null; resizeOrder = null
    }

    private companion object {
        /** Las piezas de inicio miden 1 o 2 celdas por lado (TileSize). */
        const val MAX_TILE_SPAN = 2
    }
}

/** Barra superior fina «EDITANDO · LISTO». */
@Composable
fun EditTopBar(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier.fillMaxWidth().height(34.dp).background(UsosColors.Background)
            .padding(horizontal = UsosDimens.ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(stringResource(R.string.edit_editing), UsosType.Button, accent)
        Box(Modifier.height(34.dp).pressGestures { onDone() }.padding(start = 24.dp), contentAlignment = Alignment.CenterEnd) {
            UsosText(stringResource(R.string.done), UsosType.Button, UsosColors.Text)
        }
    }
}

/** Zona «QUITAR DE INICIO» (aparece sobre la barra de búsqueda mientras se arrastra). */
@Composable
fun RemoveZone(hover: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(UsosDimens.RadiusSearch)
    Box(
        modifier.fillMaxWidth().height(52.dp)
            .background(if (hover) accent else UsosColors.Background, shape)
            .dashedBorder(if (hover) accent else UsosColors.Text, UsosDimens.RadiusSearch),
        contentAlignment = Alignment.Center,
    ) {
        UsosText(stringResource(R.string.edit_remove_from_home), UsosType.Button, if (hover) LocalOnAccent.current else UsosColors.Text)
    }
}

/** Control «− FILAS n +». */
@Composable
fun RowsControl(rows: Int, onDelta: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        UsosPill("−", filled = false, onClick = { onDelta(-1) }, modifier = Modifier.width(64.dp))
        UsosText(stringResource(R.string.edit_rows, rows), UsosType.Button, UsosColors.Text, Modifier.padding(horizontal = 20.dp))
        UsosPill("+", filled = false, onClick = { onDelta(1) }, modifier = Modifier.width(64.dp))
    }
}

/** Lista para añadir una app a la sección: primero las de la sección fuera del mosaico y «OTRAS…» para cualquiera. */
/**
 * Añadir al mosaico de [section]: pestañas por sección (la propia primero; [extraTab] = pestaña PIEZAS con piezas
 * propias si se pasa) y buscador. En la propia solo salen las apps que no se ven ya en el mosaico.
 */
@Composable
fun AddPicker(
    section: Section,
    groups: List<Pair<Section, List<PlacedApp>>>,
    shownKeys: Set<String>,
    onPick: (PlacedApp) -> Unit,
    onClose: () -> Unit,
    extraTab: String? = null,
    extraContent: (@Composable () -> Unit)? = null,
) {
    BackHandler { onClose() }
    val accent = LocalAccent.current
    val tabs = remember(groups, section) {
        listOf(section) + groups.map { it.first }.filter { it != section }
    }
    var tab by remember { mutableStateOf<Section?>(section) } // null = pestaña extra
    var query by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(UsosColors.Background)
            .pressGestures { }
            .padding(horizontal = UsosDimens.ScreenMargin),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 48.dp).height(UsosDimens.MinTouch),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackGlyph(onClose)
            UsosText(stringResource(R.string.edit_add_to, stringResource(section.titleRes).uppercase()), UsosType.Button, accent)
        }
        val fieldShape = androidx.compose.foundation.shape.RoundedCornerShape(UsosDimens.RadiusPiece)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp)
                .background(UsosColors.Surface, fieldShape)
                .border(1.dp, UsosColors.Border, fieldShape).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) UsosText(stringResource(R.string.dock_search_hint), UsosType.SearchHint, UsosColors.TextSecondary)
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = UsosType.SearchInput.themed().copy(color = UsosColors.Text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val searching = query.isNotBlank()
        if (!searching) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
                if (extraTab != null && extraContent != null) TabPill(extraTab, tab == null) { tab = null }
                tabs.forEach { s -> TabPill(stringResource(s.titleRes).uppercase(), tab == s) { tab = s } }
            }
        }
        if (!searching && tab == null && extraContent != null) {
            Box(Modifier.fillMaxSize()) { extraContent() }
            return@Column
        }
        val rows: List<Pair<Section, PlacedApp>> = remember(groups, tab, query, shownKeys) {
            if (searching) {
                groups.flatMap { (s, l) -> l.map { s to it } }
                    .mapNotNull { p -> com.usos.launcher.ui.search.SearchFilter.score(p.second.app.label, com.usos.launcher.ui.search.SearchFilter.normalize(query))?.let { p to it } }
                    .sortedWith(compareBy({ it.second }, { it.first.second.app.label.lowercase() })).map { it.first }
            } else {
                val s = tab ?: section
                groups.firstOrNull { it.first == s }?.second.orEmpty()
                    .filter { s != section || it.config.key !in shownKeys }
                    .sortedBy { it.app.label.lowercase() }.map { s to it }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (rows.isEmpty()) {
                item { UsosText(stringResource(R.string.edit_no_apps_left), UsosType.Empty, UsosColors.TextSecondary, Modifier.padding(vertical = 8.dp)) }
            }
            items(rows, key = { it.second.config.key }) { (s, app) ->
                PickerRow(app, if (s != section) stringResource(s.titleRes).uppercase() else null, onPick)
            }
        }
    }
}

@Composable
private fun TabPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(end = 8.dp)) { UsosPill(text, filled = active, onClick = onClick) }
}

@Composable
private fun PickerRow(app: PlacedApp, tag: String?, onPick: (PlacedApp) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp).pressGestures { onPick(app) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
            com.usos.launcher.ui.theme.AppIconOrNull(app.app, 26.dp, UsosColors.Text) { UsosText(app.code, UsosType.SmallCode, UsosColors.Text) }
        }
        UsosText(app.app.label, UsosType.Pill, UsosColors.Text, Modifier.weight(1f))
        if (tag != null) UsosText(tag, UsosType.SmallTag, UsosColors.TextSecondary)
    }
}
