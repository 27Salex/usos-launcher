package com.usos.launcher.ui.home

import androidx.annotation.StringRes
import com.usos.launcher.R
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.usos.launcher.AppContainer
import com.usos.launcher.UsosApp
import android.os.SystemClock
import com.usos.launcher.data.sections.AppConfig
import com.usos.launcher.data.tiles.toAppEntry
import com.usos.launcher.data.info.InfoKind
import com.usos.launcher.data.info.InfoPiece
import com.usos.launcher.data.info.InfoPieces
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.ZoneId
import com.usos.launcher.data.sections.Highlight
import com.usos.launcher.data.sections.PlacedApp
import com.usos.launcher.data.sections.SectionOrder
import com.usos.launcher.data.usage.UsageSummary
import com.usos.launcher.data.suggest.SuggestFlow
import com.usos.launcher.data.suggest.SuggestProvider
import com.usos.launcher.ui.widgets.HostedWidget
import com.usos.launcher.data.sections.ClockPrefs
import com.usos.launcher.data.sections.ClockType
import com.usos.launcher.data.sections.WeatherPrefs
import com.usos.launcher.data.weather.GeoPlace
import com.usos.launcher.data.weather.WeatherNow
import com.usos.launcher.data.feeds.DefaultFeeds
import com.usos.launcher.ui.widgets.WidgetSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.usos.launcher.service.LauncherAlert
import com.usos.launcher.service.isBlockerEnabled
import com.usos.launcher.ui.appdetail.DetailUi
import com.usos.launcher.data.sections.DEFAULT_ACCENT
import com.usos.launcher.data.leisure.LeisureStatus
import com.usos.launcher.data.sections.SectionLayout
import com.usos.launcher.data.ai.AskPlan
import com.usos.launcher.data.ai.Assistant
import com.usos.launcher.data.ai.Assistants
import com.usos.launcher.data.ai.Resolved
import com.usos.launcher.data.feeds.FeedLimits
import com.usos.launcher.data.sections.IconMode
import com.usos.launcher.data.sections.IconPrefs
import com.usos.launcher.data.sections.MosaicEntry
import com.usos.launcher.data.sections.MosaicPacker
import com.usos.launcher.data.sections.MosaicPrefs
import com.usos.launcher.data.sections.Placement
import com.usos.launcher.data.sections.SectionMosaic
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.data.sections.entry
import com.usos.launcher.data.sections.Cell
import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.search.SearchFilter
import com.usos.launcher.ui.search.SearchItem
import com.usos.launcher.ui.search.SearchClassifier
import com.usos.launcher.ui.search.SearchIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TileUi(
    val app: AppEntry,
    val code: String,
    val dimmed: Boolean,
    val highlight: Highlight = Highlight.NORMAL,
    val highlightColor: Int? = null,
    val usageMin: Int? = null,
)

/** Mosaico ya colocado: [placements] (clave de app o [SectionMosaic.INFO_KEY]), [freeze] = hueco de CONGELAR. */
data class SectionUi(
    val section: Section,
    val count: Int,
    val tiles: Map<String, TileUi>,
    val info: InfoPiece? = null,
    val columns: Int = 3,
    val placements: List<Placement> = emptyList(),
    val freeze: Cell? = null,
    val rowsUsed: Int = 0,
    val rows: Int = 2,
    /** Piezas de mosaico en orden (como las empaqueta la vista) y tamaño de la pieza de info visible: base de la vista previa. */
    val entries: List<MosaicEntry> = emptyList(),
    val infoSize: TileSize? = null,
)

/** Pieza de información de una sección: tamaño, si está oculta y si hay algo que mostrar. */
data class InfoSpec(val kind: InfoKind, val size: TileSize, val hidden: Boolean, val available: Boolean) {
    val visibleSize: TileSize? get() = if (available && !hidden) size else null
}

