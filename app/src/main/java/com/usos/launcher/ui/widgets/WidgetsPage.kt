package com.usos.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.usos.launcher.R
import com.usos.launcher.data.sections.Cell
import com.usos.launcher.data.sections.MosaicEdit
import com.usos.launcher.data.sections.MosaicPacker
import com.usos.launcher.data.sections.PackResult
import com.usos.launcher.ui.home.rootRect
import com.usos.launcher.ui.theme.Bubble
import com.usos.launcher.ui.theme.BubbleKind
import com.usos.launcher.ui.theme.BubbleRow
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.ResizeHandles
import com.usos.launcher.ui.theme.SettingsGlyph
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.dashedBorder
import com.usos.launcher.ui.theme.pressGestures
import com.usos.launcher.ui.theme.tapOutside
import com.usos.launcher.ui.theme.usosTween
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class WidgetsActions(
    val onAdd: (AppWidgetProviderInfo) -> Unit,
    val onReorder: (order: List<Int>) -> Unit,
    val onResize: (id: Int, cols: Int, rows: Int) -> Unit,
    val onRemove: (id: Int) -> Unit,
    val onReconfigure: (id: Int) -> Unit,
)

private val ColGap = 11.dp
private val RowGap = 10.dp

/**
 * Pagina izquierda del pager: cabecera (título, EDITAR/AÑADIR, ajustes) y rejilla de widgets igual que el mosaico de
 * inicio. Fuera de edición los widgets no se mueven ni redimensionan; en edición un toque selecciona un widget y
 * muestra bolitas (REDIMENSIONAR, RECONFIGURAR si tiene configuración, QUITAR); arrastrar el seleccionado lo mueve.
 * [editing] vive fuera (HomeScreen) para bloquear el pager mientras se edita.
 */
