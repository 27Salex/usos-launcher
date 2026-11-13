package com.usos.launcher.ui.home

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
import com.usos.launcher.ui.theme.ResizeAxes
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
        resizeKey = key; resizeSize = resizeFrom; frozenRect = selectedRect
    }

    fun updateResize(dx: Float, dy: Float, axes: ResizeAxes) {
        val key = resizeKey ?: return
        val sec = selectedSection ?: return
        val g = grids[sec] ?: return
        val ui = sections[sec] ?: return
        val size = MosaicEdit.snapSize(resizeFrom, if (axes.x) dx else 0f, if (axes.y) dy else 0f, g.stepX, g.stepY)
        if (size == resizeSize) return
        resizeSize = size
        preview = if (size == resizeFrom) null else
            MosaicEdit.previewResize(
                ui.columns, ui.rows, ui.infoSize, ui.entries, key, size, SectionMosaic.reservesFreeze(sec),
            )?.let { EditPreview(sec, key, it) }
    }

    fun endResize(actions: EditActions, completed: Boolean) {
        val key = resizeKey
        val sec = selectedSection
        val size = resizeSize
        val pv = preview
        if (completed && key != null && sec != null && size != null && size != resizeFrom && pv != null) {
            held = pv
            if (key == SectionMosaic.INFO_KEY) sections[sec]?.info?.let { actions.onResizeInfo(it.kind, size) }
            else actions.onResize(key, size)
        }
        resizeKey = null; resizeSize = null; frozenRect = null; preview = null
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
        UsosText("EDITANDO", UsosType.Button, accent)
        Box(Modifier.height(34.dp).pressGestures { onDone() }.padding(start = 24.dp), contentAlignment = Alignment.CenterEnd) {
            UsosText("LISTO", UsosType.Button, UsosColors.Text)
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
        UsosText("QUITAR DE INICIO", UsosType.Button, if (hover) LocalOnAccent.current else UsosColors.Text)
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
        UsosText("FILAS $rows", UsosType.Button, UsosColors.Text, Modifier.padding(horizontal = 20.dp))
        UsosPill("+", filled = false, onClick = { onDelta(1) }, modifier = Modifier.width(64.dp))
    }
}

/** Lista para añadir una app a la sección: primero las de la sección fuera del mosaico y «OTRAS…» para cualquiera. */
@Composable
fun AddPicker(
    section: Section,
    own: List<PlacedApp>,
    others: List<Pair<Section, PlacedApp>>,
    onPick: (PlacedApp) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    var showOthers by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxSize().background(UsosColors.Background)
            .pressGestures { }
            .padding(horizontal = UsosDimens.ScreenMargin),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 48.dp).height(UsosDimens.MinTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                BackGlyph(onClose)
                UsosText("AÑADIR A ${section.title.uppercase()}", UsosType.Button, accent)
            }
            Box(Modifier.height(UsosDimens.MinTouch).pressGestures { onClose() }.padding(start = 16.dp), contentAlignment = Alignment.CenterEnd) {
                UsosText("CERRAR", UsosType.Button, UsosColors.Text)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (!showOthers) {
                if (own.isEmpty()) {
                    item { UsosText("No quedan apps de esta sección.", UsosType.Empty, UsosColors.TextSecondary, Modifier.padding(vertical = 8.dp)) }
                }
                items(own, key = { it.config.key }) { PickerRow(it, null, onPick) }
                item {
                    Box(Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 8.dp).pressGestures { showOthers = true }, contentAlignment = Alignment.CenterStart) {
                        UsosText("OTRAS…", UsosType.Button, accent)
                    }
                }
            } else {
                items(others, key = { it.second.config.key }) { (s, app) -> PickerRow(app, s.title.uppercase(), onPick) }
            }
        }
    }
}

@Composable
private fun PickerRow(app: PlacedApp, tag: String?, onPick: (PlacedApp) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp).pressGestures { onPick(app) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UsosText(app.code, UsosType.SmallCode, UsosColors.Text, Modifier.width(40.dp))
        UsosText(app.app.label, UsosType.Pill, UsosColors.Text, Modifier.weight(1f))
        if (tag != null) UsosText(tag, UsosType.SmallTag, UsosColors.TextSecondary)
    }
}
