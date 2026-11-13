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
import com.usos.launcher.ui.theme.verticalGestureLock
import kotlin.math.roundToInt
import com.usos.launcher.ui.theme.usosTween

private val SectionRowHeight = 46.dp
private val TileColumnGap = 11.dp
private val TileRowGap = 10.dp
private val ClockTopOffset = 60.dp

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
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val blockerActive by viewModel.blockerActive.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val widgets by viewModel.widgets.collectAsStateWithLifecycle()
    val mosaicCols by viewModel.mosaicPrefs.collectAsStateWithLifecycle()
    val assistantUi by viewModel.assistantUi.collectAsStateWithLifecycle()
    val searchKeyboardOn by viewModel.searchKeyboard.collectAsStateWithLifecycle()
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
    var verticalGesture by remember { mutableStateOf(false) }
    var widgetsEditing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val edit = remember { EditState() }
    val searchProgress = remember { Animatable(0f) }
    LaunchedEffect(searchOpen, animations) {
        val target = if (searchOpen) 1f else 0f
        if (animations) searchProgress.animateTo(target, usosTween(true)) else searchProgress.snapTo(target)
    }

    // Atrás cierra la búsqueda; en inicio no hace nada más.
    BackHandler(enabled = true) { if (aiPicker) aiPicker = false else if (searchOpen) viewModel.closeSearch() else if (editing) { if (edit.selectedKey != null) edit.clearSelection() else viewModel.exitEdit() } }


    // Al abrir el cajón (búsqueda) el pager vuelve a Inicio sin animación: al cerrarlo nunca se ve otra página.
    LaunchedEffect(searchOpen) {
        if (searchOpen) pager.requestScrollToPage(1)
    }
    LaunchedEffect(editing) { if (!editing) edit.reset() }

    LaunchedEffect(pager) {
        viewModel.homeEvents.collect {
            if (animationsNow) pager.animateScrollToPage(1, animationSpec = usosTween(true))
            else pager.scrollToPage(1)
        }
    }

    val iconPrefs by viewModel.iconPrefs.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalIconPrefs provides iconPrefs, LocalIcons provides viewModel.iconRepository) {
    Box(Modifier.fillMaxSize().background(UsosColors.Background).verticalGestureLock { verticalGesture = it }) {
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !editing && !widgetsEditing && !verticalGesture && !searchOpen && searchProgress.value == 0f,
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
                        onReorder = viewModel::reorderWidgets,
                        onResize = viewModel::resizeWidget,
                        onRemove = viewModel::removeWidget,
                        onReconfigure = onReconfigureWidget,
                    ),
                    topSpacing = topSpacing(),
                    onOpenSettings = viewModel::openSettings,
                    editing = widgetsEditing,
                    onEditingChange = { widgetsEditing = it },
                )
                2 -> com.usos.launcher.ui.feeds.FeedsPage(feedsVm, topSpacing(), onManage = { feedManager = true }, onOpenSettings = viewModel::openSettings)
                else -> HomePage(
                    state = state,
                    notice = notice,
                    leisure = leisure,
                    blockerActive = blockerActive,
                    searchActive = searchOpen || searchProgress.value > 0f,
                    onToggle = viewModel::toggleSection,
                    onOpenApp = viewModel::openApp,
                    onOpenSearch = viewModel::openSearch,
                    gesturesOn = route == Route.Home && !searchOpen,
                    editing = editing,
                    orderLists = orderLists,
                    edit = edit,
                    editActions = remember(viewModel) {
                        EditActions(
                            onExit = viewModel::exitEdit,
                            onPlace = viewModel::placeTile,
                            onToSection = { k, sec -> viewModel.setSection(k, sec) },
                            onRemove = viewModel::removeFromMosaic,
                            onResize = viewModel::resizeTile,
                            onResizeInfo = viewModel::resizeInfo,
                            onRows = viewModel::changeRows,
                            onDetail = viewModel::openDetailByKey,
                        )
                    },
                    onEnterEdit = viewModel::enterEdit,
                    onThaw = viewModel::startThaw,
                    onCancelThaw = viewModel::cancelThaw,
                    onFreeze = viewModel::freezeLeisure,
                    onOpenInfo = viewModel::openInfo,
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
            Route.Settings -> {
                val budget by viewModel.budget.collectAsStateWithLifecycle()
                val wait by viewModel.wait.collectAsStateWithLifecycle()
                val orderLists by viewModel.orderLists.collectAsStateWithLifecycle()
                val suggestionsOn by viewModel.suggestionsOn.collectAsStateWithLifecycle()
                val perHour by viewModel.feedsPerHour.collectAsStateWithLifecycle()
                val calendarGranted by viewModel.calendarGranted.collectAsStateWithLifecycle()
                val provider by viewModel.suggestProvider.collectAsStateWithLifecycle()
                val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
                val recentColors by viewModel.recentColors.collectAsStateWithLifecycle()
                val clock by viewModel.clockPrefs.collectAsStateWithLifecycle()
                val weatherPrefs by viewModel.weatherPrefs.collectAsStateWithLifecycle()
                val cities by viewModel.cityResults.collectAsStateWithLifecycle()
                val mosaicPrefs by viewModel.mosaicPrefs.collectAsStateWithLifecycle()
                val infoSpecs by viewModel.infoSpecs.collectAsStateWithLifecycle()
                val searchKeyboard by viewModel.searchKeyboard.collectAsStateWithLifecycle()
                val thumbsGray by viewModel.thumbsGray.collectAsStateWithLifecycle()
                val feedsKeep by viewModel.feedsKeep.collectAsStateWithLifecycle()
                val clockTap by viewModel.clockTap.collectAsStateWithLifecycle()
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val versionName = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
                val allApps = remember(orderLists) { orderLists.flatMap { (_, l) -> l.map { it.app } }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
                SettingsScreen(
                    com.usos.launcher.ui.settings.SettingsUi(
                        budget, wait, orderLists, suggestionsOn, provider, grayscale, perHour, calendarGranted,
                        clock, weatherPrefs, cities, iconPrefs, mosaicPrefs, infoSpecs, searchEngine, recentColors,
                        searchKeyboard, assistantUi, feedsKeep, clockTap, allApps, versionName, thumbsGray,
                    ),
                    SettingsActions(
                        onBudget = viewModel::setBudget,
                        onWait = viewModel::setWait,
                        onAccent = viewModel::setAccent,
                        onCustomAccent = viewModel::setCustomAccent,
                        onSearchEngine = viewModel::setSearchEngine,
                        onSuggestionsOn = viewModel::setSuggestionsOn,
                        onProvider = viewModel::setSuggestProvider,
                        onGrayscale = viewModel::setWidgetsGrayscale,
                        onFeedsPerHour = viewModel::setFeedsPerHour,
                        onCalendarPermission = viewModel::requestCalendar,
                        onMove = viewModel::moveInSection,
                        onRestartOnboarding = viewModel::restartOnboarding,
                        onClockType = viewModel::setClockType,
                        onClockSeconds = viewModel::setClockSeconds,
                        onClockAnimated = viewModel::setClockAnimated,
                        onWeatherOn = viewModel::setWeatherOn,
                        onWeatherFahrenheit = viewModel::setWeatherFahrenheit,
                        onWeatherIcon = viewModel::setWeatherIcon,
                        onWeatherText = viewModel::setWeatherText,
                        onWeatherMinMax = viewModel::setWeatherMinMax,
                        onWeatherAuto = { if (it) viewModel.setWeatherAuto() else viewModel.setWeatherManual() },
                        onSearchCity = viewModel::searchCities,
                        onPickCity = viewModel::pickCity,
                        onWeatherBelow = viewModel::setWeatherBelowClock,
                        onWeatherAccent = viewModel::setWeatherAccent,
                        onBlackWallpaper = viewModel::applyBlackWallpaper,
                        onIconMode = viewModel::setIconMode,
                        onIconShowCode = viewModel::setIconShowCode,
                        onColumns = viewModel::setColumns,
                        onRows = viewModel::setRows,
                        onInfoSize = viewModel::setInfoSize,
                        onInfoHidden = viewModel::setInfoHidden,
                        onEditMosaic = viewModel::startHomeEdit,
                        onSearchKeyboard = viewModel::setSearchKeyboard,
                        onAssistant = viewModel::setAssistantChoice,
                        onFeedsKeep = viewModel::setFeedsKeep,
                        onClockTap = viewModel::setClockTap,
                        onThumbsGray = viewModel::setThumbsGray,
                        onBack = viewModel::closeOverlay,
                    ),
                )
            }
            Route.Onboarding -> OnboardingScreen(onFinish = viewModel::finishOnboarding)
            Route.Home -> Unit
        }
        if (feedManager) {
            com.usos.launcher.ui.feeds.FeedManagerScreen(feedsVm, onBack = { feedManager = false })
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
private fun HomePage(
    state: HomeUiState?,
    notice: String?,
    leisure: LeisureStatus?,
    searchActive: Boolean,
    onToggle: (Section) -> Unit,
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
) {
    val now by rememberNow()
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
            ClockBlock(now, clock, weatherPrefs, weather, onWeather = onOpenWeather, onClock = onOpenClock)
            Spacer(Modifier.height(16.dp))
            WeekStrip(now.toLocalDate())
            Spacer(Modifier.height(12.dp))

            Column(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { listCoords = it; edit.viewport = it }.verticalScroll(scroll)) {
                if (state != null) {
                    state.sections.forEach { s ->
                        SectionBlock(
                            s, isOpen = state.open == s.section, leisure = leisure, blockerActive = blockerActive,
                            editing = editing, edit = edit, editActions = editActions,
                            onAdd = { cell -> adding = s.section to cell },
                            onToggle = { sec -> edit.clearSelection(); onToggle(sec) }, onOpenApp = onOpenApp, onEnterEdit = onEnterEdit,
                            onThaw = onThaw, onCancelThaw = onCancelThaw, onFreeze = onFreeze, onOpenInfo = onOpenInfo,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
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
            val own = orderLists.firstOrNull { it.first == section }?.second.orEmpty().filter { it.config.key !in keysShown }
            val others = orderLists.filter { it.first != section }
                .flatMap { (sec, list) -> list.map { sec to it } }
                .sortedBy { it.second.app.label.lowercase() }
            AddPicker(
                section, own, others,
                onPick = { editActions.onPlace(it.config.key, section, cell); adding = null },
                onClose = { adding = null },
            )
        }
    }
}

@Composable
private fun SectionBlock(
    s: SectionUi,
    isOpen: Boolean,
    leisure: LeisureStatus?,
    blockerActive: Boolean,
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
    val frozen = isLeisure && leisure?.isOpen != true
    val nameColor = if (edit.hoverSection == s.section) accent else if (frozen) UsosColors.LeisureFrozen else UsosColors.Text
    val data: String = when {
        !isLeisure -> if (isOpen) "—" else s.count.toString()
        leisure is LeisureStatus.Open -> stringResource(R.string.home_minutes, leisure.minutesLeft)
        leisure is LeisureStatus.Thawing -> stringResource(R.string.home_seconds, leisure.secondsLeft)
        else -> stringResource(R.string.home_frozen)
    }
    Row(
        Modifier.fillMaxWidth().height(SectionRowHeight)
            .onGloballyPositioned { edit.headers[s.section] = it }
            .tap { onToggle(s.section) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(stringResource(s.section.titleRes), UsosType.SectionName, nameColor)
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
                    UsosText(stringResource(R.string.home_blocker_inactive), UsosType.Label, accent)
                }
            }
            if (isLeisure && leisure !is LeisureStatus.Open) {
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

/**
 * Rejilla de 3 o 4 columnas; ancho de celda = (ancho útil - 11 dp x (columnas - 1)) / columnas. Las piezas ya
 * vienen colocadas por el empaquetado denso (SectionMosaic / MosaicPacker, puros). En edición se dibuja la
 * rejilla completa (celdas fantasma con «+»), cada pieza con borde discontinuo y, mientras se arrastra o se
 * redimensiona, la colocación en vivo (`EditState.preview`) con las demás piezas animadas a su sitio.
 */
@Composable
private fun Mosaic(
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
                                AppTile(tile!!, Modifier.fillMaxSize(), !editing, onOpenApp, onEnterEdit)
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

/**
 * Capa de edición sobre una pieza. Sin seleccionar: toque o pulsación larga la selecciona (arrastrar desplaza la
 * lista). Seleccionada: arrastrarla la mueve (salvo la de información y mientras hay tiradores).
 */
@Composable
private fun PieceEditLayer(
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
                if (canDrag) Modifier.pointerInput(key, section) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val moved = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                        val c = coords ?: return@awaitEachGesture
                        val t = tileNow ?: return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        edit.start(key, section, t, c.positionInRoot(), down.position, c.size)
                        edit.pointer = c.positionInRoot() + moved.position
                        edit.updateHover()
                        val finished = drag(moved.id) { change ->
                            change.consume()
                            coords?.let { edit.pointer = it.positionInRoot() + change.position }
                            edit.updateHover()
                        }
                        if (finished) edit.drop(actionsNow) else edit.cancel()
                    }
                } else if (selected) Modifier else Modifier.pressGestures(onLongPress = select) { select() },
            ),
    )
}

/**
 * Capa superior de la edición de inicio: bolitas alrededor de la pieza seleccionada o, tras REDIMENSIONAR, sus
 * tiradores; y un toque fuera de la pieza deselecciona. Lee la posición viva de la pieza (scroll incluido).
 */
@Composable
private fun EditOverlay(edit: EditState, actions: EditActions, pageCoords: LayoutCoordinates?) {
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

/** Pieza de información: fondo de acento, etiqueta mono arriba y dato 15 sp 700 abajo. */
@Composable
private fun InfoTile(
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
private fun FreezeTile(minutesLeft: Int, modifier: Modifier, onFreeze: () -> Unit, onEnterEdit: () -> Unit) {
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

@Composable
private fun AppTile(
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
    Column(
        modifier
            .alpha(if (tile.dimmed) 0.35f else 1f)
            .background(fill, shape)
            .border(1.dp, edge, shape)
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
}

/** Barra fija de búsqueda. Al tocarla sube y se convierte en campo (SearchOverlay). */
@Composable
private fun SearchBar(notice: String?, hidden: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(UsosDimens.RadiusSearch)
    Box(
        modifier
            .height(52.dp)
            .alpha(if (hidden) 0f else 1f)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .tap(enabled = !hidden) { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        UsosText(notice ?: stringResource(R.string.search_hint), UsosType.SearchHint, UsosColors.TextSecondary)
    }
}

/** Hueco entre la barra BUSCAR y el botón de IA. */
internal val AiGap = 8.dp
internal val AiButtonSize = 52.dp

/** Botón redondo de IA a la derecha de la barra: glifo de puntos propio o, con iconos activados, el icono de la app del asistente. */
@Composable
private fun AiButton(assistant: HomeViewModel.AssistantUi, hidden: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier
            .size(AiButtonSize)
            .alpha(if (hidden) 0f else 1f)
            .background(UsosColors.Surface, CircleShape)
            .border(1.dp, UsosColors.Border, CircleShape)
            .tap(enabled = !hidden, onLongClick = onLongClick) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val entry = assistant.entry
        val mode = com.usos.launcher.ui.theme.LocalIconPrefs.current.mode
        if (entry != null && mode != com.usos.launcher.data.sections.IconMode.NONE) {
            com.usos.launcher.ui.theme.AppIconOrNull(entry, 28.dp, UsosColors.Text) { AiGlyph() }
        } else {
            AiGlyph()
        }
    }
}

/** Chispa de cuatro puntas hecha con puntos (rejilla 5x5), sin logos de marca. */
@Composable
fun AiGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val step = size.width / 5f
        val r = step * 0.28f
        val on = listOf(2 to 0, 2 to 1, 0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 2 to 3, 2 to 4)
        on.forEach { (cx, cy) ->
            drawCircle(UsosColors.Text, r * if (cx == 2 && cy == 2) 1.25f else 1f, androidx.compose.ui.geometry.Offset(step * (cx + 0.5f), step * (cy + 0.5f)))
        }
    }
}

/** Selector rápido del asistente (pulsación larga en el botón de IA). */
@Composable
private fun AssistantPicker(assistant: HomeViewModel.AssistantUi, onPick: (String) -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier.fillMaxSize().background(UsosColors.Background.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = UsosDimens.ScreenMargin).padding(bottom = 12.dp + navBar)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UsosText(stringResource(R.string.search_assistant), UsosType.Label, UsosColors.TextSecondary)
            val current = assistant.resolved
            assistant.installed.forEach { a ->
                val sel = current != null && !current.web && current.assistant == a
                Box(
                    Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp)
                        .background(if (sel) LocalAccent.current else UsosColors.Surface, shape)
                        .border(1.dp, UsosColors.Border, shape)
                        .tap { onPick(com.usos.launcher.data.ai.Assistants.appChoice(a)) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { UsosText(a.label, UsosType.Button, if (sel) LocalOnAccent.current else UsosColors.Text) }
            }
            Box(
                Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp)
                    .background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
                    .tap { onPick(com.usos.launcher.data.ai.Assistants.NONE) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) { UsosText(stringResource(R.string.none), UsosType.Button, UsosColors.TextSecondary) }
        }
    }
}
