package com.usos.launcher.ui.home

// Página central del inicio: reloj, semana, secciones (acordeón o pestañas), dock y barra de búsqueda.

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
internal fun HomePage(
    state: HomeUiState?,
    notice: String?,
    leisure: LeisureStatus?,
    searchActive: Boolean,
    onToggle: (Section) -> Unit,
    onResumeWork: () -> Unit,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    blockerActive: Boolean,
    onOpenSearch: () -> Unit,
    gesturesOn: Boolean,
    editing: Boolean,
    orderLists: List<Pair<Section, List<com.usos.launcher.data.sections.PlacedApp>>>,
    editActions: EditActions,
    edit: EditState,
    onEnterEdit: () -> Unit,
    onThaw: (Int) -> Unit,
    onCancelThaw: () -> Unit,
    onFreeze: () -> Unit,
    onOpenInfo: (com.usos.launcher.data.info.InfoKind) -> Unit,
    clock: com.usos.launcher.data.sections.ClockPrefs,
    weatherPrefs: com.usos.launcher.data.sections.WeatherPrefs,
    weather: com.usos.launcher.data.weather.WeatherNow?,
    onOpenWeather: () -> Unit,
    onOpenClock: () -> Unit,
    assistant: HomeViewModel.AssistantUi,
    onAssistant: () -> Unit,
    onAssistantPicker: () -> Unit,
    dock: (@Composable (editing: Boolean, hidden: Boolean) -> Unit)? = null,
    onNewTile: (com.usos.launcher.data.tiles.TileKind, Section, Cell) -> Unit = { _, _, _ -> },
    tabsMode: Boolean = false,
    musicPlayer: Boolean = false,
) {
    val now by rememberNow()
    val nightCtx = LocalContext.current
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val accent = LocalAccent.current
    val scroll = rememberScrollState()
    edit.sections = remember(state) { state?.sections?.associateBy { it.section }.orEmpty() }
    // La colocación soltada se olvida como mucho 900 ms después (normalmente antes, al llegar el estado guardado).
    LaunchedEffect(edit.held) { if (edit.held != null) { delay(900); edit.held = null } }
    var adding by remember { mutableStateOf<Pair<Section, Cell>?>(null) }
    var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var listCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val gestures = WindowInsets.systemGestures
    val gestureL = gestures.getLeft(density, LayoutDirection.Ltr)
    val gestureR = gestures.getRight(density, LayoutDirection.Ltr)
    val gestureB = gestures.getBottom(density)

    // Deslizar hacia arriba abre la búsqueda: fuera de la zona de gestos del sistema y, dentro de la lista,
    // solo si ésta no puede seguir desplazándose (así no se pelea con su scroll).
    val accept by rememberUpdatedState<(Offset) -> Boolean> { p ->
        val page = pageCoords
        if (page == null) false
        else if (p.y > page.size.height - gestureB || p.x < gestureL || p.x > page.size.width - gestureR) false
        else {
            val abs = page.positionInRoot() + p
            val inList = listCoords?.rootRect()?.contains(abs) == true
            !inList || !scroll.canScrollForward
        }
    }
    val openSearchNow by rememberUpdatedState(onOpenSearch)

    LaunchedEffect(edit.dragKey) {
        if (edit.dragKey == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        while (true) {
            val r = listCoords?.rootRect()
            if (r != null) {
                val y = edit.pointer.y
                val d = if (y < r.top + edge) -18f else if (y > r.bottom - edge) 18f else 0f
                if (d != 0f && edit.dragging) {
                    scroll.scrollBy(d)
                    edit.updateHover()
                }
            }
            withFrameNanos { }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalCustomTiles provides (state?.customTiles ?: emptyMap()), LocalMusicPlayer provides musicPlayer) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { pageCoords = it }) {
        Column(
            Modifier.fillMaxSize()
                .swipeVertical(enabled = gesturesOn && !editing, accept = { accept(it) }, onUp = { openSearchNow() })
                .tapOutside(
                    enabled = editing && edit.selectedKey != null,
                    inside = { edit.frozenRect ?: edit.selectedRect },
                    origin = { pageCoords?.positionInRoot() ?: Offset.Zero },
                ) { if (!edit.dragging) edit.clearSelection() }
                .padding(horizontal = UsosDimens.ScreenMargin),
        ) {
            Spacer(Modifier.height(topSpacing()))
            ClockBlock(
                now, clock, weatherPrefs, weather, onWeather = onOpenWeather, onClock = onOpenClock,
                onLongClock = { NightstandActivity.open(nightCtx) },
            )
            Spacer(Modifier.height(16.dp))
            WeekStrip(now.toLocalDate())
            Spacer(Modifier.height(12.dp))

            val tabs = tabsMode
            if (state != null && tabs) {
                SectionTabs(state, leisure, edit, onSelect = { sec -> edit.clearSelection(); onToggle(sec) })
                Spacer(Modifier.height(10.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { listCoords = it; edit.viewport = it }.verticalScroll(scroll)) {
                if (state != null) {
                    state.sections.filter { !tabs || it.section == state.open }.forEach { s ->
                        SectionBlock(
                            s, isOpen = state.open == s.section, tabbed = tabs, leisure = leisure, blockerActive = blockerActive,
                            workPaused = state.workPaused && s.section == Section.WORK, onResumeWork = onResumeWork,
                            editing = editing, edit = edit, editActions = editActions,
                            onAdd = { cell -> adding = s.section to cell },
                            onToggle = { sec -> edit.clearSelection(); onToggle(sec) }, onOpenApp = onOpenApp, onEnterEdit = onEnterEdit,
                            onThaw = onThaw, onCancelThaw = onCancelThaw, onFreeze = onFreeze, onOpenInfo = onOpenInfo,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (dock != null) {
                dock(editing, searchActive || edit.dragging)
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val hide = searchActive || edit.dragging
                SearchBar(notice, hidden = hide, onClick = onOpenSearch, modifier = Modifier.weight(1f))
                if (assistant.resolved != null) {
                    Spacer(Modifier.width(AiGap))
                    AiButton(assistant, hidden = hide || editing, onClick = onAssistant, onLongClick = onAssistantPicker)
                }
            }
            Spacer(Modifier.height(12.dp + navBar))
        }

        if (editing) EditTopBar(onDone = editActions.onExit, modifier = Modifier.padding(top = statusTop))
        if (editing) EditOverlay(edit, editActions, pageCoords)

        edit.dragTile?.let { t ->
            Box(
                Modifier
                    .offset { IntOffset((edit.pointer.x - edit.grab.x).roundToInt(), (edit.pointer.y - edit.grab.y).roundToInt()) }
                    .size(with(density) { edit.dragSize.width.toDp() }, with(density) { edit.dragSize.height.toDp() }),
            ) {
                AppTile(t, Modifier.fillMaxSize(), interactive = false, onOpenApp = {}, onEnterEdit = {})
                Box(Modifier.fillMaxSize().border(2.dp, accent, RoundedCornerShape(UsosDimens.RadiusPiece)))
            }
        }
        if (edit.dragging) {
            RemoveZone(
                edit.hoverRemove,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = UsosDimens.ScreenMargin)
                    .padding(bottom = 12.dp + navBar).onGloballyPositioned { edit.removeZone = it },
            )
        }
        adding?.let { (section, cell) ->
            val keysShown = state?.sections?.firstOrNull { it.section == section }?.placements?.map { it.key }.orEmpty().toSet()
            AddPicker(
                section, orderLists, keysShown,
                onPick = { editActions.onPlace(it.config.key, section, cell); adding = null },
                onClose = { adding = null },
                extraTab = stringResource(R.string.edit_tab_pieces),
                extraContent = {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.usos.launcher.ui.theme.UsosPill(stringResource(R.string.stile_dest_title), filled = false, modifier = Modifier.fillMaxWidth(),
                            onClick = { adding = null; onNewTile(com.usos.launcher.data.tiles.TileKind.DEST, section, cell) })
                        com.usos.launcher.ui.theme.UsosPill(stringResource(R.string.stile_contacts_title), filled = false, modifier = Modifier.fillMaxWidth(),
                            onClick = { adding = null; onNewTile(com.usos.launcher.data.tiles.TileKind.CONTACTS, section, cell) })
                        UsosText(stringResource(R.string.edit_pieces_hint), UsosType.Label, UsosColors.TextSecondary)
                    }
                },
            )
        }
    }
    }
}
