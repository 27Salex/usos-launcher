package com.usos.launcher.ui.home

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
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
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap
import com.usos.launcher.ui.theme.usosTween

private val SectionRowHeight = 46.dp
private val TileHeight = 95.dp
private val TileColumnGap = 11.dp
private val TileRowGap = 10.dp
private val ClockTopOffset = 60.dp

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val leisure by viewModel.leisure.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchItems by viewModel.searchItems.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val blockerActive by viewModel.blockerActive.collectAsStateWithLifecycle()
    val animations = LocalAnimationsEnabled.current
    val animationsNow by rememberUpdatedState(animations)
    val pager = rememberPagerState(initialPage = 1) { 3 }
    val searchProgress = remember { Animatable(0f) }
    LaunchedEffect(searchOpen, animations) {
        val target = if (searchOpen) 1f else 0f
        if (animations) searchProgress.animateTo(target, usosTween(true)) else searchProgress.snapTo(target)
    }

    // Atrás cierra la búsqueda; en inicio no hace nada más.
    BackHandler(enabled = true) { if (searchOpen) viewModel.closeSearch() }

    LaunchedEffect(pager) {
        viewModel.homeEvents.collect {
            if (animationsNow) pager.animateScrollToPage(1, animationSpec = usosTween(true))
            else pager.scrollToPage(1)
        }
    }

    Box(Modifier.fillMaxSize().background(UsosColors.Background)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> PanelPage("Widgets")
                2 -> PanelPage("Noticias")
                else -> HomePage(
                    state = state,
                    notice = notice,
                    leisure = leisure,
                    blockerActive = blockerActive,
                    searchActive = searchOpen || searchProgress.value > 0f,
                    onToggle = viewModel::toggleSection,
                    onOpenApp = viewModel::openApp,
                    onLongPressApp = viewModel::openDetail,
                    onOpenSettings = viewModel::openSettings,
                    onOpenSearch = viewModel::openSearch,
                    onThaw = viewModel::startThaw,
                    onCancelThaw = viewModel::cancelThaw,
                    onFreeze = viewModel::freezeLeisure,
                )
            }
        }
        SearchOverlay(
            open = searchOpen,
            progress = searchProgress.value,
            query = query,
            items = searchItems,
            onQueryChange = viewModel::onQueryChange,
            onSubmit = viewModel::submitSearch,
            onPick = viewModel::pickFromSearch,
            onLongPick = { viewModel.openDetail(it.app) },
            onClose = viewModel::closeSearch,
        )
        // Pantallas superpuestas: cada una registra su propio "atras" y vuelve al inicio.
        when (val r = route) {
            is Route.Detail -> detail?.let { ui ->
                AppDetailScreen(
                    ui,
                    DetailActions(
                        onShowUsage = { viewModel.setShowUsage(r.key, it) },
                        onSection = { viewModel.setSection(r.key, it) },
                        onInMosaic = { viewModel.setInMosaic(r.key, it) },
                        onCode = { viewModel.setCode(r.key, it) },
                        onHighlight = { viewModel.setHighlight(r.key, it) },
                        onBack = viewModel::closeOverlay,
                    ),
                )
            }
            Route.Settings -> {
                val budget by viewModel.budget.collectAsStateWithLifecycle()
                val wait by viewModel.wait.collectAsStateWithLifecycle()
                val orderLists by viewModel.orderLists.collectAsStateWithLifecycle()
                SettingsScreen(
                    budget, wait, orderLists,
                    SettingsActions(
                        onBudget = viewModel::setBudget,
                        onWait = viewModel::setWait,
                        onAccent = viewModel::setAccent,
                        onMove = viewModel::moveInSection,
                        onRestartOnboarding = viewModel::restartOnboarding,
                        onBack = viewModel::closeOverlay,
                    ),
                )
            }
            Route.Onboarding -> OnboardingScreen(onFinish = viewModel::finishOnboarding)
            Route.Home -> Unit
        }
    }
}

@Composable
private fun topSpacing(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return max(ClockTopOffset, statusBar + 16.dp)
}

@Composable
private fun PanelPage(title: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = UsosDimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(topSpacing()))
        UsosText(title, UsosType.PanelTitle, UsosColors.Text)
    }
}

