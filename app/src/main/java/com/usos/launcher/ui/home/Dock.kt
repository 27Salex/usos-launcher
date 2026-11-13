package com.usos.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.usos.launcher.R
import com.usos.launcher.domain.AppEntry
import com.usos.launcher.ui.search.SearchFilter
import com.usos.launcher.ui.theme.AppIconOrNull
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalNotifBadges
import com.usos.launcher.ui.theme.UsosMotion
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.dashedBorder
import com.usos.launcher.ui.theme.tap
import com.usos.launcher.ui.theme.themed

private val DockItem = 52.dp

/**
 * Dock de apps fijas (3-5 huecos) encima de BUSCAR. Se abre desde el centro y, fuera de edición, solo muestra
 * los huecos ocupados, centrados. En edición (o si está vacío) enseña todos: un hueco vacío se toca para elegir
 * app; uno ocupado se toca para cambiarla y su × lo vacía. Pulsación larga en una app = editar inicio.
 */
@Composable
fun Dock(
    apps: List<AppEntry?>,
    editing: Boolean,
    hidden: Boolean,
    onOpen: (AppEntry) -> Unit,
    onPick: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onEnterEdit: () -> Unit,
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(UsosMotion.DurationMs * 2, easing = UsosMotion.Easing)) }
    val showHoles = editing || apps.all { it == null }
    val shown = apps.indices.filter { showHoles || apps[it] != null }
    val shape = RoundedCornerShape(UsosDimens.RadiusSearch)
    Box(Modifier.fillMaxWidth().alpha(if (hidden) 0f else 1f), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .graphicsLayer {
                    scaleX = 0.3f + 0.7f * grow.value
                    alpha = grow.value
                    transformOrigin = TransformOrigin.Center
                }
                .background(UsosColors.Surface, shape)
                .border(1.dp, UsosColors.Border, shape)
                .animateContentSize(tween(UsosMotion.DurationMs, easing = UsosMotion.Easing))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            shown.forEach { i ->
                val app = apps[i]
                if (app == null) Hole(enabled = !hidden) { onPick(i) }
                else Slot(app, editing, enabled = !hidden, onOpen = { if (editing) onPick(i) else onOpen(app) }, onClear = { onClear(i) }, onEnterEdit)
            }
        }
    }
}

@Composable
private fun Hole(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(DockItem).dashedBorder(UsosColors.BorderStrong, UsosDimens.RadiusPiece).tap(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { UsosText("+", UsosType.Button, UsosColors.TextSecondary) }
}

@Composable
private fun Slot(app: AppEntry, editing: Boolean, enabled: Boolean, onOpen: () -> Unit, onClear: () -> Unit, onEnterEdit: () -> Unit) {
    val badge = LocalNotifBadges.current.show(app.key)
    val accent = LocalAccent.current
    Box(Modifier.size(DockItem)) {
        Box(
            Modifier.size(DockItem).tap(enabled = enabled, onLongClick = if (editing) null else onEnterEdit) { onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            AppIconOrNull(app, 30.dp, UsosColors.Text) { UsosText(app.defaultCode, UsosType.Button, UsosColors.Text) }
        }
        if (badge && !editing) Box(Modifier.align(Alignment.TopEnd).offset((-6).dp, 6.dp).size(8.dp).background(accent, CircleShape))
        if (editing) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(22.dp)
                    .background(UsosColors.Background, CircleShape).border(1.dp, UsosColors.BorderStrong, CircleShape)
                    .tap { onClear() },
                contentAlignment = Alignment.Center,
            ) { UsosText("×", UsosType.Label, UsosColors.Text) }
        }
    }
}

/** Lista de todas las apps para un hueco del dock, con buscador. */
@Composable
fun DockPicker(apps: List<AppEntry>, current: String?, onPick: (AppEntry) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp
    Column(
        Modifier.fillMaxSize().background(UsosColors.Background).tap { }
            .padding(top = top, start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(stringResource(R.string.dock_pick_title), onBack = onClose, modifier = Modifier.weight(1f))
            if (current != null) UsosPill(stringResource(R.string.dock_empty_slot), filled = false, onClick = onClear)
        }
        var query by rememberSaveable { mutableStateOf("") }
        val fieldShape = RoundedCornerShape(UsosDimens.RadiusPiece)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).height(56.dp)
                .background(UsosColors.Surface, fieldShape).border(1.dp, UsosColors.Border, fieldShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) UsosText(stringResource(R.string.dock_search_hint), UsosType.SearchHint, UsosColors.TextSecondary)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = UsosType.SearchInput.themed().copy(color = UsosColors.Text),
                cursorBrush = SolidColor(LocalAccent.current),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val q = SearchFilter.normalize(query.trim())
        val shown = remember(apps, q) {
            apps.filter { q.isEmpty() || SearchFilter.normalize(it.label).contains(q) }.sortedBy { it.label.lowercase() }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.key }) { app ->
                Row(
                    Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 8.dp).tap { onPick(app) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                        AppIconOrNull(app, 26.dp, UsosColors.Text) { UsosText(app.defaultCode, UsosType.SmallCode, UsosColors.Text) }
                    }
                    UsosText(app.label, UsosType.Pill, if (app.key == current) LocalAccent.current else UsosColors.Text, Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp)) }
        }
    }
}
