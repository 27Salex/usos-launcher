package com.usos.launcher.ui.home

// Secciones del inicio: bloque de sección (acordeón), pestañas opcionales y rejilla del mosaico.

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


@Composable
internal fun SectionBlock(
    s: SectionUi,
    isOpen: Boolean,
    leisure: LeisureStatus?,
    blockerActive: Boolean,
    tabbed: Boolean = false,
    workPaused: Boolean,
    onResumeWork: () -> Unit,
    editing: Boolean,
    edit: EditState,
    editActions: EditActions,
    onAdd: (Cell) -> Unit,
    onToggle: (Section) -> Unit,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onEnterEdit: () -> Unit,
    onThaw: (Int) -> Unit,
    onCancelThaw: () -> Unit,
    onFreeze: () -> Unit,
    onOpenInfo: (com.usos.launcher.data.info.InfoKind) -> Unit,
) {
    val animations = LocalAnimationsEnabled.current
    val accent = LocalAccent.current
    val isLeisure = s.section == Section.LEISURE
    val frozen = (isLeisure && leisure?.isOpen != true) || workPaused
    val nameColor = if (edit.hoverSection == s.section) accent else if (frozen) UsosColors.LeisureFrozen else UsosColors.Text
    val data: String = when {
        workPaused -> stringResource(R.string.home_work_paused)
        !isLeisure -> if (isOpen) "—" else s.count.toString()
        leisure is LeisureStatus.Open -> stringResource(R.string.home_minutes, leisure.minutesLeft)
        leisure is LeisureStatus.Thawing -> stringResource(R.string.home_seconds, leisure.secondsLeft)
        else -> stringResource(R.string.home_frozen)
    }
    if (!tabbed) Row(
        Modifier.fillMaxWidth().height(SectionRowHeight)
            .onGloballyPositioned { edit.headers[s.section] = it }
            .tap { onToggle(s.section) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val pending = if (frozen) 0 else com.usos.launcher.ui.theme.LocalNotifBadges.current.total(s.placements.map { it.key }.filter { it in s.tiles })
        Row(verticalAlignment = Alignment.CenterVertically) {
            UsosText(stringResource(s.section.titleRes), UsosType.SectionName, nameColor)
            if (pending > 0) {
                Spacer(Modifier.width(8.dp))
                com.usos.launcher.ui.theme.CountBubble(pending, accent, accent.onColor(), Modifier.offset(y = (-6).dp))
            }
        }
        UsosText(data, UsosType.SectionData, if (isLeisure) accent else UsosColors.TextSecondary)
    }
    AnimatedVisibility(
        visible = isOpen,
        enter = if (animations && !tabbed) {
            expandVertically(usosTween(true)) + fadeIn(usosTween(true))
        } else {
            EnterTransition.None
        },
        exit = if (animations) {
            shrinkVertically(usosTween(true)) + fadeOut(usosTween(true))
        } else {
            ExitTransition.None
        },
    ) {
        val rise by transition.animateFloat(
            transitionSpec = { usosTween(animations) },
            label = "rise",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val risePx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
        val contentModifier = Modifier
            .padding(top = 4.dp, bottom = 12.dp)
            .graphicsLayer {
                alpha = rise
                translationY = (1f - rise) * risePx
            }
        val context = LocalContext.current
        Column(contentModifier) {
            if (isLeisure && !blockerActive) {
                Box(
                    Modifier.fillMaxWidth().height(UsosDimens.MinTouch).tap {
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    UsosText(stringResource(R.string.home_blocker_inactive), UsosType.Label, accent)
                }
            }
            if (workPaused) {
                WorkPausedPanel(onResumeWork)
            } else if (isLeisure && leisure !is LeisureStatus.Open) {
                LeisurePanel(leisure, onThaw, onCancelThaw)
            } else {
                Mosaic(
                    s = s,
                    editing = editing, edit = edit, editActions = editActions, onAdd = onAdd,
                    onOpenInfo = onOpenInfo,
                    onOpenApp = onOpenApp,
                    onEnterEdit = onEnterEdit,
                    trailing = (leisure as? LeisureStatus.Open)?.let { open ->
                        { cellModifier -> FreezeTile(open.minutesLeft, cellModifier, onFreeze, onEnterEdit) }
                    },
                )
                if (editing) RowsControl(s.rows) { editActions.onRows(s.section, it) }
            }
        }
    }
}

/** Trabajo con el perfil en pausa: al desplegar la sección se ofrece activarlo (el sistema puede pedir el bloqueo). */
@Composable
internal fun WorkPausedPanel(onResume: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        UsosText(stringResource(R.string.home_work_paused_text), UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
        com.usos.launcher.ui.theme.UsosPill(stringResource(R.string.home_work_resume), filled = true, onClick = onResume)
    }
}

/**
 * Pestañas de secciones bajo la semana: nombre (fuente de título), contador de notificaciones y estado (Ocio
 * congelado / Trabajo en pausa en gris). La activa en texto y con subrayado de acento. En edición cada pestaña es
 * zona de soltar (mueve la pieza a esa sección).
 */
@Composable
internal fun SectionTabs(state: HomeUiState, leisure: LeisureStatus?, edit: EditState, onSelect: (Section) -> Unit) {
    val accent = LocalAccent.current
    val tabsScroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().horizontalScroll(tabsScroll), verticalAlignment = Alignment.CenterVertically) {
        state.sections.forEach { s ->
            val active = state.open == s.section
            val frozen = (s.section == Section.LEISURE && leisure?.isOpen != true) ||
                (s.section == Section.WORK && state.workPaused)
            val hover = edit.hoverSection == s.section
            val color = when {
                hover -> accent
                active -> UsosColors.Text
                frozen -> UsosColors.LeisureFrozen
                else -> UsosColors.TextSecondary
            }
            val pending = if (frozen) 0 else com.usos.launcher.ui.theme.LocalNotifBadges.current
                .total(s.placements.map { it.key }.filter { it in s.tiles })
            Column(
                Modifier.padding(end = 18.dp)
                    .onGloballyPositioned { edit.headers[s.section] = it }
                    .tap { onSelect(s.section) }
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsosText(stringResource(s.section.titleRes), UsosType.SectionName.copy(fontSize = 22.sp, lineHeight = 26.sp), color)
                    if (pending > 0) {
                        Spacer(Modifier.width(4.dp))
                        com.usos.launcher.ui.theme.CountBubble(pending, accent, accent.onColor(), Modifier.offset(y = (-8).dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(2.dp).width(24.dp).background(if (active) accent else Color.Transparent))
            }
        }
    }
}

/**
 * Rejilla de 3 o 4 columnas; ancho de celda = (ancho útil - 11 dp x (columnas - 1)) / columnas. Las piezas ya
 * vienen colocadas por el empaquetado denso (SectionMosaic / MosaicPacker, puros). En edición se dibuja la
 * rejilla completa (celdas fantasma con «+»), cada pieza con borde discontinuo y, mientras se arrastra o se
 * redimensiona, la colocación en vivo (`EditState.preview`) con las demás piezas animadas a su sitio.
 */
@Composable
internal fun Mosaic(
    s: SectionUi,
    editing: Boolean,
    edit: EditState,
    editActions: EditActions,
    onAdd: (Cell) -> Unit,
    onOpenInfo: (com.usos.launcher.data.info.InfoKind) -> Unit,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onEnterEdit: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable (Modifier) -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val density = LocalDensity.current
    val animations = LocalAnimationsEnabled.current
    // La colocación soltada se mantiene hasta que llega la guardada (o 900 ms) para no volver atrás un instante.
    LaunchedEffect(s.placements) { if (edit.held?.section == s.section) edit.held = null }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = s.columns
        val cell = (maxWidth - TileColumnGap * (cols - 1)) / cols
        val tileH = com.usos.launcher.data.sections.MosaicPacker.tileHeightDp(cols, maxWidth.value).dp
        val rows = when {
            editing -> s.rows
            trailing != null -> s.rowsUsed
            else -> s.placements.maxOfOrNull { it.row + it.rowSpan } ?: 0
        }
        val stepX = with(density) { (cell + TileColumnGap).toPx() }
        val stepY = with(density) { (tileH + TileRowGap).toPx() }
        val eff = if (editing) (edit.preview ?: edit.held)?.takeIf { it.section == s.section } else null
        val placements = eff?.layout?.placements ?: s.placements
        val freezeCell = if (eff != null) eff.layout.reserved else s.freeze
        fun sizeOf(cs: Int, rs: Int) = Modifier.size(cell * cs + TileColumnGap * (cs - 1), tileH * rs + TileRowGap * (rs - 1))
        fun place(col: Int, row: Int, cs: Int, rs: Int) = Modifier
            .offset(x = (cell + TileColumnGap) * col, y = (tileH + TileRowGap) * row)
            .then(sizeOf(cs, rs))
        Box(
            Modifier.fillMaxWidth().height(tileH * rows + TileRowGap * (rows - 1).coerceAtLeast(0))
                .onGloballyPositioned { if (editing) edit.grids[s.section] = GridGeom(it, cols, s.rows, stepX, stepY) },
        ) {
            if (editing) {
                val occupied = Array(s.rows) { BooleanArray(cols) }
                placements.forEach { c ->
                    for (r in c.row until minOf(c.row + c.rowSpan, s.rows)) for (cc in c.col until minOf(c.col + c.colSpan, cols)) occupied[r][cc] = true
                }
                freezeCell?.let { if (it.row < s.rows && trailing != null) occupied[it.row][it.col] = true }
                for (r in 0 until s.rows) for (c in 0 until cols) if (!occupied[r][c]) {
                    Box(
                        place(c, r, 1, 1)
                            .dashedBorder(UsosColors.BorderStrong, UsosDimens.RadiusPiece)
                            .pressGestures { if (edit.selectedKey != null) edit.clearSelection() else onAdd(Cell(c, r)) },
                        contentAlignment = Alignment.Center,
                    ) { UsosText("+", UsosType.SectionName, UsosColors.TextSecondary) }
                }
            }
            placements.forEach { c ->
                key(c.key) {
                    val isInfo = c.key == com.usos.launcher.data.sections.SectionMosaic.INFO_KEY
                    val tile = if (isInfo) null else s.tiles[c.key]
                    if (isInfo || tile != null) {
                        val target = IntOffset((stepX * c.col).roundToInt(), (stepY * c.row).roundToInt())
                        val pos by animateIntOffsetAsState(target, usosTween(animations), label = "tile")
                        val dragged = edit.dragKey == c.key
                        val showSlot = dragged && eff != null
                        Box(
                            Modifier.offset { pos }.then(sizeOf(c.colSpan, c.rowSpan))
                                .graphicsLayer { alpha = if (dragged) (if (showSlot) 0f else 0.3f) else 1f },
                        ) {
                            if (isInfo) {
                                s.info?.let { info ->
                                    InfoTile(info, Modifier.fillMaxSize(), !editing, onEnterEdit) { onOpenInfo(info.kind) }
                                }
                            } else {
                                SmartOrAppTile(tile!!, LocalCustomTiles.current[c.key], c.colSpan, c.rowSpan, !editing, onOpenApp, onEnterEdit)
                            }
                            if (editing) {
                                PieceEditLayer(c.key, s.section, tile, edit, editActions)
                            }
                        }
                        // La celda destino / el nuevo tamaño se marca con un contorno de acento.
                        if (editing && (showSlot || (edit.resizeKey == c.key && eff != null))) {
                            Box(
                                Modifier.offset { pos }.then(sizeOf(c.colSpan, c.rowSpan))
                                    .border(2.dp, accent, RoundedCornerShape(UsosDimens.RadiusPiece)),
                            )
                        }
                    }
                }
            }
            val f = freezeCell
            if (trailing != null && f != null) trailing(place(f.col, f.row, 1, 1))
        }
    }
}