/** [workPaused] = el perfil de trabajo está en pausa: sus apps se ocultan y Trabajo ofrece activarlo. */
data class HomeUiState(
    val sections: List<SectionUi>, val open: Section?, val workPaused: Boolean = false,
    /** Piezas propias (destino, contactos) por clave de configuración. */
    val customTiles: Map<String, com.usos.launcher.data.tiles.CustomTile> = emptyMap(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.appRepository
    private val configStore = container.appConfigStore
    private val settings = container.settingsStore
    private val leisureRepo = container.leisureRepository

    /** Estado del ocio; null hasta que se lee de DataStore (se trata como congelado). */
    val leisure: StateFlow<LeisureStatus?> = leisureRepo.status

    private val configs: StateFlow<Map<String, com.usos.launcher.data.sections.AppConfig>> =
        configStore.configs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val widgetHost get() = container.widgetHost
    val widgetManager get() = container.widgetManager

    val suggestionsOn: StateFlow<Boolean> = settings.suggestionsOn
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val searchEngine: StateFlow<com.usos.launcher.data.suggest.SearchEngine> = settings.searchEngine
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.usos.launcher.data.suggest.SearchEngine.GOOGLE)
    val recentColors: StateFlow<List<Int>> = settings.recentColors
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val suggestProvider: StateFlow<SuggestProvider> = settings.suggestProvider
        .stateIn(viewModelScope, SharingStarted.Eagerly, SuggestProvider.GOOGLE)

    /** Sugerencias web. Con el ajuste apagado el flujo nunca llega al cliente de red. */
    val suggestions: StateFlow<List<String>> = SuggestFlow
        .build(_query, suggestionsOn, suggestProvider, container.suggestClient)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val widgets: StateFlow<List<HostedWidget>> = container.widgetStore.widgets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val widgetsGrayscale: StateFlow<Boolean> = settings.widgetsGrayscale
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val fontChoices: StateFlow<Pair<com.usos.launcher.ui.theme.FontChoice, com.usos.launcher.ui.theme.FontChoice>> =
        kotlinx.coroutines.flow.combine(settings.fontTitle, settings.fontText) { t, x ->
            com.usos.launcher.ui.theme.FontChoice.fromName(t) to com.usos.launcher.ui.theme.FontChoice.fromName(x)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, com.usos.launcher.ui.theme.FontChoice.DEFAULT to com.usos.launcher.ui.theme.FontChoice.DEFAULT)
    val background: StateFlow<com.usos.launcher.data.sections.BackgroundPrefs> = settings.background
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.usos.launcher.data.sections.BackgroundPrefs())
    private val backgroundPicks = Channel<Unit>(Channel.BUFFERED)
    val backgroundPickEvents = backgroundPicks.receiveAsFlow()

    fun setFontTitle(c: com.usos.launcher.ui.theme.FontChoice) { viewModelScope.launch { settings.setFontTitle(c.name) } }
    fun setFontText(c: com.usos.launcher.ui.theme.FontChoice) { viewModelScope.launch { settings.setFontText(c.name) } }
    fun setBackgroundMode(m: com.usos.launcher.data.sections.BackgroundMode) { viewModelScope.launch { settings.setBackgroundMode(m) } }
    fun setBackgroundColor(argb: Int) { viewModelScope.launch { settings.setBackgroundColor(argb); settings.addRecentColor(argb) } }
    fun setBackgroundDim(pct: Int) { viewModelScope.launch { settings.setBackgroundDim(pct) } }
    fun setBackgroundBlur(on: Boolean) { viewModelScope.launch { settings.setBackgroundBlur(on) } }
    fun pickBackgroundImage() { viewModelScope.launch { backgroundPicks.send(Unit) } }
    fun importBackground(uri: android.net.Uri?) {
        uri ?: return
        viewModelScope.launch {
            if (com.usos.launcher.data.background.BackgroundImage.import(container.appContext, uri)) settings.bumpBackgroundImage()
            else showNotice(R.string.notice_background_failed)
        }
    }

    val clockPrefs: StateFlow<ClockPrefs> = settings.clockPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, ClockPrefs())
    val weatherPrefs: StateFlow<WeatherPrefs> = settings.weatherPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, WeatherPrefs())
    private val weatherRepo = container.weatherRepository
    val weather: StateFlow<WeatherNow?> = weatherRepo.cached.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _cityResults = MutableStateFlow<List<GeoPlace>>(emptyList())
    val cityResults: StateFlow<List<GeoPlace>> = _cityResults
    private val locationRequests = Channel<Unit>(Channel.BUFFERED)
    val locationRequestEvents = locationRequests.receiveAsFlow()

    private val _route = MutableStateFlow<Route>(Route.Home)
    val route: StateFlow<Route> = _route

    private class DetailExtras(val key: String, val hasPermission: Boolean, val usage: UsageSummary?, val storage: Long?)
    private val detailExtras = MutableStateFlow<DetailExtras?>(null)
    private val todayUsage = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val agenda = MutableStateFlow<InfoPiece?>(null)
    private val _calendarGranted = MutableStateFlow(container.agendaRepository.hasPermission())
    val calendarGranted: StateFlow<Boolean> = _calendarGranted

    /** Peticiones de permiso de calendario para la actividad (buffer: no se pierden antes de colectar). */
    private val calendarRequests = Channel<Boolean>(Channel.BUFFERED)
    val calendarRequestEvents = calendarRequests.receiveAsFlow()
    val feedsPerHour: StateFlow<Int> = settings.feedsPerHour.stateIn(viewModelScope, SharingStarted.Eagerly, 5)
    private val usageRepo = container.usageRepository

    private val _blockerActive = MutableStateFlow(true)
    val blockerActive: StateFlow<Boolean> = _blockerActive
    private var blockedAt = -10_000L

    val budget: StateFlow<Int> = leisureRepo.budget
    val wait: StateFlow<Int> = leisureRepo.wait

    /** Apps instaladas reales (sin piezas propias). */
    private val realApps: StateFlow<List<AppEntry>?> = repository.apps
        .map<List<AppEntry>, List<AppEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val customTiles: StateFlow<List<com.usos.launcher.data.tiles.CustomTile>> = container.customTileStore.tiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Apps + piezas propias como «apps virtuales» (paquete `usos.tile.<id>`, serial -1) que ya tienen
     * configuración: así mosaico, edición, búsqueda y orden las tratan igual que a una app.
     */
    private val apps: StateFlow<List<AppEntry>?> = combine(realApps, customTiles, container.appConfigStore.configs) { a, t, c ->
        a?.plus(t.map { it.toAppEntry() }.filter { it.key in c })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null mientras se lee de DataStore; luego la sección abierta o [NoSection]. */
    private val open = MutableStateFlow<Section?>(null)
    private val openLoaded = MutableStateFlow(false)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    init {
        viewModelScope.launch {
            container.updateManager.announce.collect { v -> showNotice(R.string.notice_update_available, v) }
        }
    }

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeEvents: SharedFlow<Unit> = _homeEvents.asSharedFlow()

    val accent: StateFlow<Color> = settings.accent
        .map { Color(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Color(DEFAULT_ACCENT))

    val mosaicPrefs: StateFlow<MosaicPrefs> = container.displayStore.mosaicPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, MosaicPrefs())
    val iconPrefs: StateFlow<IconPrefs> = container.displayStore.iconPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, IconPrefs())
    val iconRepository get() = container.iconRepository

    /** Piezas de información por sección: Agenda (si hay permiso y evento) y Mapas (si está instalado). */
    private val infoPieces: StateFlow<Map<Section, InfoPiece>> = combine(agenda, apps) { ag, a ->
        val mapsInstalled = a.orEmpty().any { it.packageName == InfoPieces.MAPS_PACKAGE && it.isMainProfile }
        InfoPieces.bySection(listOf(ag, InfoPieces.mapsPiece(mapsInstalled)))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Tamaño, ocultación y disponibilidad de cada pieza de información (para el mosaico y el editor). */
    val infoSpecs: StateFlow<Map<Section, InfoSpec>> = combine(infoPieces, apps, mosaicPrefs) { pieces, a, prefs ->
        val mapsInstalled = a.orEmpty().any { it.packageName == InfoPieces.MAPS_PACKAGE && it.isMainProfile }
        InfoKind.entries.associate { kind ->
            val section = InfoPieces.sectionOf(kind)
            section to InfoSpec(
                kind, prefs.infoSizeFor(kind), kind in prefs.infoHidden,
                available = if (kind == InfoKind.MAPS) mapsInstalled else pieces[section] != null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private class Extras(
        val usage: Map<String, Int>, val info: Map<Section, InfoPiece>,
        val prefs: MosaicPrefs, val specs: Map<Section, InfoSpec>,
    )

    /** null hasta que apps, configuración y sección abierta están listas. */
    val state: StateFlow<HomeUiState?> = combine(
        apps, configStore.configs, open, openLoaded,
        combine(todayUsage, infoPieces, mosaicPrefs, infoSpecs) { u, i, p, s -> Extras(u, i, p, s) },
    ) { a, c, o, loaded, extras ->
        val usage = extras.usage
        if (a == null || !loaded || a.any { it.key !in c }) return@combine null
        val grouped = SectionLayout.build(a, c)
        val workPaused = a.any { !it.isMainProfile && it.isQuiet }
        val visible = extras.prefs.visibleSections
        HomeUiState(
            sections = visible.map { section ->
                // Con el perfil de trabajo en pausa sus apps no se ven en ninguna sección.
                val placed = grouped[section].orEmpty().filter { !it.app.isQuiet }
                val infoSize = extras.specs[section]?.visibleSize
                val info = if (infoSize != null) extras.info[section] ?: mapsFallback(section) else null
                // La pieza de Mapas sustituye a la app de Mapas en el mosaico de Moverse.
                val forMosaic = if (info?.kind == InfoKind.MAPS) placed.filter { it.app.packageName != InfoPieces.MAPS_PACKAGE } else placed
                val packed = SectionMosaic.layout(
                    extras.prefs.columns, extras.prefs.rowsFor(section), if (info != null) infoSize else null,
                    forMosaic.map { it.entry() }, reserveFreeze = SectionMosaic.reservesFreeze(section),
                )
                SectionUi(
                    section = section,
                    count = placed.size,
                    info = info,
                    tiles = forMosaic.associate {
                        it.config.key to TileUi(
                            it.app, it.code, it.app.isQuiet, it.config.highlight, it.config.highlightColor,
                            usageMin = if (it.config.showUsage) usage[it.app.packageName] ?: 0 else null,
                        )
                    },
                    columns = extras.prefs.columns,
                    placements = packed.placements,
                    freeze = packed.reserved,
                    rowsUsed = packed.rowsUsed,
                    rows = extras.prefs.rowsFor(section),
                    entries = forMosaic.map { it.entry() },
                    infoSize = if (info != null) infoSize else null,
                )
            },
            // Acordeón (por defecto): como siempre. Pestañas (opcional): siempre hay una abierta.
            open = if (extras.prefs.tabs) o?.takeIf { it in visible } ?: visible.first() else o,
            workPaused = workPaused,
            customTiles = customTiles.value.associateBy { com.usos.launcher.data.tiles.CustomTiles.configKey(it.id) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun resumeWorkProfile() {
        if (!repository.resumeWorkProfile()) showNotice(R.string.notice_work_resume_failed)
    }

    private fun mapsFallback(section: Section): InfoPiece? =
        if (section == Section.MOVE) InfoPieces.mapsPiece(true) else null

    /** Todas las apps para la busqueda; las de ocio llevan `frozen` mientras el ocio no este abierto. */
    val searchItems: StateFlow<List<SearchItem>> = combine(
        apps, configs,
        combine(leisure.map { it?.isOpen == true }, mosaicPrefs) { o, p -> o || Section.LEISURE in p.hiddenSections }.distinctUntilChanged(),
    ) { a, c, leisureOpen ->
        a.orEmpty().mapNotNull { app ->
            if (app.isQuiet) return@mapNotNull null
            val cfg = c[app.key] ?: return@mapNotNull null
            val code = cfg.code?.takeIf { it.isNotBlank() } ?: app.defaultCode
            SearchItem(app, code, cfg.section, frozen = cfg.section == Section.LEISURE && !leisureOpen)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Apps de cada seccion, en su orden, para la pantalla de ajustes. */
    val orderLists: StateFlow<List<Pair<Section, List<PlacedApp>>>> = combine(apps, configs) { a, c ->
        val grouped = SectionLayout.build(a.orEmpty(), c)
        (Section.home + Section.NONE).map { it to grouped[it].orEmpty() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val detail: StateFlow<DetailUi?> = combine(
        _route, apps, configs, detailExtras, combine(mosaicPrefs, infoSpecs) { p, s -> p to s },
    ) { r, a, c, extras, ps ->
        val key = (r as? Route.Detail)?.key ?: return@combine null
        val app = a?.firstOrNull { it.key == key } ?: return@combine null
        val cfg = c[key] ?: return@combine null
        val placed = SectionLayout.build(a, c)[cfg.section].orEmpty()
        val ex = extras?.takeIf { it.key == key }
        DetailUi(
            app = app, config = cfg, code = cfg.code?.takeIf { it.isNotBlank() } ?: app.defaultCode,
            usage = ex?.usage, hasPermission = ex?.hasPermission ?: usageRepo.hasPermission(),
            storageBytes = ex?.storage,
            mosaicFull = cfg.section != Section.NONE && !SectionMosaic.fits(
                ps.first.columns, ps.first.rowsFor(cfg.section), ps.second[cfg.section]?.visibleSize,
                placed.map { it.entry() }, key, SectionMosaic.reservesFreeze(cfg.section),
            ),
            rows = ps.first.rowsFor(cfg.section),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var noticeJob: Job? = null

    init {
        viewModelScope.launch { leisureRepo.load() }
        viewModelScope.launch {
            open.value = settings.lastOpenSection.first()
            openLoaded.value = true
        }
        viewModelScope.launch {
            realApps.collect { list -> if (list != null) configStore.reconcile(list) }
        }
        viewModelScope.launch {
            if (!settings.onboardingDone.first()) _route.value = Route.Onboarding
        }
        viewModelScope.launch {
            container.pendingAlert.collect { alert ->
                if (alert == null) return@collect
                container.pendingAlert.value = null
                if (SystemClock.elapsedRealtime() - alert.atMs < 10_000) onAlert(alert)
            }
        }
        viewModelScope.launch { cleanOrphanWidgets() }
        viewModelScope.launch { seedDefaultFeeds() }
        viewModelScope.launch {
            // Primer arranque: el diálogo de calendario se pide una sola vez, cuando ya no hay onboarding.
            if (settings.onboardingDone.first()) askCalendarOnce()
        }
        refreshInfo()
        refreshBlocker()
    }

    /** Primer arranque: si no hay feeds, crea los de por defecto una sola vez y los descarga. */
    private suspend fun seedDefaultFeeds() {
        if (settings.feedsSeeded()) return
        settings.setFeedsSeeded()
        val repo = container.feedRepository
        if (repo.seedDefaults(DefaultFeeds.forLanguage(container.appContext.getString(R.string.feeds_language)))) runCatching { repo.refreshAll() }
    }

    /** Borra del host los ids que no estan guardados (alta interrumpida, datos perdidos). */
    private suspend fun cleanOrphanWidgets() {
        val saved = container.widgetStore.current()
        val ids = withContext(Dispatchers.Default) { container.widgetHost.appWidgetIds.toList() }
        WidgetSizing.orphans(ids, saved).forEach { container.widgetHost.deleteAppWidgetId(it) }
        container.pruneMissingWidgets()
    }

    fun addWidget(id: Int) {
        viewModelScope.launch { container.widgetStore.update { l -> if (l.any { it.id == id }) l else l + HostedWidget(id) } }
    }

    fun discardWidgetId(id: Int) { container.widgetHost.deleteAppWidgetId(id) }

    /** Persiste posición y tamaño de las cajas dadas (mover, redimensionar o migración). */
    fun applyWidgetLayout(boxes: List<com.usos.launcher.ui.widgets.GBox>) {
        viewModelScope.launch { container.widgetStore.update { WidgetSizing.withBoxes(it, boxes) } }
    }

    /** Alta de una pieza propia de Usos (id negativo propio, sin AppWidgetHost); la página le busca sitio. */
    fun addOwnWidget(kind: com.usos.launcher.ui.widgets.WidgetKind, cols: Int, rows: Int) {
        viewModelScope.launch {
            container.widgetStore.update { l ->
                l + HostedWidget(com.usos.launcher.ui.widgets.OwnWidgets.nextId(l.map { it.id }), cols = cols, rows = rows, kind = kind.name)
            }
        }
    }

    fun setWidgetConfig(id: Int, config: String) {
        viewModelScope.launch { container.widgetStore.update { l -> l.map { if (it.id == id) it.copy(config = config) else it } } }
    }

    fun removeWidget(id: Int) {
        if (id >= 0) container.widgetHost.deleteAppWidgetId(id)
        viewModelScope.launch { container.widgetStore.update { l -> l.filter { it.id != id } } }
    }

    fun setSuggestionsOn(on: Boolean) { viewModelScope.launch { settings.setSuggestionsOn(on) } }
    fun setSuggestProvider(p: SuggestProvider) { viewModelScope.launch { settings.setSuggestProvider(p) } }
    fun setSearchEngine(e: com.usos.launcher.data.suggest.SearchEngine) { viewModelScope.launch { settings.setSearchEngine(e) } }
    /** Acento con color libre: se guarda y entra en las muestras recientes. */
    fun setCustomAccent(argb: Int) { viewModelScope.launch { settings.setAccent(argb); settings.addRecentColor(argb) } }
    fun setHighlightColor(key: String, argb: Int) {
        updateConfig(key) { it.copy(highlight = Highlight.COLOR, highlightColor = argb) }
        viewModelScope.launch { settings.addRecentColor(argb) }
    }
    fun setWidgetsGrayscale(on: Boolean) { viewModelScope.launch { settings.setWidgetsGrayscale(on) } }

    /** Tocar una sugerencia lanza la busqueda web. */
    fun pickSuggestion(text: String) {
        closeSearch()
        if (!repository.webSearch(text, searchEngine.value)) showNotice(R.string.notice_search_failed)
    }

    private fun onAlert(alert: LauncherAlert) {
        blockedAt = SystemClock.elapsedRealtime()
        if (_route.value !is Route.Onboarding) _route.value = Route.Home
        exitEdit()
        if (alert.revealLeisure) revealLeisure() else closeSearch()
        showNotice(alert.textRes)
    }

    fun refreshBlocker() { _blockerActive.value = isBlockerEnabled(container.appContext) }

    /** Minutos de hoy para las piezas con uso y, si hay una ficha abierta, sus datos. */
    fun refreshUsage() {
        viewModelScope.launch {
            val perm = usageRepo.hasPermission()
            todayUsage.value = if (perm) runCatching { usageRepo.todayMinutes() }.getOrDefault(emptyMap()) else emptyMap()
            val key = (_route.value as? Route.Detail)?.key ?: return@launch
            val app = apps.value?.firstOrNull { it.key == key } ?: return@launch
            detailExtras.value = DetailExtras(
                key, perm,
                usage = if (perm) runCatching { usageRepo.summary(app.packageName) }.getOrNull() else null,
                storage = if (perm) usageRepo.storageBytes(app.packageName, app.userSerial) else null,
            )
        }
    }

    // ---- Navegacion entre pantallas ----

    fun openDetail(app: AppEntry) {
        closeSearch()
        detailExtras.value = null
        _route.value = Route.Detail(app.key)
        refreshUsage()
    }

    fun openDetailByKey(key: String) {
        if (com.usos.launcher.data.tiles.CustomTiles.isVirtual(key)) { editCustomTile(key); return }
        apps.value?.firstOrNull { it.key == key }?.let { openDetail(it) }
    }

    fun openSettings(category: String? = null) { closeSearch(); _route.value = Route.Settings(category) }
    val installedApps: StateFlow<List<AppEntry>?> get() = apps

    /** Atras desde ficha o ajustes. */
    fun closeOverlay() {
        _route.value = Route.Home
        detailExtras.value = null
    }

    /** Terminar o saltar el primer arranque. */
    fun finishOnboarding() {
        _route.value = Route.Home
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            askCalendarOnce()
        }
    }

    private suspend fun askCalendarOnce() {
        if (container.agendaRepository.hasPermission() || settings.calendarAsked.first()) return
        settings.setCalendarAsked(true)
        calendarRequests.send(false)
    }

    /** Desde Ajustes: vuelve a pedir el permiso de calendario. */
    fun requestCalendar() { viewModelScope.launch { calendarRequests.send(true) } }

    /** Recalcula la pieza de agenda (cada minuto, al volver y al conceder el permiso). */
    fun refreshInfo() {
        viewModelScope.launch {
            val repo = container.agendaRepository
            _calendarGranted.value = repo.hasPermission()
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val next = InfoPieces.nextEvent(repo.upcoming(now, zone), now, zone)
            agenda.value = InfoPieces.agendaPiece(next, now, zone)
        }
    }

    /** Toque en una pieza de información: abre la app relacionada. */
    fun openInfo(kind: InfoKind) {
        val ctx = container.appContext
        val ok = when (kind) {
            InfoKind.AGENDA -> InfoLauncher.openCalendar(ctx)
            InfoKind.MAPS -> InfoLauncher.navigateHome(ctx)
        }
        if (!ok) showNotice(R.string.notice_open_failed)
    }

    // ---- Reloj, tiempo, fondo ----

    fun setClockType(t: ClockType) { viewModelScope.launch { settings.setClockType(t) } }
    fun setClockSeconds(on: Boolean) { viewModelScope.launch { settings.setClockSeconds(on) } }
    fun setClockAnimated(on: Boolean) { viewModelScope.launch { settings.setClockAnimated(on) } }

    /** Activar el tiempo con ubicacion automatica pide primero el permiso; el resultado decide si queda activo. */
    fun setWeatherOn(on: Boolean) {
        viewModelScope.launch {
            if (on && weatherPrefs.value.auto && !weatherRepo.hasLocationPermission()) {
                locationRequests.send(Unit)
            } else {
                settings.setWeatherOn(on)
                if (on) weatherRepo.refreshIfStale()
            }
        }
    }

    fun onLocationResult(granted: Boolean) {
        viewModelScope.launch {
            settings.setWeatherOn(granted)
            if (granted) { weatherRepo.invalidate(); weatherRepo.refreshIfStale() }
        }
    }

    fun setWeatherAuto() {
        viewModelScope.launch {
            settings.setWeatherAuto(true)
            weatherRepo.invalidate()
            if (weatherPrefs.value.on && !weatherRepo.hasLocationPermission()) locationRequests.send(Unit)
            else weatherRepo.refreshIfStale()
        }
    }

    fun setWeatherManual() { viewModelScope.launch { settings.setWeatherAuto(false); weatherRepo.invalidate(); weatherRepo.refreshIfStale() } }
    fun setWeatherFahrenheit(on: Boolean) { viewModelScope.launch { settings.setWeatherFahrenheit(on) } }
    fun setWeatherIcon(on: Boolean) { viewModelScope.launch { settings.setWeatherIcon(on) } }
    fun setWeatherText(on: Boolean) { viewModelScope.launch { settings.setWeatherText(on) } }
    fun setWeatherMinMax(on: Boolean) { viewModelScope.launch { settings.setWeatherMinMax(on) } }
    fun setWeatherBelowClock(on: Boolean) { viewModelScope.launch { settings.setWeatherBelowClock(on) } }
    fun setWeatherAccent(on: Boolean) { viewModelScope.launch { settings.setWeatherAccent(on) } }

    fun refreshWeather() { viewModelScope.launch { weatherRepo.refreshIfStale() } }

    fun searchCities(query: String) {
        viewModelScope.launch { _cityResults.value = weatherRepo.searchCities(query) }
    }

    fun pickCity(p: GeoPlace) {
        _cityResults.value = emptyList()
        viewModelScope.launch {
            settings.setWeatherCity(p.label, p.lat, p.lon)
            weatherRepo.invalidate()
            weatherRepo.refreshIfStale()
        }
    }

    /** Toque en el tiempo: abre la app del tiempo del fabricante si existe; si no, busca «tiempo» en la web. */
    fun openWeatherApp() {
        val pm = container.appContext.packageManager
        val intent = WEATHER_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
        val opened = intent != null && runCatching {
            container.appContext.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) repository.webSearch(container.appContext.getString(R.string.weather_web_query), searchEngine.value)
    }

    // ---- Toque en el reloj ----

    val clockTap: StateFlow<String> = settings.clockTap.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    fun setClockTap(v: String) { viewModelScope.launch { settings.setClockTap(v) } }

    /** Toque en el reloj: según el ajuste, el reloj del sistema, una app elegida o nada. */
    fun openClock() {
        val v = clockTap.value
        when {
            v == "NONE" -> Unit
            v.startsWith("APP:") -> {
                val pkg = v.removePrefix("APP:")
                val entry = apps.value.orEmpty().firstOrNull { it.packageName == pkg && it.isMainProfile }
                if (entry != null) openApp(entry) else if (!repository.openSystemClock()) showNotice(R.string.notice_open_failed)
            }
            else -> if (!repository.openSystemClock()) showNotice(R.string.notice_open_failed)
        }
    }

    // ---- Asistente de IA ----

    class AssistantUi(
        val raw: String?,
        val resolved: Resolved?,
        val entry: AppEntry?,
        val installed: List<Assistant>,
    )

    val assistantUi: StateFlow<AssistantUi> = combine(settings.assistantChoice, apps) { raw, list ->
        val main = list.orEmpty().filter { it.isMainProfile }
        val pkgs = main.map { it.packageName }.toSet()
        val r = Assistants.resolve(raw, pkgs)
        AssistantUi(raw, r, r?.takeIf { !it.web }?.let { a -> main.firstOrNull { it.packageName == a.assistant.pkg } }, Assistants.installed(pkgs))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssistantUi(null, null, null, emptyList()))

    val notifMode: StateFlow<com.usos.launcher.data.notify.NotifMode> =
        settings.notifMode.stateIn(viewModelScope, SharingStarted.Eagerly, com.usos.launcher.data.notify.NotifMode.DOT)
    val pwaKeywords: StateFlow<Set<String>> = settings.pwaKeywords.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val searchKeyboard: StateFlow<Boolean> = settings.searchKeyboard.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setSearchKeyboard(on: Boolean) { viewModelScope.launch { settings.setSearchKeyboard(on) } }
    fun setAssistantChoice(v: String?) { viewModelScope.launch { settings.setAssistantChoice(v) } }

    /** Toque en el botón de IA: abre el asistente (la app, o su web en modo Web). */
    fun openAssistant() {
        val ui = assistantUi.value
        val r = ui.resolved ?: return
        val ok = if (r.web) repository.openUrl(r.assistant.webHome) else ui.entry?.let { repository.launch(it) } == true
        if (!ok) showNotice(R.string.notice_open_failed)
    }

    /** Fila «PREGUNTA A …» de la búsqueda: pasa el texto al asistente con el método de su ficha (CLAUDE.md). */
    fun askAssistant(text: String) {
        val ui = assistantUi.value
        val r = ui.resolved ?: return
        val q = text.trim()
        if (q.isEmpty()) return
        closeSearch()
        when (val plan = Assistants.askPlan(r, q)) {
            is AskPlan.View -> if (!repository.openUrl(plan.url, plan.pkg)) showNotice(R.string.notice_open_failed)
            AskPlan.CopyAndOpenApp -> {
                repository.copyText(q)
                if (ui.entry?.let { repository.launch(it) } == true) showNotice(R.string.notice_question_copied) else showNotice(R.string.notice_open_failed)
            }
            is AskPlan.CopyAndView -> {
                repository.copyText(q)
                if (repository.openUrl(plan.url)) showNotice(R.string.notice_question_copied) else showNotice(R.string.notice_open_failed)
            }
        }
    }

    // ---- Noticias ----

    val thumbsGray: StateFlow<Boolean> = settings.thumbsGray.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setThumbsGray(on: Boolean) { viewModelScope.launch { settings.setThumbsGray(on) } }
    val feedsKeep: StateFlow<Int> = settings.feedsKeep.stateIn(viewModelScope, SharingStarted.Eagerly, FeedLimits.DEFAULT_KEEP)
    fun setFeedsKeep(n: Int) {
        viewModelScope.launch {
            settings.setFeedsKeep(n)
            container.feedRepository.pruneAll()
        }
    }

    /** Pone un bitmap negro como fondo de inicio y de bloqueo. */
    fun applyBlackWallpaper() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = container.appContext
                    val wm = android.app.WallpaperManager.getInstance(ctx)
                    val dm = ctx.resources.displayMetrics
                    val w = wm.desiredMinimumWidth.takeIf { it > 0 } ?: dm.widthPixels
                    val h = wm.desiredMinimumHeight.takeIf { it > 0 } ?: dm.heightPixels
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.RGB_565)
                    bmp.eraseColor(android.graphics.Color.BLACK)
                    wm.setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
                    bmp.recycle()
                }.isSuccess
            }
            showNotice(if (ok) R.string.notice_wallpaper_ok else R.string.notice_wallpaper_failed)
        }
    }

    fun setFeedsPerHour(n: Int) { viewModelScope.launch { settings.setFeedsPerHour(n) } }

    fun restartOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone(false) }
        _route.value = Route.Onboarding
    }

    // ---- Ficha de app ----

    /** ¿Se vería [key] (activada) en el mosaico de [section]? Usa filas, columnas, pieza de info y CONGELAR reales. */
    private fun fitsIn(section: Section, entries: List<MosaicEntry>, key: String): Boolean {
        if (section == Section.NONE) return false
        val prefs = mosaicPrefs.value
        return SectionMosaic.fits(
            prefs.columns, prefs.rowsFor(section), infoSpecs.value[section]?.visibleSize, entries, key,
            SectionMosaic.reservesFreeze(section),
        )
    }

    private fun updateConfig(key: String, change: (AppConfig) -> AppConfig) {
        viewModelScope.launch {
            configStore.update { all -> all[key]?.let { all + (key to change(it)) } ?: all }
        }
    }

    fun setShowUsage(key: String, on: Boolean) {
        updateConfig(key) { it.copy(showUsage = on) }
        refreshUsage()
    }

    fun setHighlight(key: String, h: Highlight) = updateConfig(key) { it.copy(highlight = h) }

    /** Dos letras o digitos; vacio vuelve al codigo por defecto. */
    fun setCode(key: String, raw: String) {
        val code = raw.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { null }
        updateConfig(key) { it.copy(code = code) }
    }

    fun setSection(key: String, section: Section) {
        viewModelScope.launch {
            configStore.update { all ->
                val cfg = all[key] ?: return@update all
                if (cfg.section == section) return@update all
                val inSection = all.values.filter { it.section == section && it.key != key }
                val room = fitsIn(section, inSection.sortedBy { it.order }.map { MosaicEntry(it.key, it.size, it.inMosaic) } +
                    MosaicEntry(key, cfg.size, true), key)
                all + (key to cfg.copy(
                    section = section,
                    order = (inSection.maxOfOrNull { it.order } ?: -1) + 1,
                    inMosaic = room,
                    manual = true,
                ))
            }
        }
    }

    fun setInMosaic(key: String, on: Boolean) {
        viewModelScope.launch {
            var full = false
            configStore.update { all ->
                val cfg = all[key] ?: return@update all
                if (on) {
                    val entries = all.values.filter { it.section == cfg.section }.sortedBy { it.order }
                        .map { MosaicEntry(it.key, it.size, it.inMosaic) }
                    if (!fitsIn(cfg.section, entries, key)) {
                        full = true
                        return@update all
                    }
                }
                all + (key to cfg.copy(inMosaic = on, manual = true))
            }
            if (full) showNotice(R.string.notice_mosaic_full)
        }
    }

    fun setTileSize(key: String, size: TileSize) = updateConfig(key) { it.copy(size = size, manual = true) }

    // ---- Mosaico e iconos ----

    fun setColumns(n: Int) { viewModelScope.launch { container.displayStore.setColumns(n) } }
    fun setRows(section: Section, n: Int) { viewModelScope.launch { container.displayStore.setRows(section, n) } }
    fun setInfoSize(kind: InfoKind, size: TileSize) { viewModelScope.launch { container.displayStore.setInfoSize(kind, size) } }
    fun setInfoHidden(kind: InfoKind, hidden: Boolean) { viewModelScope.launch { container.displayStore.setInfoHidden(kind, hidden) } }
    val dockPrefs: StateFlow<com.usos.launcher.data.sections.DockPrefs> = container.displayStore.dockPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.usos.launcher.data.sections.DockPrefs())

    /** Apps de cada hueco del dock (null = vacío o desinstalada). */
    val dockApps: StateFlow<List<AppEntry?>> = combine(dockPrefs, apps) { d, a ->
        val byKey = a.orEmpty().associateBy { it.key }
        (0 until d.slots).map { i -> d.at(i)?.let { byKey[it] } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setDockOn(on: Boolean) { viewModelScope.launch { container.displayStore.setDockOn(on) } }
    fun setDockSlots(n: Int) { viewModelScope.launch { container.displayStore.setDockSlots(n) } }
    fun setDockApp(i: Int, key: String?) { viewModelScope.launch { container.displayStore.setDockApp(i, key) } }

    fun setIconMode(m: IconMode) { viewModelScope.launch { container.displayStore.setIconMode(m) } }
    fun setIconShowCode(on: Boolean) { viewModelScope.launch { container.displayStore.setIconShowCode(on) } }

    // ---- Edicion directa de inicio ----

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing

    fun enterEdit() { _editing.value = true; closeSearch() }
    fun exitEdit() { _editing.value = false }

    /** Desde Ajustes: vuelve a inicio y entra en edicion. */
    fun startHomeEdit() { closeOverlay(); enterEdit() }

    /** Piezas de mosaico de [section] como las coloca la vista (sin la app de Mapas si la pieza de Mapas la sustituye). */
    private fun mosaicEntries(section: Section, cfgs: Collection<AppConfig>): List<MosaicEntry> {
        val mapsInfo = infoSpecs.value[section]?.let { it.kind == InfoKind.MAPS && it.visibleSize != null } == true
        return cfgs.filter { it.section == section && !(mapsInfo && it.packageName == InfoPieces.MAPS_PACKAGE) }
            .sortedBy { it.order }.map { MosaicEntry(it.key, it.size, it.inMosaic) }
    }

    private fun layoutOf(section: Section, entries: List<MosaicEntry>, info: TileSize?, rows: Int = mosaicPrefs.value.rowsFor(section)) =
        SectionMosaic.layout(
            mosaicPrefs.value.columns, rows, info, entries, SectionMosaic.reservesFreeze(section),
        )

    /** Suelta [key] en [section]: la mueve ahi si hace falta, la mete en el mosaico y la ordena segun [target]. */
    fun placeTile(key: String, section: Section, target: Cell) {
        val ui = state.value?.sections?.firstOrNull { it.section == section } ?: return
        viewModelScope.launch {
            var full = false
            configStore.update { all ->
                val cfg = all[key] ?: return@update all
                val inSection = all.values.filter { it.section == section }.sortedBy { it.order }.map { it.key }
                val order = com.usos.launcher.data.sections.MosaicEdit.reorder(
                    inSection, ui.placements, key, target, ui.columns,
                )
                val updated = all.mapValues { (k, c) ->
                    val idx = order.indexOf(k)
                    when {
                        k == key -> c.copy(section = section, order = idx, inMosaic = true, manual = true)
                        idx >= 0 -> c.copy(order = idx)
                        else -> c
                    }
                }
                val info = infoSpecs.value[section]?.visibleSize
                if (key in layoutOf(section, mosaicEntries(section, updated.values), info).skipped) {
                    full = true
                    return@update all
                }
                updated
            }
            if (full) showNotice(R.string.notice_mosaic_full)
        }
    }

    fun removeFromMosaic(key: String) {
        if (com.usos.launcher.data.tiles.CustomTiles.isVirtual(key)) { deleteCustomTile(key); return }
        viewModelScope.launch {
            configStore.update { all -> all[key]?.let { all + (key to it.copy(inMosaic = false, manual = true)) } ?: all }
        }
    }

    /** Cambia el tamaño de una app; si ya no caben todas, se ocultan las de detrás (su inMosaic no cambia). */
    fun resizeTile(key: String, size: TileSize) = updateConfig(key) { if (it.size == size) it else it.copy(size = size, manual = true) }

    fun resizeInfo(kind: InfoKind, size: TileSize) = setInfoSize(kind, size)

    /** Suma o resta una fila al mosaico; las piezas que no caben se ocultan por orden sin tocar su inMosaic. */
    fun changeRows(section: Section, delta: Int) {
        val rows = mosaicPrefs.value.rowsFor(section)
        val next = (rows + delta).coerceIn(MosaicPacker.MIN_ROWS, MosaicPacker.MAX_ROWS)
        if (next != rows) setRows(section, next)
    }

    // ---- Ajustes ----

    fun setBudget(min: Int) { leisureRepo.setBudget(min) }

    fun setWait(seconds: Int) { leisureRepo.setWaitSeconds(seconds) }

    fun setAccent(argb: Int) { viewModelScope.launch { settings.setAccent(argb) } }

    /** Sube o baja [delta] posiciones una app dentro de su seccion. */
    fun moveInSection(section: Section, key: String, delta: Int) {
        val keys = orderLists.value.firstOrNull { it.first == section }?.second?.map { it.config.key } ?: return
        val orders = SectionOrder.move(keys, key, delta) ?: return
        viewModelScope.launch {
            configStore.update { all -> all.mapValues { (k, c) -> orders[k]?.let { c.copy(order = it) } ?: c } }
        }
    }

    /** Acordeón: abre o pliega. Con pestañas: elige la sección (no se pliega). */
    fun toggleSection(section: Section) {
        val next = if (!mosaicPrefs.value.tabs && open.value == section) null else section
        open.value = next
        viewModelScope.launch { settings.setLastOpenSection(next) }
    }

    fun setMusicPlayer(on: Boolean) { viewModelScope.launch { container.displayStore.setMusicPlayer(on) } }
    fun setHomeTabs(on: Boolean) { viewModelScope.launch { container.displayStore.setHomeTabs(on) } }
    fun setDrawerTabs(on: Boolean) { viewModelScope.launch { container.displayStore.setDrawerTabs(on) } }

    fun moveSectionTab(section: Section, delta: Int) {
        viewModelScope.launch {
            container.displayStore.setSectionOrder(
                com.usos.launcher.data.sections.SectionTabs.move(mosaicPrefs.value.sectionOrder, section, delta),
            )
        }
    }

    fun setSectionHidden(section: Section, hidden: Boolean) {
        viewModelScope.launch { container.displayStore.setSectionHidden(section, hidden) }
    }

    // ---- Piezas propias ----

    /** Editor abierto: pieza (nueva o existente) y, si es nueva, dónde colocarla. */
    data class TileEditing(val tile: com.usos.launcher.data.tiles.CustomTile, val isNew: Boolean, val section: Section?, val cell: Cell?)
    private val _tileEditing = MutableStateFlow<TileEditing?>(null)
    val tileEditing: StateFlow<TileEditing?> = _tileEditing

    fun newCustomTile(kind: com.usos.launcher.data.tiles.TileKind, section: Section, cell: Cell) {
        _tileEditing.value = TileEditing(
            com.usos.launcher.data.tiles.CustomTile(id = com.usos.launcher.data.tiles.CustomTiles.newId(), kind = kind),
            isNew = true, section = section, cell = cell,
        )
    }

    fun editCustomTile(key: String) {
        val t = customTiles.value.firstOrNull { com.usos.launcher.data.tiles.CustomTiles.configKey(it.id) == key } ?: return
        _tileEditing.value = TileEditing(t, isNew = false, section = null, cell = null)
    }

    fun closeTileEditor() { _tileEditing.value = null }

    fun saveCustomTile(tile: com.usos.launcher.data.tiles.CustomTile) {
        val ed = _tileEditing.value
        _tileEditing.value = null
        viewModelScope.launch {
            container.customTileStore.upsert(tile)
            syncChatShortcuts()
            val key = com.usos.launcher.data.tiles.CustomTiles.configKey(tile.id)
            if (ed?.isNew == true && ed.section != null) {
                configStore.update { all ->
                    if (key in all) all else all + (key to AppConfig(
                        packageName = com.usos.launcher.data.tiles.CustomTiles.PACKAGE_PREFIX + tile.id, userSerial = -1L,
                        section = ed.section, order = (all.values.filter { it.section == ed.section }.maxOfOrNull { it.order } ?: -1) + 1,
                        inMosaic = true, manual = true,
                        size = if (tile.kind == com.usos.launcher.data.tiles.TileKind.CONTACTS) TileSize.S2X1 else TileSize.S1X1,
                    ))
                }
                // Espera a que la pieza aparezca en el estado y la coloca en la celda elegida.
                state.first { s -> s?.sections?.any { sec -> key in sec.tiles } == true }
                ed.cell?.let { placeTile(key, ed.section, it) }
            }
        }
    }

    /** Fija los chats elegidos y borra fotos sin uso. */
    private suspend fun syncChatShortcuts() {
        val all = container.customTileStore.tiles.first()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.usos.launcher.data.tiles.ChatShortcuts.pinAll(container.appContext, all)
            com.usos.launcher.data.tiles.ChatShortcuts.prunePhotos(container.appContext, all)
        }
    }

    fun deleteCustomTile(key: String) {
        _tileEditing.value = null
        val id = com.usos.launcher.data.tiles.CustomTiles.idOf(key.substringBefore('/'))
        viewModelScope.launch {
            configStore.update { it - key }
            container.customTileStore.remove(id)
            syncChatShortcuts()
            container.displayStore.dockPrefs.first().apps.forEachIndexed { i, k -> if (k == key) container.displayStore.setDockApp(i, null) }
        }
    }

    fun openApp(app: AppEntry) {
        if (com.usos.launcher.data.tiles.CustomTiles.isVirtual(app.packageName)) {
            customTiles.value.firstOrNull { com.usos.launcher.data.tiles.CustomTiles.configKey(it.id) == app.key }
                ?.let { com.usos.launcher.data.tiles.TileIntents.launch(container.appContext, it) }
            return
        }
        // El launcher nunca abre apps de ocio congeladas, venga la peticion de donde venga.
        // Con Ocio oculto en inicio no hay donde desbloquearlo: se abre directamente.
        if (configs.value[app.key]?.section == Section.LEISURE && !leisureRepo.isOpen() &&
            Section.LEISURE !in mosaicPrefs.value.hiddenSections
        ) {
            revealLeisure()
            return
        }
        when {
            app.isQuiet -> showNotice(R.string.notice_profile_paused)
            repository.launch(app) -> showNotice(R.string.notice_opening, app.label.uppercase())
            else -> showNotice(R.string.notice_open_failed)
        }
    }

    /** Botón de inicio: cierra la sección abierta (sin olvidar la recordada) y vuelve a la página central. */
    fun onHomePressed() {
        // El aviso del servicio ya llevo al usuario a donde toca; no lo deshacemos con el HOME que lo acompana.
        if (SystemClock.elapsedRealtime() - blockedAt < 2000) {
            _homeEvents.tryEmit(Unit)
            return
        }
        if (_route.value !is Route.Onboarding) closeOverlay()
        closeSearch()
        exitEdit()
        open.value = null
        _homeEvents.tryEmit(Unit)
    }

    /** Cierra la busqueda y abre la seccion Ocio en inicio, sin abrir ninguna app. */
    private fun revealLeisure() {
        closeSearch()
        open.value = Section.LEISURE
        viewModelScope.launch { settings.setLastOpenSection(Section.LEISURE) }
        _homeEvents.tryEmit(Unit)
    }

    fun openSearch() { _searchOpen.value = true }

    fun closeSearch() {
        _searchOpen.value = false
        _query.value = ""
    }

    fun onQueryChange(text: String) { _query.value = text }

    fun pickFromSearch(item: SearchItem) {
        closeSearch()
        openApp(item.app)
    }

    /** Intro (o tocar la primera fila): URL > app con nombre exacto > busqueda en el buscador elegido. */
    fun submitSearch() {
        val text = _query.value.trim()
        if (text.isEmpty()) return
        when (val intent = SearchClassifier.classify(text, searchItems.value) { it.app.label }) {
            is SearchIntent.OpenUrl -> {
                closeSearch()
                if (!repository.openUrl(intent.url)) showNotice(R.string.notice_open_failed)
            }
            is SearchIntent.OpenApp<*> -> pickFromSearch(intent.app as SearchItem)
            is SearchIntent.WebSearch -> {
                closeSearch()
                if (!repository.webSearch(intent.text, searchEngine.value)) showNotice(R.string.notice_search_failed)
            }
        }
    }

    fun tickLeisure() {
        leisureRepo.tick()
        refreshBlocker()
    }

    fun startThaw(durationMin: Int) { leisureRepo.startThaw(durationMin) }

    fun cancelThaw() { leisureRepo.cancelThaw() }

    fun freezeLeisure() { leisureRepo.freeze() }

    /** Salir del launcher cancela una descongelacion en curso. */
    fun onLauncherStopped() { leisureRepo.cancelThaw() }

    private fun showNotice(@StringRes id: Int, vararg args: Any) {
        noticeJob?.cancel()
        _notice.value = container.appContext.getString(id, *args)
        noticeJob = viewModelScope.launch {
            delay(2500)
            _notice.value = null
        }
    }

    companion object {

        /** Apps del tiempo conocidas por fabricante, por orden; si no hay ninguna se busca «tiempo» en la web. */
        val WEATHER_PACKAGES = listOf(
            "com.sec.android.daemonapp", "com.miui.weather2", "com.coloros.weather2", "net.oneplus.weather",
            "com.oneplus.weather", "com.motorola.timeweatherwidget", "com.huawei.android.totemweather",
            "com.google.android.apps.weather",
        )

        val Factory = viewModelFactory {
            initializer { HomeViewModel((this[APPLICATION_KEY] as UsosApp).container) }
        }
    }
}