@Composable
private fun HomePage(
    state: HomeUiState?,
    notice: String?,
    leisure: LeisureStatus?,
    searchActive: Boolean,
    onToggle: (Section) -> Unit,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onLongPressApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    blockerActive: Boolean,
    onOpenSearch: () -> Unit,
    onThaw: (Int) -> Unit,
    onCancelThaw: () -> Unit,
    onFreeze: () -> Unit,
) {
    val now by rememberNow()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().padding(horizontal = UsosDimens.ScreenMargin)) {
        Spacer(Modifier.height(topSpacing()))
        Box(Modifier.fillMaxWidth().tap(onLongClick = onOpenSettings) { }, contentAlignment = Alignment.Center) { DotClock(now) }
        Spacer(Modifier.height(15.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DateLabel(now.toLocalDate()) }
        Spacer(Modifier.height(16.dp))
        WeekStrip(now.toLocalDate())
        Spacer(Modifier.height(12.dp))

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state != null) {
                state.sections.forEach { s ->
                    SectionBlock(
                        s, isOpen = state.open == s.section, leisure = leisure, blockerActive = blockerActive,
                        onToggle = onToggle, onOpenApp = onOpenApp, onLongPressApp = onLongPressApp,
                        onThaw = onThaw, onCancelThaw = onCancelThaw, onFreeze = onFreeze,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SearchBar(notice, hidden = searchActive, onClick = onOpenSearch)
        Spacer(Modifier.height(12.dp + navBar))
    }
}

@Composable
private fun SectionBlock(
    s: SectionUi,
    isOpen: Boolean,
    leisure: LeisureStatus?,
    blockerActive: Boolean,
    onToggle: (Section) -> Unit,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onLongPressApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onThaw: (Int) -> Unit,
    onCancelThaw: () -> Unit,
    onFreeze: () -> Unit,
) {
    val animations = LocalAnimationsEnabled.current
    val accent = LocalAccent.current
    val isLeisure = s.section == Section.LEISURE
    val frozen = isLeisure && leisure?.isOpen != true
    val nameColor = if (frozen) UsosColors.LeisureFrozen else UsosColors.Text
    val data: String = when {
        !isLeisure -> if (isOpen) "—" else s.count.toString()
        leisure is LeisureStatus.Open -> "${leisure.minutesLeft} MIN"
        leisure is LeisureStatus.Thawing -> "${leisure.secondsLeft} S"
        else -> "CONGELADO"
    }
    Row(
        Modifier.fillMaxWidth().height(SectionRowHeight).tap { onToggle(s.section) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(s.section.title, UsosType.SectionName, nameColor)
        UsosText(data, UsosType.SectionData, if (isLeisure) accent else UsosColors.TextSecondary)
    }
    AnimatedVisibility(
        visible = isOpen,
        enter = if (animations) {
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
                    UsosText("BLOQUEO INACTIVO · ACTIVAR", UsosType.Label, accent)
                }
            }
            if (isLeisure && leisure !is LeisureStatus.Open) {
                LeisurePanel(leisure, onThaw, onCancelThaw)
            } else {
                Mosaic(
                    tiles = s.tiles,
                    onOpenApp = onOpenApp,
                    onLongPressApp = onLongPressApp,
                    trailing = (leisure as? LeisureStatus.Open)?.let { open ->
                        { cellModifier -> FreezeTile(open.minutesLeft, cellModifier, onFreeze) }
                    },
                )
            }
        }
    }
}

/** Rejilla 3 x 2; ancho de celda = (ancho útil - 22 dp) / 3. */
@Composable
private fun Mosaic(
    tiles: List<TileUi>,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onLongPressApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable (Modifier) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = (maxWidth - TileColumnGap * 2) / 3
        val cells: List<@Composable (Modifier) -> Unit> =
            tiles.map { tile ->
                val c: @Composable (Modifier) -> Unit = { m -> AppTile(tile, m, onOpenApp, onLongPressApp) }
                c
            } + listOfNotNull(trailing)
        Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
            cells.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileColumnGap)) {
                    row.forEach { content -> content(Modifier.size(cell, TileHeight)) }
                }
            }
        }
    }
}

/** Pieza de acento del mosaico de Ocio abierto: congela y suma el tiempo gastado. */
@Composable
private fun FreezeTile(minutesLeft: Int, modifier: Modifier, onFreeze: () -> Unit) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Column(
        modifier
            .background(LocalAccent.current, shape)
            .tap { onFreeze() }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText("CONGELAR ·", UsosType.Label, UsosColors.OnAccent)
        UsosText("$minutesLeft min", UsosType.PieceData, UsosColors.OnAccent)
    }
}

@Composable
private fun AppTile(
    tile: TileUi,
    modifier: Modifier,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    onLongPressApp: (com.usos.launcher.domain.AppEntry) -> Unit,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    val fill = when (tile.highlight) {
        Highlight.NORMAL -> UsosColors.Surface
        Highlight.ACCENT -> accent
        Highlight.WHITE -> UsosColors.Text
        Highlight.OUTLINE -> UsosColors.Background
    }
    val edge = when (tile.highlight) {
        Highlight.NORMAL -> UsosColors.Border
        Highlight.ACCENT -> accent
        Highlight.WHITE -> UsosColors.Text
        Highlight.OUTLINE -> UsosColors.Text
    }
    val filled = tile.highlight == Highlight.ACCENT || tile.highlight == Highlight.WHITE
    val codeColor = if (filled) UsosColors.OnAccent else UsosColors.Text
    val nameColor = if (filled) UsosColors.OnAccent else UsosColors.TextSecondary
    Column(
        modifier
            .alpha(if (tile.dimmed) 0.35f else 1f)
            .background(fill, shape)
            .border(1.dp, edge, shape)
            .tap(onLongClick = { onLongPressApp(tile.app) }) { onOpenApp(tile.app) }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(tile.code, UsosType.TileCode, codeColor)
        Column {
            tile.usageMin?.let { UsosText(UsageSummary.format(it), UsosType.Label, codeColor) }
            UsosText(tile.app.label.uppercase(), UsosType.TileName, nameColor)
        }
    }
}

/** Barra fija de búsqueda. Al tocarla sube y se convierte en campo (SearchOverlay). */
@Composable
private fun SearchBar(notice: String?, hidden: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(UsosDimens.RadiusSearch)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (hidden) 0f else 1f)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .tap(enabled = !hidden) { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        UsosText(notice ?: "BUSCAR", UsosType.SearchHint, UsosColors.TextSecondary)
    }
}
