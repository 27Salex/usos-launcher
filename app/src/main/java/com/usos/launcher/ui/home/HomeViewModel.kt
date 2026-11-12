package com.usos.launcher.ui.home

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
import com.usos.launcher.data.sections.Highlight
import com.usos.launcher.data.sections.PlacedApp
import com.usos.launcher.data.sections.SectionOrder
import com.usos.launcher.data.usage.UsageSummary
import com.usos.launcher.service.LauncherAlert
import com.usos.launcher.service.isBlockerEnabled
import com.usos.launcher.ui.appdetail.DetailUi
import com.usos.launcher.data.sections.DEFAULT_ACCENT
import com.usos.launcher.data.leisure.LeisureStatus
import com.usos.launcher.data.sections.MOSAIC_CAPACITY
import com.usos.launcher.data.sections.SectionLayout
import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.search.SearchFilter
import com.usos.launcher.ui.search.SearchItem
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
    val usageMin: Int? = null,
)

data class SectionUi(val section: Section, val count: Int, val tiles: List<TileUi>)

data class HomeUiState(val sections: List<SectionUi>, val open: Section?)

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

    private val _route = MutableStateFlow<Route>(Route.Home)
    val route: StateFlow<Route> = _route

    private class DetailExtras(val key: String, val hasPermission: Boolean, val usage: UsageSummary?, val storage: Long?)
    private val detailExtras = MutableStateFlow<DetailExtras?>(null)
    private val todayUsage = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val usageRepo = container.usageRepository

    private val _blockerActive = MutableStateFlow(true)
    val blockerActive: StateFlow<Boolean> = _blockerActive
    private var blockedAt = -10_000L

    val budget: StateFlow<Int> = leisureRepo.budget
    val wait: StateFlow<Int> = leisureRepo.wait

    private val apps: StateFlow<List<AppEntry>?> = repository.apps
        .map<List<AppEntry>, List<AppEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null mientras se lee de DataStore; luego la sección abierta o [NoSection]. */
    private val open = MutableStateFlow<Section?>(null)
    private val openLoaded = MutableStateFlow(false)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeEvents: SharedFlow<Unit> = _homeEvents.asSharedFlow()

    val accent: StateFlow<Color> = settings.accent
        .map { Color(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Color(DEFAULT_ACCENT))

    /** null hasta que apps, configuración y sección abierta están listas. */
    val state: StateFlow<HomeUiState?> = combine(apps, configStore.configs, open, openLoaded, todayUsage) { a, c, o, loaded, usage ->
        if (a == null || !loaded || a.any { it.key !in c }) return@combine null
        val grouped = SectionLayout.build(a, c)
        HomeUiState(
            sections = Section.home.map { section ->
                val placed = grouped[section].orEmpty()
                SectionUi(
                    section = section,
                    count = placed.size,
                    tiles = SectionLayout.mosaic(placed, if (section == Section.LEISURE) LEISURE_APP_SLOTS else MOSAIC_CAPACITY)
                        .map {
                            TileUi(
                                it.app, it.code, it.app.isQuiet, it.config.highlight,
                                usageMin = if (it.config.showUsage) usage[it.app.packageName] ?: 0 else null,
                            )
                        },
                )
            },
            open = o,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Todas las apps para la busqueda; las de ocio llevan `frozen` mientras el ocio no este abierto. */
    val searchItems: StateFlow<List<SearchItem>> = combine(
        apps, configs, leisure.map { it?.isOpen == true }.distinctUntilChanged(),
    ) { a, c, leisureOpen ->
        a.orEmpty().mapNotNull { app ->
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

    val detail: StateFlow<DetailUi?> = combine(_route, apps, configs, detailExtras) { r, a, c, extras ->
        val key = (r as? Route.Detail)?.key ?: return@combine null
        val app = a?.firstOrNull { it.key == key } ?: return@combine null
        val cfg = c[key] ?: return@combine null
        val placed = SectionLayout.build(a, c)[cfg.section].orEmpty()
        val ex = extras?.takeIf { it.key == key }
        DetailUi(
            app = app, config = cfg, code = cfg.code?.takeIf { it.isNotBlank() } ?: app.defaultCode,
            usage = ex?.usage, hasPermission = ex?.hasPermission ?: usageRepo.hasPermission(),
            storageBytes = ex?.storage,
            mosaicFull = placed.count { it.config.inMosaic } >= capacityFor(cfg.section),
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
            apps.collect { list -> if (list != null) configStore.reconcile(list) }
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
        refreshBlocker()
    }

    private fun onAlert(alert: LauncherAlert) {
        blockedAt = SystemClock.elapsedRealtime()
        if (_route.value !is Route.Onboarding) _route.value = Route.Home
        if (alert.revealLeisure) revealLeisure() else closeSearch()
        showNotice(alert.text)
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

    fun openSettings() { _route.value = Route.Settings }

    /** Atras desde ficha o ajustes. */
    fun closeOverlay() {
        _route.value = Route.Home
        detailExtras.value = null
    }

    /** Terminar o saltar el primer arranque. */
    fun finishOnboarding() {
        _route.value = Route.Home
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }

    fun restartOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone(false) }
        _route.value = Route.Onboarding
    }

    // ---- Ficha de app ----

    private fun capacityFor(section: Section) =
        if (section == Section.LEISURE) LEISURE_APP_SLOTS else MOSAIC_CAPACITY

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
                val room = section != Section.NONE && inSection.count { it.inMosaic } < capacityFor(section)
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
                val others = all.values.count { it.section == cfg.section && it.inMosaic && it.key != key }
                if (on && others >= capacityFor(cfg.section)) {
                    full = true
                    return@update all
                }
                all + (key to cfg.copy(inMosaic = on, manual = true))
            }
            if (full) showNotice("MOSAICO LLENO")
        }
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

    fun toggleSection(section: Section) {
        val next = if (open.value == section) null else section
        open.value = next
        viewModelScope.launch { settings.setLastOpenSection(next) }
    }

    fun openApp(app: AppEntry) {
        // El launcher nunca abre apps de ocio congeladas, venga la peticion de donde venga.
        if (configs.value[app.key]?.section == Section.LEISURE && !leisureRepo.isOpen()) {
            revealLeisure()
            return
        }
        when {
            app.isQuiet -> showNotice("PERFIL EN PAUSA")
            repository.launch(app) -> showNotice("ABRIENDO ${app.label.uppercase()}…")
            else -> showNotice("NO SE PUDO ABRIR")
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

    /** Intro: una sola coincidencia la abre; si no, busqueda web del texto. */
    fun submitSearch() {
        val text = _query.value.trim()
        if (text.isEmpty()) return
        val matches = SearchFilter.groups(searchItems.value, text).flatMap { it.items }
        if (matches.size == 1) {
            pickFromSearch(matches[0])
        } else {
            closeSearch()
            if (!repository.webSearch(text)) showNotice("NO SE PUDO BUSCAR")
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

    private fun showNotice(text: String) {
        noticeJob?.cancel()
        _notice.value = text
        noticeJob = viewModelScope.launch {
            delay(2500)
            _notice.value = null
        }
    }

    companion object {
        /** El mosaico de Ocio reserva una celda para la pieza CONGELAR. */
        const val LEISURE_APP_SLOTS = MOSAIC_CAPACITY - 1

        val Factory = viewModelFactory {
            initializer { HomeViewModel((this[APPLICATION_KEY] as UsosApp).container) }
        }
    }
}
