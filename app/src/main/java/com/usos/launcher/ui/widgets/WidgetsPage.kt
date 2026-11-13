package com.usos.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.usos.launcher.data.sections.MosaicEdit
import com.usos.launcher.data.sections.MosaicPacker
import com.usos.launcher.data.sections.Placement
import com.usos.launcher.ui.home.ResizeGlyph
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.SettingsGlyph
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.dashedBorder
import com.usos.launcher.ui.theme.pressGestures

class WidgetsActions(
    val onAdd: (AppWidgetProviderInfo) -> Unit,
    val onReorder: (order: List<Int>) -> Unit,
    val onResize: (id: Int, cols: Int, rows: Int) -> Unit,
    val onRemove: (id: Int) -> Unit,
)

private val ColGap = 11.dp
private val RowGap = 10.dp

/** Pagina izquierda del pager: cabecera y rejilla de widgets igual que el mosaico de inicio (columnas, alto de celda y separaciones). */
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
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var resizeId by remember { mutableStateOf<Int?>(null) }
    var resizeCells by remember { mutableStateOf<WidgetCells?>(null) }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalContext.current.resources.displayMetrics.density
    val accent = LocalAccent.current
    BackHandler(enabled = editing) { editing = false }

    fun defaultRows(id: Int): Int {
        val info = manager.getAppWidgetInfo(id) ?: return 1
        fun dp(px: Int) = (px / density).toInt()
        return WidgetSizing.dpToRows(WidgetSizing.defaultDp(dp(info.minHeight), info.targetCellHeight))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = UsosDimens.ScreenMargin)) {
            Spacer(Modifier.height(topSpacing))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UsosText("Widgets", UsosType.PanelTitle, UsosColors.Text, Modifier.weight(1f))
                SettingsGlyph(onOpenSettings, Modifier.offset(x = 12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsosPill(if (editing) "LISTO" else "EDITAR", filled = editing, onClick = { editing = !editing })
                UsosPill("AÑADIR", filled = !editing, onClick = { picking = true })
            }
            if (editing) {
                Spacer(Modifier.height(8.dp))
                UsosText("ARRASTRA · TIRADOR = TAMAÑO · × QUITA", UsosType.Label, accent)
            }
            Spacer(Modifier.height(16.dp))
            if (widgets.isEmpty()) {
                UsosText("Sin widgets. Añade el primero.", UsosType.Empty, UsosColors.TextSecondary)
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cell = (maxWidth - ColGap * (columns - 1)) / columns
                    val tileH = MosaicPacker.tileHeightDp(columns, maxWidth.value).dp
                    val stepX = with(LocalDensity.current) { (cell + ColGap).toPx() }
                    val stepY = with(LocalDensity.current) { (tileH + RowGap).toPx() }
                    val sizes = widgets.map { it.id to WidgetSizing.cells(it, columns, defaultRows(it.id)) }
                    val pack = WidgetSizing.layout(columns, sizes)
                    val rowsUsed = pack.rowsUsed
                    val sizeById = sizes.toMap()
                    fun place(p: Placement) = Modifier
                        .offset(x = (cell + ColGap) * p.col, y = (tileH + RowGap) * p.row)
                        .size(cell * p.colSpan + ColGap * (p.colSpan - 1), tileH * p.rowSpan + RowGap * (p.rowSpan - 1))
                    val widgetsNow by rememberUpdatedState(widgets)
                    val packNow by rememberUpdatedState(pack)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().height(tileH * rowsUsed + RowGap * (rowsUsed - 1).coerceAtLeast(0))) {
                            pack.placements.forEach { p ->
                                val id = p.key.toInt()
                                val w = widgets.firstOrNull { it.id == id } ?: return@forEach
                                key(id) {
                                    val dragging = dragId == id
                                    val width = cell * p.colSpan + ColGap * (p.colSpan - 1)
                                    val height = tileH * p.rowSpan + RowGap * (p.rowSpan - 1)
                                    Box(
                                        place(p)
                                            .zIndex(if (dragging) 1f else 0f)
                                            .graphicsLayer {
                                                translationX = if (dragging) dragOffset.x else 0f
                                                translationY = if (dragging) dragOffset.y else 0f
                                            },
                                    ) {
                                        WidgetBox(w, width, height, grayscale, host, manager) { editing = true }
                                        if (editing) {
                                            EditOverlay(
                                                dragging = dragging,
                                                onDragStart = { dragId = id; dragOffset = Offset.Zero },
                                                onDrag = { dragOffset += it },
                                                onDragEnd = {
                                                    val cur = packNow.placements.firstOrNull { it.key == p.key }
                                                    if (cur != null) {
                                                        val x = (cur.col * stepX + dragOffset.x + stepX / 2f).coerceAtLeast(0f)
                                                        val y = (cur.row * stepY + dragOffset.y + stepY / 2f).coerceAtLeast(0f)
                                                        MosaicEdit.cellAt(x, y, stepX, stepY, columns, 200)?.let { target ->
                                                            val full = widgetsNow.map { it.id.toString() }
                                                            val next = MosaicEdit.reorder(full, packNow.placements, p.key, target, columns)
                                                            if (next != full) actions.onReorder(next.map { it.toInt() })
                                                        }
                                                    }
                                                    dragId = null
                                                    dragOffset = Offset.Zero
                                                },
                                                onResizeStart = { resizeId = id; resizeCells = sizeById[id] },
                                                onResize = { dx, dy ->
                                                    resizeCells = WidgetSizing.snapCells(sizeById.getValue(id), dx, dy, stepX, stepY, columns)
                                                },
                                                onResizeEnd = {
                                                    resizeCells?.let { if (it != sizeById[id]) actions.onResize(id, it.cols, it.rows) }
                                                    resizeId = null
                                                    resizeCells = null
                                                },
                                                onRemove = { actions.onRemove(id) },
                                            )
                                        }
                                    }
                                }
                            }
                            val rid = resizeId
                            val rc = resizeCells
                            if (editing && rid != null && rc != null) {
                                pack.placements.firstOrNull { it.key == rid.toString() }?.let { p ->
                                    Box(
                                        place(p.copy(colSpan = rc.cols, rowSpan = rc.rows))
                                            .border(2.dp, accent, RoundedCornerShape(UsosDimens.RadiusPiece)),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(navBar + 12.dp))
                    }
                }
            }
        }
        if (picking) {
            WidgetPicker(manager, onPick = { picking = false; actions.onAdd(it) }, onClose = { picking = false })
        }
    }
}

