package com.usos.launcher.ui.feeds

import androidx.annotation.StringRes
import com.usos.launcher.R
import com.usos.launcher.data.feeds.FeedErrors
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.usos.launcher.UsosApp
import com.usos.launcher.data.feeds.AddOutcome
import com.usos.launcher.data.feeds.Article
import com.usos.launcher.data.feeds.DiscoverResult
import com.usos.launcher.data.feeds.Feed
import com.usos.launcher.data.feeds.FeedFormat
import com.usos.launcher.data.feeds.FoundFeed
import com.usos.launcher.data.feeds.ReadTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Filtro especial: solo los artículos guardados con estrella. */
const val SAVED_FILTER = -2L

/** [label] = nombre del feed; null = pastilla fija con título en [labelRes]. */
data class PillUi(val feedId: Long?, val label: String?, val count: Int, @StringRes val labelRes: Int = 0)

data class CardUi(val article: Article, val source: String, val age: FeedFormat.Age)

data class FeedsUi(
    val pills: List<PillUi>,
    val cards: List<CardUi>,
    /** Lecturas disponibles. */
    val tokens: Int,
    /** Hora de la próxima lectura («19:05»); null con el cubo lleno. */
    val nextTime: String?,
    val hasFeeds: Boolean,
    val hidden: Boolean,
    val filter: Long?,
)

/** Resultado de buscar feeds en el gestor. */
sealed interface DiscoverUi {
    data object Idle : DiscoverUi
    data object Loading : DiscoverUi
    data class Results(val feeds: List<FoundFeed>) : DiscoverUi
}

class FeedsViewModel(private val app: UsosApp) : ViewModel() {
    private val repo = app.container.feedRepository
    private val settings = app.container.settingsStore
    val thumbnails get() = app.container.thumbnailLoader
    val thumbsGray: StateFlow<Boolean> = settings.thumbsGray.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val feeds: StateFlow<List<Feed>> = repo.feeds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val filter = MutableStateFlow<Long?>(null)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private var messageJob: Job? = null

