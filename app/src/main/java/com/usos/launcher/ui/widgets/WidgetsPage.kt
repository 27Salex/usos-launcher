package com.usos.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap

class WidgetsActions(
    val onAdd: (AppWidgetProviderInfo) -> Unit,
    val onMove: (id: Int, delta: Int) -> Unit,
    val onSize: (id: Int, size: WidgetSize) -> Unit,
    val onRemove: (id: Int) -> Unit,
)

/** Pagina izquierda del pager: cabecera, columna de widgets reales y menu de edicion. */
@Composable
fun WidgetsPage(
    widgets: List<HostedWidget>,
    grayscale: Boolean,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    actions: WidgetsActions,
    topSpacing: Dp,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = UsosDimens.ScreenMargin)) {
            Spacer(Modifier.height(topSpacing))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                UsosText("Widgets", UsosType.PanelTitle, UsosColors.Text)
                UsosPill("AÑADIR", filled = true, onClick = { picking = true })
            }
            Spacer(Modifier.height(16.dp))
            if (widgets.isEmpty()) {
                UsosText("Sin widgets. Añade el primero.", UsosType.Empty, UsosColors.TextSecondary)
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    widgets.forEach { w ->
                        key(w.id) { WidgetBox(w, grayscale, host, manager) { menuFor = w.id } }
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(navBar + 12.dp))
                }
            }
        }
        val menuWidget = widgets.firstOrNull { it.id == menuFor }
        if (menuWidget != null) {
            val idx = widgets.indexOf(menuWidget)
            WidgetMenu(
                menuWidget, canUp = idx > 0, canDown = idx < widgets.lastIndex,
                onUp = { actions.onMove(menuWidget.id, -1) },
                onDown = { actions.onMove(menuWidget.id, 1) },
                onSize = { actions.onSize(menuWidget.id, it) },
                onRemove = { menuFor = null; actions.onRemove(menuWidget.id) },
                onClose = { menuFor = null },
            )
        }
        if (picking) {
            WidgetPicker(manager, onPick = { picking = false; actions.onAdd(it) }, onClose = { picking = false })
        }
    }
}

@Composable
private fun WidgetBox(
    widget: HostedWidget,
    grayscale: Boolean,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    onLongPress: () -> Unit,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val info = remember(widget.id) { manager.getAppWidgetInfo(widget.id) }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    val heightDp = WidgetSizing.heightDp(info?.let { (it.minHeight / density).toInt() } ?: 0, widget.size)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(heightDp.dp)
            .clip(shape).background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape),
    ) {
        if (info == null) {
            Box(Modifier.fillMaxSize().tap(onLongClick = onLongPress) { }.padding(16.dp), contentAlignment = Alignment.CenterStart) {
                UsosText("WIDGET NO DISPONIBLE", UsosType.Label, UsosColors.TextSecondary)
            }
        } else {
            val widthDp = maxWidth.value.toInt()
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> host.createView(ctx, widget.id, info) },
                update = { view: AppWidgetHostView ->
                    (view as? UsosHostView)?.let {
                        it.onLongPress = onLongPress
                        it.setGrayscale(grayscale)
                    }
                    // Tamano real en dp para que el widget elija su disposicion.
                    view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
                },
            )
        }
    }
}

@Composable
private fun WidgetMenu(
    widget: HostedWidget,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onSize: (WidgetSize) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Box(
        Modifier.fillMaxSize().background(UsosColors.Background.copy(alpha = 0.85f)).tap { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = UsosDimens.ScreenMargin).fillMaxWidth()
                .background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
                .tap { }.padding(16.dp),
        ) {
            MenuRow("SUBIR", canUp, UsosColors.Text, onUp)
            MenuRow("BAJAR", canDown, UsosColors.Text, onDown)
            UsosText("TAMAÑO", UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetSize.entries.forEach { s ->
                    UsosPill(s.label, filled = s == widget.size, onClick = { onSize(s) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            MenuRow("QUITAR", true, LocalAccent.current, onRemove)
        }
    }
}

@Composable
private fun MenuRow(text: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(UsosDimens.MinTouch).tap(enabled = enabled) { onClick() },
        contentAlignment = Alignment.CenterStart,
    ) {
        UsosText(text, UsosType.Button, if (enabled) color else UsosColors.BorderStrong)
    }
}