/** Capa de edicion: borde discontinuo, arrastre para ordenar, tirador de la esquina inferior derecha (ancho y alto) y boton ×. */
@Composable
private fun EditOverlay(
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (dx: Float, dy: Float) -> Unit,
    onResizeEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = LocalAccent.current
    val resizeTotal = remember { floatArrayOf(0f, 0f) }
    Box(
        Modifier.fillMaxSize()
            .background(UsosColors.Background.copy(alpha = 0.35f), RoundedCornerShape(UsosDimens.RadiusPiece))
            .dashedBorder(if (dragging) UsosColors.Text else accent, UsosDimens.RadiusPiece)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { c, a -> c.consume(); onDrag(a) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).size(UsosDimens.MinTouch).pressGestures { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            UsosText("×", UsosType.SectionName, accent)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(44.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { resizeTotal[0] = 0f; resizeTotal[1] = 0f; onResizeStart() },
                        onDrag = { c, a -> c.consume(); resizeTotal[0] += a.x; resizeTotal[1] += a.y; onResize(resizeTotal[0], resizeTotal[1]) },
                        onDragEnd = onResizeEnd,
                        onDragCancel = onResizeEnd,
                    )
                },
            contentAlignment = Alignment.BottomEnd,
        ) {
            ResizeGlyph(Modifier.padding(10.dp))
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
                UsosText("WIDGET NO DISPONIBLE", UsosType.Label, UsosColors.TextSecondary)
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
