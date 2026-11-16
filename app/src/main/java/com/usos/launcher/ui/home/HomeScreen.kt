package com.usos.launcher.ui.home

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

internal val SectionRowHeight = 46.dp
internal val TileColumnGap = 11.dp
internal val TileRowGap = 10.dp
internal val ClockTopOffset = 60.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddWidget: (android.appwidget.AppWidgetProviderInfo) -> Unit,
    onReconfigureWidget: (Int) -> Unit,
) {
    val feedsVm: com.usos.launcher.ui.feeds.FeedsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = com.usos.launcher.ui.feeds.FeedsViewModel.Factory)
    var feedManager by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val leisure by viewModel.leisure.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchItems by viewModel.searchItems.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val blockerActive by viewModel.blockerActive.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val widgets by viewModel.widgets.collectAsStateWithLifecycle()
    val calendarGrantedW by viewModel.calendarGranted.collectAsStateWithLifecycle()
    val mosaicCols by viewModel.mosaicPrefs.collectAsStateWithLifecycle()
    val assistantUi by viewModel.assistantUi.collectAsStateWithLifecycle()
    val searchKeyboardOn by viewModel.searchKeyboard.collectAsStateWithLifecycle()
    val mosaicPrefsForDrawer by viewModel.mosaicPrefs.collectAsStateWithLifecycle()
    val pwaKeywords by viewModel.pwaKeywords.collectAsStateWithLifecycle()
    val mosaicPrefsHome by viewModel.mosaicPrefs.collectAsStateWithLifecycle()
    var aiPicker by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val grayscale by viewModel.widgetsGrayscale.collectAsStateWithLifecycle()
    val clockPrefs by viewModel.clockPrefs.collectAsStateWithLifecycle()
    val weatherPrefs by viewModel.weatherPrefs.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val orderLists by viewModel.orderLists.collectAsStateWithLifecycle()
    val animations = LocalAnimationsEnabled.current
    val animationsNow by rememberUpdatedState(animations)
    val pager = rememberPagerState(initialPage = 1) { 3 }
    var widgetsEditing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val edit = remember { EditState() }
    val searchProgress = remember { Animatable(0f) }
    LaunchedEffect(searchOpen, animations) {
        val target = if (searchOpen) 1f else 0f
        try {
            if (animations) searchProgress.animateTo(target, usosTween(true)) else searchProgress.snapTo(target)
        } finally {
            // Si la animación se corta, el progreso nunca queda a medias con la búsqueda cerrada (el overlay taparía el pager).
            if (!searchOpen) withContext(NonCancellable) { searchProgress.snapTo(0f) }
        }
    }

    // Atrás cierra la búsqueda; en inicio no hace nada más.
    BackHandler(enabled = true) { if (aiPicker) aiPicker = false else if (searchOpen) viewModel.closeSearch() else if (editing) { if (edit.selectedKey != null) edit.clearSelection() else viewModel.requestExitEdit() } }


    // Al abrir el cajón (búsqueda) o entrar en edición el pager vuelve a Inicio sin animación. No se usa
    // requestScrollToPage (se aplica en la siguiente medición, aun con un arrastre de usuario en curso): se espera
    // a que no haya gesto y se hace scrollToPage, que toma el mutex del scroll de forma ordenada.
    suspend fun goHomeNow() {
        if (pager.currentPage == PagerLock.HOME_PAGE && pager.currentPageOffsetFraction == 0f) return
        withTimeoutOrNull(500) { snapshotFlow { pager.isScrollInProgress }.first { !it } }
        pager.scrollToPage(PagerLock.HOME_PAGE)
    }
    LaunchedEffect(searchOpen) { if (searchOpen) goHomeNow() }
    LaunchedEffect(editing) {
        if (!editing) edit.reset() else goHomeNow() // «Editar inicio» desde Ajustes puede llegar con el pager en Widgets/Noticias
    }

    LaunchedEffect(pager) {
        viewModel.homeEvents.collect {
            widgetsEditing = false // el botón inicio sale también de la edición de widgets (si no, el pager quedaba bloqueado)
            edit.reset()
            if (animationsNow) pager.animateScrollToPage(PagerLock.HOME_PAGE, animationSpec = usosTween(true))
            else pager.scrollToPage(PagerLock.HOME_PAGE)
        }
    }
    // Red de seguridad: la edición de widgets solo existe en su página; al salir de ella se apaga.
    LaunchedEffect(pager) {
        snapshotFlow { PagerLock.widgetsEditingValid(widgetsEditing, pager.settledPage) to pager.settledPage }
            .collect { (valid, page) -> if (!valid && page != PagerLock.WIDGETS_PAGE) widgetsEditing = false }
    }
    // Si el pager queda a medio camino sin gesto en curso (p. ej. un arrastre cancelado al abrir el cajón), se asienta.
    LaunchedEffect(pager) {
        snapshotFlow { PagerLock.needsSettle(pager.isScrollInProgress, pager.currentPageOffsetFraction) }
            .collect { if (it) pager.scrollToPage(pager.currentPage) }
    }
    if (BuildConfig.DEBUG) {
        LaunchedEffect(editing) { Log.d("UsosPager", "editing=$editing") }
        LaunchedEffect(widgetsEditing) { Log.d("UsosPager", "widgetsEditing=$widgetsEditing") }
        LaunchedEffect(searchOpen) { Log.d("UsosPager", "searchOpen=$searchOpen") }
        LaunchedEffect(pager) {
            launch { snapshotFlow { pager.isScrollInProgress }.collect { Log.d("UsosPager", "isScrollInProgress=$it") } }
            launch { snapshotFlow { pager.settledPage }.collect { Log.d("UsosPager", "settledPage=$it") } }
            launch { snapshotFlow { pager.currentPageOffsetFraction }.collect { Log.d("UsosPager", "offsetFraction=$it") } }
        }
    }

    val iconPrefs by viewModel.iconPrefs.collectAsStateWithLifecycle()
    val dockPrefs by viewModel.dockPrefs.collectAsStateWithLifecycle()
    val dockApps by viewModel.dockApps.collectAsStateWithLifecycle()
    var dockPicking by remember { mutableStateOf<Int?>(null) }
    val notifMode by viewModel.notifMode.collectAsStateWithLifecycle()
    val notifCounts by com.usos.launcher.service.NotificationState.counts.collectAsStateWithLifecycle()
    val notifBadges = remember(notifMode, notifCounts) { com.usos.launcher.ui.theme.NotifBadges(notifMode, notifCounts) }
    CompositionLocalProvider(
        LocalIconPrefs provides iconPrefs, LocalIcons provides viewModel.iconRepository,
        com.usos.launcher.ui.theme.LocalNotifBadges provides notifBadges,
    ) {
    val bgPrefs by viewModel.background.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        com.usos.launcher.ui.theme.AppBackground(bgPrefs)
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize(),
            userScrollEnabled = PagerLock.scrollEnabled(editing, widgetsEditing, searchOpen, pager.currentPage),
        ) { page ->
            when (page) {
                0 -> com.usos.launcher.ui.widgets.WidgetsPage(
                    widgets = widgets,
                    columns = mosaicCols.columns,
                    grayscale = grayscale,
                    host = viewModel.widgetHost,
                    manager = viewModel.widgetManager,
                    actions = com.usos.launcher.ui.widgets.WidgetsActions(
                        onAdd = onAddWidget,
                        onLayout = viewModel::applyWidgetLayout,
                        onRemove = viewModel::removeWidget,
                        onReconfigure = onReconfigureWidget,
                        onAddOwn = viewModel::addOwnWidget,
                        onConfigOwn = viewModel::setWidgetConfig,
                        onRequestCalendar = viewModel::requestCalendar,
                    ),
                    topSpacing = topSpacing(),
                    onOpenSettings = { viewModel.openSettings(SettingsCat.WIDGETS) },
                    apps = installedApps.orEmpty(),
                    calendarGranted = calendarGrantedW,
                    editing = widgetsEditing,
                    onEditingChange = { widgetsEditing = it },
                )
                2 -> com.usos.launcher.ui.feeds.FeedsPage(feedsVm, topSpacing(), onManage = { feedManager = true }, onOpenSettings = { viewModel.openSettings(SettingsCat.NEWS) })
                else -> HomePage(
                    state = state,
                    notice = notice,
                    leisure = leisure,
                    blockerActive = blockerActive,
                    searchActive = searchOpen || searchProgress.value > 0f,
                    onToggle = viewModel::toggleSection,
                    onResumeWork = viewModel::resumeWorkProfile,
                    onOpenApp = viewModel::openApp,
                    onOpenSearch = viewModel::openSearch,
                    gesturesOn = route == Route.Home && !searchOpen,
                    editing = editing,
                    orderLists = orderLists,
                    edit = edit,
                    editActions = remember(viewModel) {
                        EditActions(
                            onExit = viewModel::requestExitEdit,
                            onPlace = viewModel::placeTile,
                            onToSection = { k, sec -> viewModel.setSection(k, sec) },
                            onRemove = viewModel::removeFromMosaic,
                            onResize = viewModel::resizeTile,
                            onResizeInfo = viewModel::resizeInfo,
                            onResizeAt = viewModel::resizeTileTo,
                            onRows = viewModel::changeRows,
                            onDetail = viewModel::openDetailByKey,
                        )
                    },
                    onEnterEdit = viewModel::enterEdit,
                    onThaw = viewModel::startThaw,
                    onCancelThaw = viewModel::cancelThaw,
                    onFreeze = viewModel::freezeLeisure,
                    onOpenInfo = viewModel::openInfo,
                    onNewTile = viewModel::newCustomTile,
                    tabsMode = mosaicPrefsHome.tabs,
                    musicPlayer = mosaicPrefsHome.musicPlayer,
                    dock = if (!dockPrefs.on) null else { ed, hide ->
                        Dock(
                            dockApps, ed, hide, onOpen = viewModel::openApp, onPick = { dockPicking = it },
                            onClear = { viewModel.setDockApp(it, null) }, onEnterEdit = viewModel::enterEdit,
                        )
                    },
                    clock = clockPrefs,
                    weatherPrefs = weatherPrefs,
                    weather = weather,
                    onOpenWeather = viewModel::openWeatherApp,
                    onOpenClock = viewModel::openClock,
                    assistant = assistantUi,
                    onAssistant = viewModel::openAssistant,
                    onAssistantPicker = { aiPicker = true },
                )
            }
        }
        SearchOverlay(
            open = searchOpen,
            progress = searchProgress.value,
            query = query,
            items = searchItems,
            suggestions = suggestions,
            onSuggestion = viewModel::pickSuggestion,
            onQueryChange = viewModel::onQueryChange,
            onSubmit = viewModel::submitSearch,
            onPick = viewModel::pickFromSearch,
            onLongPick = { viewModel.openDetail(it.app) },
            onOpenSettings = viewModel::openSettings,
            onClose = viewModel::closeSearch,
            autoKeyboard = searchKeyboardOn,
            tabs = mosaicPrefsForDrawer.drawerTabs,
            pwaKeywords = pwaKeywords,
            assistantName = assistantUi.resolved?.assistant?.label,
            onAsk = viewModel::askAssistant,
            restEndInset = if (assistantUi.resolved != null) AiButtonSize + AiGap else 0.dp,
        )
        if (aiPicker) {
            AssistantPicker(assistantUi, onPick = { aiPicker = false; viewModel.setAssistantChoice(it) }, onClose = { aiPicker = false })
        }

        // Pantallas superpuestas: cada una registra su propio "atras" y vuelve al inicio.
        when (val r = route) {
            is Route.Detail -> detail?.let { d0 ->
                val recent by viewModel.recentColors.collectAsStateWithLifecycle()
                val ui = d0.copy(recentColors = recent)
                AppDetailScreen(
                    ui,
                    DetailActions(
                        onShowUsage = { viewModel.setShowUsage(r.key, it) },
                        onSection = { viewModel.setSection(r.key, it) },
                        onInMosaic = { viewModel.setInMosaic(r.key, it) },
                        onCode = { viewModel.setCode(r.key, it) },
                        onHighlight = { viewModel.setHighlight(r.key, it) },
                        onHighlightColor = { viewModel.setHighlightColor(r.key, it) },
                        onSize = { viewModel.setTileSize(r.key, it) },
                        onRows = { n -> viewModel.setRows(ui.config.section, n) },
                        onBack = viewModel::closeOverlay,
                    ),
                )
            }
            is Route.Settings -> SettingsRoute(viewModel, r.category, grayscale, iconPrefs, assistantUi, bgPrefs, dockPrefs)
            Route.Onboarding -> OnboardingScreen(onFinish = viewModel::finishOnboarding)
            Route.Home -> Unit
        }
        dockPicking?.let { slot ->
            DockPicker(
                apps = installedApps.orEmpty(), current = dockPrefs.at(slot),
                onPick = { viewModel.setDockApp(slot, it.key); dockPicking = null },
                onClear = { viewModel.setDockApp(slot, null); dockPicking = null },
                onClose = { dockPicking = null },
            )
        }
        if (feedManager) {
            com.usos.launcher.ui.feeds.FeedManagerScreen(feedsVm, onBack = { feedManager = false })
        }
        val pendingHidden by viewModel.pendingHidden.collectAsStateWithLifecycle()
        pendingHidden?.let { hidden ->
            ConfirmEditDialog(hidden.size, onConfirm = viewModel::confirmExitEdit, onKeepEditing = viewModel::keepEditing)
        }
        val leisureSheet by viewModel.leisureSheet.collectAsStateWithLifecycle()
        if (leisureSheet) {
            LeisureSheet(leisure, onThaw = viewModel::startThaw, onCancelThaw = viewModel::cancelThaw, onClose = viewModel::closeLeisureSheet)
        }
        val tileEditing by viewModel.tileEditing.collectAsStateWithLifecycle()
        tileEditing?.let { ed ->
            val key = com.usos.launcher.data.tiles.CustomTiles.configKey(ed.tile.id)
            val onDelete: (() -> Unit)? = if (ed.isNew) null else ({ viewModel.deleteCustomTile(key) })
            if (ed.tile.kind == com.usos.launcher.data.tiles.TileKind.DEST) {
                val navApps = remember(installedApps) {
                    com.usos.launcher.data.tiles.NavApps.installed(installedApps.orEmpty().map { it.packageName })
                }
                DestinationEditor(ed.tile.takeIf { !ed.isNew }, navApps, onSave = { viewModel.saveCustomTile(it.copy(id = ed.tile.id)) },
                    onDelete = onDelete, onClose = viewModel::closeTileEditor)
            } else {
                ContactsEditor(ed.tile.takeIf { !ed.isNew }, onSave = { viewModel.saveCustomTile(it.copy(id = ed.tile.id)) },
                    onDelete = onDelete, onClose = viewModel::closeTileEditor)
            }
        }
    }
    }
}

@Composable
internal fun topSpacing(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return max(ClockTopOffset, statusBar + 16.dp)
}