    private val _discover = MutableStateFlow<DiscoverUi>(DiscoverUi.Idle)
    val discover: StateFlow<DiscoverUi> = _discover
    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(15_000)
        }
    }

    private class Tokens(val count: Int, val next: String?, val now: Long)

    private val tokens = combine(settings.readTokens, settings.feedsPerHour, ticker) { state, perHour, now ->
        val s = ReadTokens.normalize(state, now, perHour)
        Tokens(s.count, ReadTokens.nextTime(s, perHour, ZoneId.systemDefault()), now)
    }

    val ui: StateFlow<FeedsUi?> = combine(repo.feeds, repo.articles, tokens, settings.feedsHidden, filter) { feeds, articles, t, hidden, f ->
        val titles = feeds.associate { it.id to it.title }
        // Un filtro apuntando a un feed borrado vuelve a «Todo».
        val active = f?.takeIf { it == SAVED_FILTER || it in titles }
        // «Todo» = unión de los feeds actuales; los guardados de feeds borrados solo salen en «Guardados».
        val live = articles.filter { it.feedId in titles }
        val shown = when (active) {
            null -> live
            SAVED_FILTER -> articles.filter { it.starred }
            else -> live.filter { it.feedId == active }
        }
        FeedsUi(
            pills = listOf(
                PillUi(null, null, live.count { !it.read }, R.string.feeds_filter_all),
                PillUi(SAVED_FILTER, null, articles.count { it.starred }, R.string.feeds_filter_saved),
            ) + feeds.map { fd -> PillUi(fd.id, fd.title, live.count { !it.read && it.feedId == fd.id }) },
            cards = shown.map { CardUi(it, titles[it.feedId] ?: it.sourceTitle, FeedFormat.age(it.publishedAt, t.now)) },
            tokens = t.count,
            nextTime = t.next,
            hasFeeds = feeds.isNotEmpty(),
            hidden = hidden,
            filter = active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Elegir pastilla: si el feed (o alguno, en «Todo») lleva más de 10 min sin actualizar, se refresca. */
    fun setFilter(feedId: Long?) {
        filter.value = feedId
        if (feedId == SAVED_FILTER) return
        autoRefresh(feedId?.let { listOf(it) })
    }

    fun toggleHidden() {
        viewModelScope.launch {
            val hidden = ui.value?.hidden ?: false
            settings.setFeedsHidden(!hidden)
        }
    }

    /**
     * Toque en un artículo. Leído = gratis. No leído = gasta 1 lectura y lo marca; sin lecturas no abre.
     * [open] lanza la URL en el navegador.
     */
    fun open(a: Article, open: (String) -> Unit) {
        viewModelScope.launch {
            if (a.read) {
                open(a.url)
                return@launch
            }
            val perHour = settings.feedsPerHour.first()
            if (settings.spendReadToken(System.currentTimeMillis(), perHour)) {
                repo.markRead(a.id)
                open(a.url)
            } else {
                val next = ui.value?.nextTime
                say(if (next != null) str(R.string.feeds_no_reads_next, str(R.string.feeds_next_read, next)) else str(R.string.feeds_no_reads))
            }
        }
    }

    fun toggleStar(a: Article) {
        viewModelScope.launch {
            repo.setStarred(a.id, !a.starred)
            say(str(if (a.starred) R.string.feeds_unsaved else R.string.feeds_saved))
        }
    }

    /** Al abrir el panel: refresca los feeds con más de 10 min. */
    fun refreshIfStale() {
        viewModelScope.launch {
            if (repo.feedsNow().isEmpty()) return@launch
            repo.pruneAll()
            autoRefresh(null)
        }
    }

    /** Descarga automática según Ajustes → Noticias (nunca en segundo plano); en manual no hace nada. */
    private fun autoRefresh(ids: List<Long>?) {
        viewModelScope.launch {
            val min = settings.feedsRefreshMin.first()
            if (min == 0) return@launch
            runRefresh { repo.refreshFeeds(ids, force = false, maxAgeMs = min * 60_000L) }
        }
    }

    /** Tirar hacia abajo: siempre; solo el feed filtrado si lo hay. */
    fun refresh() {
        val f = filter.value
        if (f == SAVED_FILTER) return
        runRefresh(report = true) { repo.refreshFeeds(f?.let { listOf(it) }, force = true) }
    }

    private fun runRefresh(report: Boolean = false, block: suspend () -> Boolean) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val ok = try { block() } finally { _refreshing.value = false }
            if (!ok && report) say(str(R.string.feeds_refresh_failed))
        }
    }

    // ---- Gestor de feeds ----

    /** Busca feeds a partir de una URL de feed, de una web o un dominio. */
    fun search(text: String) {
        if (_discover.value is DiscoverUi.Loading) return
        _discover.value = DiscoverUi.Loading
        viewModelScope.launch {
            when (val r = repo.discover(text)) {
                is DiscoverResult.Found -> _discover.value = DiscoverUi.Results(r.feeds)
                is DiscoverResult.Failed -> {
                    _discover.value = DiscoverUi.Idle
                    say(errorText(r.message))
                }
            }
        }
    }

    fun clearDiscover() { _discover.value = DiscoverUi.Idle }

    /** Añade un feed encontrado (ya descargado) y muestra sus artículos. */
    fun addFound(f: FoundFeed, onAdded: () -> Unit) {
        viewModelScope.launch {
            val id = repo.add(f)
            if (id == null) {
                say(str(R.string.feeds_already_have))
            } else {
                _discover.value = DiscoverUi.Idle
                filter.value = id
                onAdded()
            }
        }
    }

    /** Sugerido de un toque: descarga al instante, lo añade y lo muestra. */
    fun addSuggested(name: String, url: String, onAdded: () -> Unit) {
        if (_adding.value) return
        viewModelScope.launch {
            _adding.value = true
            val r = try { repo.addByUrl(url, name) } finally { _adding.value = false }
            when (r) {
                is AddOutcome.Added -> { filter.value = r.feedId; onAdded() }
                AddOutcome.Duplicate -> say(str(R.string.feeds_already_have))
                is AddOutcome.Failed -> say(errorText(r.message))
            }
        }
    }

    fun rename(id: Long, title: String) { viewModelScope.launch { repo.rename(id, title) } }
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }

    private fun str(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    /** Traduce los códigos de FeedErrors; «HTTP 404» se deja tal cual. */
    private fun errorText(code: String): String = when (code) {
        FeedErrors.INVALID -> str(R.string.feeds_err_invalid)
        FeedErrors.NOT_A_FEED -> str(R.string.feeds_err_not_a_feed)
        FeedErrors.OFFLINE -> str(R.string.feeds_err_offline)
        else -> code
    }

    private fun say(text: String) {
        messageJob?.cancel()
        _message.value = text
        messageJob = viewModelScope.launch {
            delay(3000)
            _message.value = null
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { FeedsViewModel(this[APPLICATION_KEY] as UsosApp) }
        }
    }
}