@Composable
fun WidgetsPage(
    widgets: List<HostedWidget>,
    columns: Int,
    grayscale: Boolean,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    actions: WidgetsActions,
    topSpacing: Dp,
    onOpenSettings: () -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var resizing by remember { mutableStateOf(false) }
    var selectedRect by remember { mutableStateOf<Rect?>(null) }
    var frozenRect by remember { mutableStateOf<Rect?>(null) }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    var hoverCell by remember { mutableStateOf<Cell?>(null) }
    var previewOrder by remember { mutableStateOf<List<Int>?>(null) }
    var preview by remember { mutableStateOf<PackResult?>(null) }
    var held by remember { mutableStateOf<PackResult?>(null) }
    var resizeId by remember { mutableStateOf<Int?>(null) }
    var resizeFrom by remember { mutableStateOf(WidgetCells(1, 1)) }
    var resizeCells by remember { mutableStateOf<WidgetCells?>(null) }
    var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var listCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalContext.current.resources.displayMetrics.density
    val animations = LocalAnimationsEnabled.current
    val accent = LocalAccent.current
    val haptic = LocalHapticFeedback.current

    fun clearSelection() {
        selectedId = null; selectedRect = null; frozenRect = null; resizing = false
        resizeId = null; resizeCells = null; preview = null
    }
    BackHandler(enabled = editing) { if (selectedId != null) clearSelection() else onEditingChange(false) }
    LaunchedEffect(editing) {
        if (!editing) { clearSelection(); dragId = null; held = null; hoverCell = null }
    }
    // Lo soltado se mantiene hasta que llega la lista guardada (o 900 ms): así no se vuelve atrás un instante.
    LaunchedEffect(widgets) { held = null }
    LaunchedEffect(held) { if (held != null) { delay(900); held = null } }

    fun defaultRows(id: Int): Int {
        val info = manager.getAppWidgetInfo(id) ?: return 1
        fun dp(px: Int) = (px / density).toInt()
        return WidgetSizing.dpToRows(WidgetSizing.defaultDp(dp(info.minHeight), info.targetCellHeight))
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { pageCoords = it }
            .tapOutside(
                enabled = editing && selectedId != null,
                inside = { frozenRect ?: selectedRect },
                origin = { pageCoords?.positionInRoot() ?: Offset.Zero },
            ) { if (dragId == null) clearSelection() },
    ) {
        val cell = ((maxWidth - UsosDimens.ScreenMargin * 2) - ColGap * (columns - 1)) / columns
        val tileH = MosaicPacker.tileHeightDp(columns, (maxWidth - UsosDimens.ScreenMargin * 2).value).dp
        val stepX = with(LocalDensity.current) { (cell + ColGap).toPx() }
        val stepY = with(LocalDensity.current) { (tileH + RowGap).toPx() }
        val sizes = widgets.map { it.id to WidgetSizing.cells(it, columns, defaultRows(it.id)) }
        val pack = WidgetSizing.layout(columns, sizes)
        val sizeById = sizes.toMap()
        val totalRows = sizes.sumOf { it.second.rows }.coerceAtLeast(1)
        val eff = if (editing) preview ?: held else null
        val placements = eff?.placements ?: pack.placements
        val rowsUsed = maxOf(pack.rowsUsed, eff?.rowsUsed ?: 0)
        val widgetsNow by rememberUpdatedState(widgets)
        val packNow by rememberUpdatedState(pack)
        val sizesNow by rememberUpdatedState(sizes)

        fun spanW(cs: Int) = cell * cs + ColGap * (cs - 1)
        fun spanH(rs: Int) = tileH * rs + RowGap * (rs - 1)

        fun minCellsOf(id: Int): Pair<Int, Int> {
            val info = manager.getAppWidgetInfo(id) ?: return 1 to 1
            fun dp(px: Int) = (px / density).toInt()
            val minW = if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
            val minH = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
            return WidgetSizing.minCells(dp(minW), cell.value, ColGap.value, columns) to
                WidgetSizing.minCells(dp(minH), tileH.value, RowGap.value, WidgetSizing.MAX_ROWS)
        }

        fun updateDragPreview() {
            val id = dragId ?: return
            val g = gridCoords?.takeIf { it.isAttached } ?: return
            val o = g.positionInRoot()
            val c = MosaicEdit.hoverCell(hoverCell, dragPointer.x - o.x, dragPointer.y - o.y, stepX, stepY, columns, totalRows)
            hoverCell = c
            val pv = c?.let { WidgetSizing.previewMove(columns, sizesNow, packNow.placements, id, it) }
            previewOrder = pv?.first
            preview = pv?.second
        }

        fun dropDrag() {
            val order = previewOrder
            val pv = preview
            if (order != null && pv != null && order != widgetsNow.map { it.id }) {
                held = pv
                actions.onReorder(order)
            }
            dragId = null; hoverCell = null; previewOrder = null; preview = null
        }


        Column(Modifier.fillMaxSize().padding(horizontal = UsosDimens.ScreenMargin)) {
            Spacer(Modifier.height(topSpacing))
            Row(Modifier.fillMaxWidth().height(UsosDimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
                UsosText(stringResource(R.string.widgets_title), UsosType.PanelTitle, UsosColors.Text, Modifier.weight(1f))
                UsosPill(stringResource(if (editing) R.string.done else R.string.widgets_edit), filled = editing, onClick = { onEditingChange(!editing) })
                if (editing) {
                    Spacer(Modifier.size(8.dp))
                    UsosPill(stringResource(R.string.widgets_add), filled = true, onClick = { picking = true })
                }
                SettingsGlyph(onOpenSettings, Modifier.offset(x = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
            if (widgets.isEmpty()) {
                UsosText(
                    stringResource(if (editing) R.string.widgets_empty_editing else R.string.widgets_empty),
                    UsosType.Empty, UsosColors.TextSecondary, maxLines = 2,
                )
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { listCoords = it }) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Box(
                            Modifier.fillMaxWidth().height(tileH * rowsUsed + RowGap * (rowsUsed - 1).coerceAtLeast(0))
                                .onGloballyPositioned { gridCoords = it },
                        ) {
                            pack.placements.forEach { p ->
                                val id = p.key.toInt()
                                val w = widgets.firstOrNull { it.id == id } ?: return@forEach
                                key(id) {
                                    val q = placements.firstOrNull { it.key == p.key }
                                    val dragging = dragId == id
                                    // El que se redimensiona conserva su tamaño real hasta confirmar; lo demás sigue la colocación en vivo.
                                    val shown = if (resizeId == id) p else (q ?: p)
                                    val grid = gridCoords?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
                                    val base = if (dragging) {
                                        val t = dragPointer - dragGrab - grid
                                        IntOffset(t.x.roundToInt(), t.y.roundToInt())
                                    } else IntOffset((stepX * shown.col).roundToInt(), (stepY * shown.row).roundToInt())
                                    val pos by animateIntOffsetAsState(
                                        base, if (dragging) snap() else usosTween(animations), label = "widget",
                                    )
                                    val selected = selectedId == id
                                    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
                                    Box(
                                        Modifier.offset { pos }.zIndex(if (dragging) 1f else 0f),
                                    ) {
                                        // Fuera de edicion la pulsacion larga no hace nada: solo se edita con EDITAR.
                                        WidgetBox(w, spanW(shown.colSpan), spanH(shown.rowSpan), grayscale, host, manager) {}
                                        if (editing) {
                                            var layer by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                            val canDrag = selected && !resizing
                                            LaunchedEffect(selected) { if (selected) selectedRect = layer?.rootRect() }
                                            val select = { if (selectedId != id) { selectedId = id; selectedRect = null; resizing = false } }
                                            Box(
                                                Modifier.size(spanW(shown.colSpan), spanH(shown.rowSpan))
                                                    .background(UsosColors.Background.copy(alpha = 0.35f), shape)
                                                    .onGloballyPositioned {
                                                        layer = it
                                                        if (selectedId == id) selectedRect = it.rootRect()
                                                    }
                                                    .then(
                                                        if (selected) Modifier.border(2.dp, accent, shape)
                                                        else Modifier.dashedBorder(accent, UsosDimens.RadiusPiece),
                                                    )
                                                    .then(
                                                        if (canDrag) Modifier.pointerInput(id) {
                                                            awaitEachGesture {
                                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                                val moved = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                                                                    ?: return@awaitEachGesture
                                                                val l = layer ?: return@awaitEachGesture
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                dragId = id
                                                                dragGrab = down.position
                                                                dragPointer = l.positionInRoot() + moved.position
                                                                hoverCell = null
                                                                updateDragPreview()
                                                                val finished = drag(moved.id) { change ->
                                                                    change.consume()
                                                                    layer?.let { dragPointer = it.positionInRoot() + change.position }
                                                                    updateDragPreview()
                                                                }
                                                                if (finished) dropDrag()
                                                                else { dragId = null; hoverCell = null; previewOrder = null; preview = null }
                                                            }
                                                        } else if (selected) Modifier
                                                        else Modifier.pressGestures(longMs = 400, onLongPress = select) { select() },
                                                    ),
                                            )
                                        }
                                    }
                                    // Contorno de acento en el hueco destino (arrastre) o con el nuevo tamaño (redimensionado).
                                    if (editing && q != null && preview != null && (dragging || resizeId == id)) {
                                        val t = IntOffset((stepX * q.col).roundToInt(), (stepY * q.row).roundToInt())
                                        Box(
                                            Modifier.offset { t }.size(spanW(q.colSpan), spanH(q.rowSpan))
                                                .border(2.dp, accent, RoundedCornerShape(UsosDimens.RadiusPiece)),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(navBar + 12.dp))
                    }

                }
            }
        }
            // Bolitas o tiradores de la selección (capa propia sobre toda la página).
            val sel = selectedId
            val live = if (resizeId != null) frozenRect else selectedRect
            val page = pageCoords?.takeIf { it.isAttached }
            if (editing && sel != null && live != null && page != null && dragId == null) {
                val origin = page.positionInRoot()
                val local = live.translate(-origin)
                if (resizing) {
                    ResizeHandles(
                        local,
                        onStart = {
                            resizeId = sel
                            resizeFrom = sizeById[sel] ?: WidgetCells(1, 1)
                            resizeCells = resizeFrom
                            frozenRect = selectedRect
                        },
                        onDelta = { dx, dy, axes ->
                            val (minC, minR) = minCellsOf(sel)
                            val next = WidgetSizing.snapCells(
                                resizeFrom, if (axes.x) dx else 0f, if (axes.y) dy else 0f, stepX, stepY, columns, minC, minR,
                            )
                            if (next != resizeCells) {
                                resizeCells = next
                                preview = if (next == resizeFrom) null else
                                    WidgetSizing.layout(columns, sizesNow.map { (i, c) -> i to (if (i == sel) next else c) })
                            }
                        },
                        onEnd = { completed ->
                            val next = resizeCells
                            val pv = preview
                            if (completed && next != null && next != resizeFrom && pv != null) {
                                held = pv
                                actions.onResize(sel, next.cols, next.rows)
                            }
                            resizeId = null; resizeCells = null; frozenRect = null; preview = null
                        },
                    )
                } else {
                    val resizeText = stringResource(R.string.bubble_resize)
                    val reconfText = stringResource(R.string.bubble_reconfigure)
                    val removeText = stringResource(R.string.bubble_remove)
                    val configurable = manager.getAppWidgetInfo(sel)?.configure != null
                    val bubbles = remember(sel, configurable, resizeText, reconfText, removeText) {
                        buildList {
                            add(Bubble(BubbleKind.RESIZE, resizeText) { resizing = true })
                            if (configurable) add(Bubble(BubbleKind.SETTINGS, reconfText) { clearSelection(); actions.onReconfigure(sel) })
                            add(Bubble(BubbleKind.REMOVE, removeText) { clearSelection(); actions.onRemove(sel) })
                        }
                    }
                    // Las bolitas se colocan respecto a la página, no a la lista (el BoxWithConstraints no se desplaza).
                    val viewport = listCoords?.takeIf { it.isAttached }?.rootRect()?.translate(-origin)
                    BubbleRow(local, page.size, viewport, bubbles, appearKey = sel)
                }
            }

        if (picking) {
            WidgetPicker(manager, grayscale, onPick = { picking = false; actions.onAdd(it) }, onClose = { picking = false })
        }
    }
}

@Composable
private fun WidgetBox(
    widget: HostedWidget,
    width: Dp,
    height: Dp,
    grayscale: Boolean,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    onLongPress: () -> Unit,
) {
    val info = remember(widget.id) { manager.getAppWidgetInfo(widget.id) }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Box(Modifier.size(width, height).clip(shape)) {
        if (info == null) {
            Box(
                Modifier.fillMaxSize().background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
                    .pressGestures(longMs = 600, onLongPress = onLongPress) { }.padding(16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                UsosText(stringResource(R.string.widgets_unavailable), UsosType.Label, UsosColors.TextSecondary)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> host.createView(ctx, widget.id, info) },
                update = { view: AppWidgetHostView ->
                    (view as? UsosHostView)?.let {
                        it.onLongPress = onLongPress
                        it.setGrayscale(grayscale)
                    }
                    // Tamano real de la celda ocupada (ancho x alto en dp) para que el widget elija su disposicion.
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(width.value, height.value)))
                },
            )
        }
    }
}
