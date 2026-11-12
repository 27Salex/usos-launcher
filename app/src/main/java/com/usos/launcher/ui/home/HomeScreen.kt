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
import androidx.compose.runtime.LaunchedEffect
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
import com.usos.launcher.domain.Section
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
    val animations = LocalAnimationsEnabled.current
    val animationsNow by rememberUpdatedState(animations)
    val pager = rememberPagerState(initialPage = 1) { 3 }

    // Atrás en inicio no hace nada.
    BackHandler(enabled = true) { }

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
                    onToggle = viewModel::toggleSection,
                    onOpenApp = viewModel::openApp,
                )
            }
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
    onToggle: (Section) -> Unit,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
) {
    val now by rememberNow()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().padding(horizontal = UsosDimens.ScreenMargin)) {
        Spacer(Modifier.height(topSpacing()))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DotClock(now) }
        Spacer(Modifier.height(15.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DateLabel(now.toLocalDate()) }
        Spacer(Modifier.height(16.dp))
        WeekStrip(now.toLocalDate())
        Spacer(Modifier.height(12.dp))

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state != null) {
                state.sections.forEach { s ->
                    SectionBlock(s, isOpen = state.open == s.section, onToggle = onToggle, onOpenApp = onOpenApp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SearchBar(notice)
        Spacer(Modifier.height(12.dp + navBar))
    }
}

@Composable
private fun SectionBlock(
    s: SectionUi,
    isOpen: Boolean,
    onToggle: (Section) -> Unit,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
) {
    val animations = LocalAnimationsEnabled.current
    Row(
        Modifier.fillMaxWidth().height(SectionRowHeight).tap { onToggle(s.section) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(s.section.title, UsosType.SectionName, UsosColors.Text)
        UsosText(if (isOpen) "—" else s.count.toString(), UsosType.SectionData, UsosColors.TextSecondary)
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
        Mosaic(
            tiles = s.tiles,
            onOpenApp = onOpenApp,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 12.dp)
                .graphicsLayer {
                    alpha = rise
                    translationY = (1f - rise) * risePx
                },
        )
    }
}

/** Rejilla 3 x 2; ancho de celda = (ancho útil - 22 dp) / 3. */
@Composable
private fun Mosaic(
    tiles: List<TileUi>,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = (maxWidth - TileColumnGap * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileColumnGap)) {
                    row.forEach { tile -> AppTile(tile, Modifier.size(cell, TileHeight), onOpenApp) }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    tile: TileUi,
    modifier: Modifier,
    onOpenApp: (com.usos.launcher.domain.AppEntry) -> Unit,
) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Column(
        modifier
            .alpha(if (tile.dimmed) 0.35f else 1f)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .tap { onOpenApp(tile.app) }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(tile.code, UsosType.TileCode, UsosColors.Text)
        UsosText(tile.app.label.uppercase(), UsosType.TileName, UsosColors.TextSecondary)
    }
}

/** Barra fija de búsqueda; en la Fase 1 solo muestra avisos. */
@Composable
private fun SearchBar(notice: String?) {
    val shape = RoundedCornerShape(UsosDimens.RadiusSearch)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        UsosText(notice ?: "BUSCAR", UsosType.SearchHint, UsosColors.TextSecondary)
    }
}
