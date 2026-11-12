package com.usos.launcher.ui.home

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.usos.launcher.AppContainer
import com.usos.launcher.UsosApp
import com.usos.launcher.data.sections.DEFAULT_ACCENT
import com.usos.launcher.data.sections.MOSAIC_CAPACITY
import com.usos.launcher.data.sections.SectionLayout
import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TileUi(val app: AppEntry, val code: String, val dimmed: Boolean)

data class SectionUi(val section: Section, val count: Int, val tiles: List<TileUi>)

data class HomeUiState(val sections: List<SectionUi>, val open: Section?)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.appRepository
    private val configStore = container.appConfigStore
    private val settings = container.settingsStore

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
    val state: StateFlow<HomeUiState?> = combine(apps, configStore.configs, open, openLoaded) { a, c, o, loaded ->
        if (a == null || !loaded || a.any { it.key !in c }) return@combine null
        val grouped = SectionLayout.build(a, c)
        HomeUiState(
            sections = Section.home.map { section ->
                val placed = grouped[section].orEmpty()
                SectionUi(
                    section = section,
                    count = placed.size,
                    tiles = placed.filter { it.config.inMosaic }.take(MOSAIC_CAPACITY)
                        .map { TileUi(it.app, it.code, it.app.isQuiet) },
                )
            },
            open = o,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var noticeJob: Job? = null

    init {
        viewModelScope.launch {
            open.value = settings.lastOpenSection.first()
            openLoaded.value = true
        }
        viewModelScope.launch {
            apps.collect { list -> if (list != null) configStore.reconcile(list) }
        }
    }

    fun toggleSection(section: Section) {
        val next = if (open.value == section) null else section
        open.value = next
        viewModelScope.launch { settings.setLastOpenSection(next) }
    }

    fun openApp(app: AppEntry) {
        when {
            app.isQuiet -> showNotice("PERFIL EN PAUSA")
            repository.launch(app) -> showNotice("ABRIENDO ${app.label.uppercase()}…")
            else -> showNotice("NO SE PUDO ABRIR")
        }
    }

    /** Botón de inicio: cierra la sección abierta (sin olvidar la recordada) y vuelve a la página central. */
    fun onHomePressed() {
        open.value = null
        _homeEvents.tryEmit(Unit)
    }

    private fun showNotice(text: String) {
        noticeJob?.cancel()
        _notice.value = text
        noticeJob = viewModelScope.launch {
            delay(2500)
            _notice.value = null
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel((this[APPLICATION_KEY] as UsosApp).container) }
        }
    }
}
