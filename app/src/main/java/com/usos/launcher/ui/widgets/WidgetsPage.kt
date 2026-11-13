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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
    val onMove: (id: Int, delta: Int) -> Unit,
    val onHeight: (id: Int, heightDp: Int) -> Unit,
    val onRemove: (id: Int) -> Unit,
)

/** Pagina izquierda del pager: cabecera, columna de widgets reales y modo edicion (arrastrar, alto por filas, quitar). */
@Composable
fun WidgetsPage(
    widgets: List<HostedWidget>,
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
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var resizeId by remember { mutableStateOf<Int?>(null) }
    var resizeRows by remember { mutableIntStateOf(1) }
    var resizeStart by remember { mutableIntStateOf(1) }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalContext.current.resources.displayMetrics.density
    val accent = LocalAccent.current
    val widgetsNow by rememberUpdatedState(widgets)
    BackHandler(enabled = editing) { editing = false }

    fun heightOf(w: HostedWidget): Int =
        if (w.heightDp > 0) w.heightDp else WidgetDims.of(manager.getAppWidgetInfo(w.id), density).height(0)

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
                UsosText("ARRASTRA · TIRADOR = ALTO · × QUITA", UsosType.Label, accent)
            }
            Spacer(Modifier.height(16.dp))
            if (widgets.isEmpty()) {
                UsosText("Sin widgets. Añade el primero.", UsosType.Empty, UsosColors.TextSecondary)
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    widgets.forEach { w ->
                        key(w.id) {
                            val h = if (resizeId == w.id) WidgetSizing.rowsToDp(resizeRows) else heightOf(w)
                            val dragging = dragId == w.id
                            Box(
                                Modifier.fillMaxWidth().height(h.dp)
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                            ) {
                                WidgetBox(w, h, grayscale, host, manager) { editing = true }
                                if (editing) {
                                    EditOverlay(
                                        dragging = dragging,
                                        onDragStart = { dragId = w.id; dragOffset = 0f },
                                        onDrag = { dragOffset += it },
                                        onDragEnd = {
                                            val list = widgetsNow
                                            val from = list.indexOfFirst { it.id == w.id }
                                            val to = WidgetSizing.targetIndex(
                                                list.map { heightOf(it) * density }, 12 * density, from, dragOffset,
                                            )
                                            if (from >= 0 && to != from) actions.onMove(w.id, to - from)
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                        onResizeStart = { resizeId = w.id; resizeStart = WidgetSizing.dpToRows(h); resizeRows = resizeStart },
                                        onResize = { dy -> resizeRows = WidgetSizing.snapRows(resizeStart, dy / density) },
                                        onResizeEnd = {
                                            actions.onHeight(w.id, WidgetSizing.rowsToDp(resizeRows))
                                            resizeId = null
                                        },
                                        onRemove = { actions.onRemove(w.id) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(navBar + 12.dp))
                }
            }
        }
        if (picking) {
            WidgetPicker(manager, onPick = { picking = false; actions.onAdd(it) }, onClose = { picking = false })
        }
    }
}

/** Limites de alto de un widget, en dp, sacados de su AppWidgetProviderInfo. */
private class WidgetDims(val base: Int, val max: Int, val default: Int) {
    fun height(chosen: Int) = WidgetSizing.heightDp(chosen, base, max, default)

    companion object {
        fun of(info: AppWidgetProviderInfo?, density: Float): WidgetDims {
            if (info == null) return WidgetDims(WidgetSizing.MIN_BASE_DP, WidgetSizing.MIN_BASE_DP * 4, WidgetSizing.MIN_BASE_DP)
            fun dp(px: Int) = (px / density).toInt()
            val base = WidgetSizing.baseDp(dp(info.minHeight), dp(info.minResizeHeight))
            val max = WidgetSizing.maxDp(base, dp(info.maxResizeHeight))
            val def = WidgetSizing.defaultDp(dp(info.minHeight), info.targetCellHeight)
            return WidgetDims(base, max, def)
        }
    }
}

/** Capa de edicion: borde discontinuo, arrastre para ordenar, tirador inferior de alto y boton ×. */
@Composable
private fun EditOverlay(
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Float) -> Unit,
    onResizeEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = LocalAccent.current
    val resizeTotal = remember { floatArrayOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .background(UsosColors.Background.copy(alpha = 0.35f), RoundedCornerShape(UsosDimens.RadiusPiece))
            .dashedBorder(if (dragging) UsosColors.Text else accent, UsosDimens.RadiusPiece)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { c, a -> c.consume(); onDrag(a.y) },
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
            Modifier.align(Alignment.BottomCenter).size(width = 96.dp, height = UsosDimens.MinTouch)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { resizeTotal[0] = 0f; onResizeStart() },
                        onDrag = { c, a -> c.consume(); resizeTotal[0] += a.y; onResize(resizeTotal[0]) },
                        onDragEnd = onResizeEnd,
                        onDragCancel = onResizeEnd,
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.padding(bottom = 8.dp).size(width = 40.dp, height = 5.dp).background(accent, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun WidgetBox(
    widget: HostedWidget,
    heightDp: Int,
    grayscale: Boolean,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    onLongPress: () -> Unit,
) {
    val info = remember(widget.id) { manager.getAppWidgetInfo(widget.id) }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    BoxWithConstraints(Modifier.fillMaxWidth().height(heightDp.dp).clip(shape)) {
        if (info == null) {
            Box(
                Modifier.fillMaxSize().background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
                    .pressGestures(longMs = 600, onLongPress = onLongPress) { }.padding(16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                UsosText("WIDGET NO DISPONIBLE", UsosType.Label, UsosColors.TextSecondary)
            }
        } else {
            val widthDp = maxWidth.value
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> host.createView(ctx, widget.id, info) },
                update = { view: AppWidgetHostView ->
                    (view as? UsosHostView)?.let {
                        it.onLongPress = onLongPress
                        it.setGrayscale(grayscale)
                    }
                    // Tamano real (ancho util x alto elegido) para que el widget elija su disposicion.
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp.toFloat())))
                },
            )
        }
    }
}
