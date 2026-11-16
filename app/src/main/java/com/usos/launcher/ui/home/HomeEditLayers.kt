package com.usos.launcher.ui.home

// Edición directa: capa de gestos de cada pieza y bolitas/tiradores de la pieza seleccionada.

import androidx.compose.runtime.snapshotFlow
import android.util.Log
import com.usos.launcher.BuildConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.usos.launcher.ui.theme.LocalOnAccent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import com.usos.launcher.data.leisure.LeisureStatus
import com.usos.launcher.data.sections.Highlight
import com.usos.launcher.data.usage.UsageSummary
import com.usos.launcher.ui.appdetail.AppDetailScreen
import com.usos.launcher.ui.appdetail.DetailActions
import com.usos.launcher.ui.settings.OnboardingScreen
import com.usos.launcher.ui.settings.SettingsActions
import com.usos.launcher.ui.settings.SettingsScreen
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.search.SearchOverlay
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.onColor
import androidx.compose.ui.graphics.Color
import com.usos.launcher.ui.theme.LocalOnAccent
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.TileHead
import com.usos.launcher.ui.theme.LocalIconPrefs
import com.usos.launcher.ui.theme.LocalIcons
import androidx.compose.runtime.CompositionLocalProvider
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.key
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import com.usos.launcher.ui.theme.Bubble
import com.usos.launcher.ui.theme.BubbleKind
import com.usos.launcher.ui.theme.BubbleRow
import com.usos.launcher.ui.theme.ResizeHandles
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.withFrameNanos
import com.usos.launcher.data.sections.Cell
import com.usos.launcher.data.sections.MosaicEdit
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.ui.theme.dashedBorder
import com.usos.launcher.ui.theme.pressGestures
import com.usos.launcher.ui.theme.swipeVertical
import com.usos.launcher.ui.theme.tapOutside
import kotlin.math.roundToInt
import com.usos.launcher.ui.theme.usosTween


/**
 * Capa de edición sobre una pieza. Sin seleccionar: toque o pulsación larga la selecciona (arrastrar desplaza la
 * lista). Seleccionada: arrastrarla la mueve (salvo la de información y mientras hay tiradores).
 */
@Composable
internal fun PieceEditLayer(
    key: String,
    section: Section,
    tile: TileUi?,
    edit: EditState,
    actions: EditActions,
) {
    val accent = LocalAccent.current
    val haptic = LocalHapticFeedback.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val selected = edit.selectedKey == key
    val canDrag = selected && !edit.resizing && tile != null
    val tileNow by rememberUpdatedState(tile)
    val actionsNow by rememberUpdatedState(actions)
    LaunchedEffect(selected) { if (selected) edit.selectedRect = coords?.rootRect() }
    val select = { edit.select(key, section) }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned {
                coords = it
                if (edit.selectedKey == key) edit.selectedRect = it.rootRect()
            }
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier.dashedBorder(accent, UsosDimens.RadiusPiece))
            .then(
                if (tile == null) {
                    if (selected) Modifier else Modifier.pressGestures(onLongPress = select) { select() }
                } else Modifier.pointerInput(key, section) {
                    // Una sola entrada de gestos (no cambia al seleccionar, así el arrastre no se corta):
                    // seleccionada = arrastrar ya mueve; sin seleccionar = toque selecciona y mantener quieto
                    // ~0,5 s la selecciona y la coge sin soltar.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (edit.resizing) return@awaitEachGesture
                        val pointerId: androidx.compose.ui.input.pointer.PointerId
                        val at: Offset
                        if (edit.selectedKey == key) {
                            val moved = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                ?: return@awaitEachGesture
                            pointerId = moved.id; at = moved.position
                        } else {
                            val lp = awaitLongPressOrCancellation(down.id)
                            if (lp == null) {
                                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (up != null && up.changedToUp() && !up.isConsumed &&
                                    (up.position - down.position).getDistance() < viewConfiguration.touchSlop
                                ) select()
                                return@awaitEachGesture
                            }
                            select()
                            pointerId = lp.id; at = lp.position
                        }
                        val c = coords ?: return@awaitEachGesture
                        val t = tileNow ?: return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        edit.start(key, section, t, c.positionInRoot(), down.position, c.size)
                        edit.pointer = c.positionInRoot() + at
                        edit.updateHover()
                        var finished = false
                        try {
                            finished = drag(pointerId) { change ->
                                change.consume()
                                coords?.let { edit.pointer = it.positionInRoot() + change.position }
                                edit.updateHover()
                            }
                        } finally {
                            // También si el gesto se cancela por una excepción (nodo quitado, edición terminada).
                            if (finished) edit.drop(actionsNow) else edit.cancel()
                        }
                    }
                },
            ),
    )
}

/**
 * Capa superior de la edición de inicio: bolitas alrededor de la pieza seleccionada o, tras REDIMENSIONAR, sus
 * tiradores; y un toque fuera de la pieza deselecciona. Lee la posición viva de la pieza (scroll incluido).
 */
@Composable
internal fun EditOverlay(edit: EditState, actions: EditActions, pageCoords: LayoutCoordinates?) {
    val key = edit.selectedKey ?: return
    if (edit.dragging) return
    val live = if (edit.resizeKey != null) edit.frozenRect else edit.selectedRect
    val page = pageCoords?.takeIf { it.isAttached } ?: return
    val rect = live ?: return
    val origin = page.positionInRoot()
    val local = rect.translate(-origin)
    val viewport = edit.viewport?.rootRect()?.translate(-origin)
    if (edit.resizing) {
        ResizeHandles(
            local,
            onStart = { edit.beginResize() },
            onDelta = { dx, dy, axes -> edit.updateResize(dx, dy, axes) },
            onEnd = { edit.endResize(actions, it) },
        )
    } else {
        val resizeText = stringResource(R.string.bubble_resize)
        val settingsText = stringResource(R.string.bubble_settings)
        val removeText = stringResource(R.string.bubble_remove)
        val isInfo = key == com.usos.launcher.data.sections.SectionMosaic.INFO_KEY
        val bubbles = remember(key, resizeText, settingsText, removeText) {
            buildList {
                add(Bubble(BubbleKind.RESIZE, resizeText) { edit.resizing = true })
                if (!isInfo) {
                    add(Bubble(BubbleKind.SETTINGS, settingsText) { val k = key; edit.clearSelection(); actions.onDetail(k) })
                    add(Bubble(BubbleKind.REMOVE, removeText) { val k = key; edit.clearSelection(); actions.onRemove(k) })
                }
            }
        }
        BubbleRow(local, page.size, viewport, bubbles, appearKey = key)
    }
}
