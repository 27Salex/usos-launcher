package com.usos.launcher.ui.home

// Piezas del mosaico: información, CONGELAR, app normal y piezas inteligentes (destino, contactos, música).

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


/** Pieza de información: fondo de acento, etiqueta mono arriba y dato 15 sp 700 abajo. */
@Composable
internal fun InfoTile(
    info: com.usos.launcher.data.info.InfoPiece,
    modifier: Modifier,
    interactive: Boolean,
    onEnterEdit: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Column(
        modifier.background(LocalAccent.current, shape)
            .then(if (interactive) Modifier.pressGestures(onLongPress = onEnterEdit) { onClick() } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val label = when (info.kind) {
            com.usos.launcher.data.info.InfoKind.MAPS -> stringResource(R.string.info_maps)
            else -> when (info.whenAt) {
                com.usos.launcher.data.info.InfoWhen.NOW -> stringResource(R.string.info_agenda_now)
                com.usos.launcher.data.info.InfoWhen.TOMORROW -> stringResource(R.string.info_agenda_tomorrow, info.time)
                else -> stringResource(R.string.info_agenda_today, info.time)
            }
        }
        val data = if (info.kind == com.usos.launcher.data.info.InfoKind.MAPS) stringResource(R.string.info_go_home)
        else info.title.ifBlank { stringResource(R.string.info_untitled) }
        UsosText(label, UsosType.Label, LocalOnAccent.current)
        UsosText(data, UsosType.PieceData, LocalOnAccent.current, maxLines = 3)
    }
}

/** Pieza de acento del mosaico de Ocio abierto: congela y suma el tiempo gastado. */
@Composable
internal fun FreezeTile(minutesLeft: Int, modifier: Modifier, onFreeze: () -> Unit, onEnterEdit: () -> Unit) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Column(
        modifier
            .background(LocalAccent.current, shape)
            .pressGestures(onLongPress = onEnterEdit) { onFreeze() }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(stringResource(R.string.home_freeze), UsosType.Label, LocalOnAccent.current)
        UsosText(stringResource(R.string.home_minutes_short, minutesLeft), UsosType.PieceData, LocalOnAccent.current)
    }
}

/** Piezas propias por clave de configuración (las provee HomePage). */
internal val LocalCustomTiles = androidx.compose.runtime.staticCompositionLocalOf<Map<String, com.usos.launcher.data.tiles.CustomTile>> { emptyMap() }
internal val LocalMusicPlayer = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Pieza propia (destino, contactos), reproductor si es una app de música más grande que 1x1, o la pieza de app.
 */
@Composable
internal fun SmartOrAppTile(
    tile: TileUi, custom: com.usos.launcher.data.tiles.CustomTile?, colSpan: Int, rowSpan: Int,
    interactive: Boolean, onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit, onEnterEdit: () -> Unit,
) {
    val context = LocalContext.current
    val m = Modifier.fillMaxSize()
    when {
        custom?.kind == com.usos.launcher.data.tiles.TileKind.DEST ->
            DestinationTile(custom, colSpan, rowSpan, m, interactive, onOpen = { onOpenApp(tile.app) }, onLongPress = onEnterEdit)
        custom?.kind == com.usos.launcher.data.tiles.TileKind.CONTACTS ->
            ContactsTile(custom, colSpan, rowSpan, m, interactive,
                onOpenContact = { i -> com.usos.launcher.data.tiles.TileIntents.launch(context, custom, i) }, onLongPress = onEnterEdit)
        LocalMusicPlayer.current && (colSpan > 1 || rowSpan > 1) && com.usos.launcher.data.tiles.MusicApps.isMusic(tile.app.packageName, tile.app.category) ->
            MusicTile(tile.app.label, tile.app.packageName, colSpan, rowSpan, m, interactive,
                onOpenApp = { onOpenApp(tile.app) }, onLongPress = onEnterEdit) {
                com.usos.launcher.ui.theme.AppIconOrNull(tile.app, 24.dp, UsosColors.Text) { UsosText(tile.code, UsosType.SmallCode, UsosColors.Text) }
            }
        else -> AppTile(tile, m, interactive, onOpenApp, onEnterEdit)
    }
}

@Composable
internal fun AppTile(
    tile: TileUi,
    modifier: Modifier,
    interactive: Boolean,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onEnterEdit: () -> Unit,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    val custom = tile.highlightColor?.let { Color(it) }
    val fill = when (tile.highlight) {
        Highlight.NORMAL -> UsosColors.Surface
        Highlight.ACCENT -> accent
        Highlight.WHITE -> UsosColors.Text
        Highlight.OUTLINE -> UsosColors.Background
        Highlight.COLOR -> custom ?: UsosColors.Surface
    }
    val edge = when (tile.highlight) {
        Highlight.NORMAL -> UsosColors.Border
        Highlight.ACCENT -> accent
        Highlight.WHITE -> UsosColors.Text
        Highlight.OUTLINE -> UsosColors.Text
        Highlight.COLOR -> custom ?: UsosColors.Border
    }
    val filled = tile.highlight == Highlight.ACCENT || tile.highlight == Highlight.WHITE ||
        (tile.highlight == Highlight.COLOR && custom != null)
    // Texto e icono en negro o blanco segun la luminancia de la pieza (WCAG).
    val onFill = if (filled) fill.onColor() else UsosColors.Text
    val codeColor = onFill
    val nameColor = if (filled) onFill else UsosColors.TextSecondary
    val badge = com.usos.launcher.ui.theme.LocalNotifBadges.current
    val notify = badge.show(tile.app.key)
    val dotColor = if (fill == accent) onFill else accent
    val dotOn = notify && badge.mode == com.usos.launcher.data.notify.NotifMode.DOT
    val borderOn = notify && badge.mode == com.usos.launcher.data.notify.NotifMode.BORDER
    Box(modifier.alpha(if (tile.dimmed) 0.35f else 1f)) {
    Column(
        Modifier.fillMaxSize()
            .background(fill, shape)
            .border(if (borderOn) 1.5.dp else 1.dp, if (borderOn) dotColor else edge, shape)
            .then(if (interactive) Modifier.pressGestures(onLongPress = onEnterEdit) { onOpenApp(tile.app) } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TileHead(tile.app, tile.code, UsosType.TileCode, codeColor, 28.dp, invert = filled && onFill == UsosColors.OnAccent)
        Column {
            tile.usageMin?.let { UsosText(UsageSummary.format(it), UsosType.Label, codeColor) }
            UsosText(tile.app.label.uppercase(), UsosType.TileName, nameColor)
        }
    }
    if (dotOn) {
        com.usos.launcher.ui.theme.CountBubble(
            badge.count(tile.app.key), dotColor, dotColor.onColor(), Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
        )
    }
    }
}
