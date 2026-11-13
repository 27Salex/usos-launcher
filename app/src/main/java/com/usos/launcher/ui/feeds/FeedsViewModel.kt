package com.usos.launcher.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.usos.launcher.UsosApp
import com.usos.launcher.data.feeds.AddResult
import com.usos.launcher.data.feeds.Article
import com.usos.launcher.data.feeds.Feed
import com.usos.launcher.data.feeds.FeedFormat
import com.usos.launcher.data.feeds.FeedQueue
import com.usos.launcher.data.feeds.QueuePlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class PillUi(val feedId: Long?, val label: String, val unread: Int)

data class CardUi(val article: Article, val source: String, val age: String)

data class FeedsUi(
    val pills: List<PillUi>,
    val cards: List<CardUi>,
    val queueLabel: String?,
    val hasFeeds: Boolean,
    val hidden: Boolean,
    val filter: Long?,
)

class FeedsViewModel(private val app: UsosApp) : ViewModel() {
    private val repo = app.container.feedRepository
    private val settings = app.container.settingsStore
    val thumbnails get() = app.container.thumbnailLoader

    val feeds: StateFlow<List<Feed>> = repo.feeds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val perHour: StateFlow<Int> = settings.feedsPerHour.stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    private val filter = MutableStateFlow<Long?>(null)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private var messageJob: Job? = null

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(20_000)
        }
    }

    private class Base(val feeds: List<Feed>, val plan: QueuePlan, val perHour: Int, val now: Long, val hidden: Boolean)

    /** Solo mientras el panel está en pantalla (WhileSubscribed): el desvelado no corre en segundo plano. */
    private val base = combine(
        repo.feeds, repo.articles, settings.feedsPerHour, ticker, settings.feedsHidden,
    ) { feeds, articles, perHour, now, hidden ->
        Base(feeds, FeedQueue.plan(articles, now, ZoneId.systemDefault(), perHour), perHour, now, hidden)
    }.onEach { if (!it.hidden) repo.reveal(it.plan.toReveal) }

    val ui: StateFlow<FeedsUi?> = combine(base, filter) { b, f ->
        val zone = ZoneId.systemDefault()
        val titles = b.feeds.associate { it.id to it.title }
        // Un filtro apuntando a un feed borrado vuelve a «Todo».
        val active = f?.takeIf { it in titles }
        val unread = b.plan.visible.filter { !it.read }
        FeedsUi(
            pills = listOf(PillUi(null, "Todo", unread.size)) +
                b.feeds.map { fd -> PillUi(fd.id, fd.title, unread.count { it.feedId == fd.id }) },
            cards = b.plan.visible.filter { active == null || it.feedId == active }.map {
                CardUi(it, titles[it.feedId].orEmpty(), com.usos.launcher.data.feeds.FeedFormat.age(it.publishedAt, b.now))
            },
            queueLabel = FeedQueue.queueLabel(b.plan, b.perHour, zone),
            hasFeeds = b.feeds.isNotEmpty(),
            hidden = b.hidden,
            filter = active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setFilter(feedId: Long?) { filter.value = feedId }

    fun toggleHidden() {
        viewModelScope.launch {
            val hidden = ui.value?.hidden ?: false
            settings.setFeedsHidden(!hidden)
        }
    }

    fun markRead(id: Long) { viewModelScope.launch { repo.markRead(id) } }
    fun toggleStar(a: Article) { viewModelScope.launch { repo.setStarred(a.id, !a.starred) } }

    /** Al abrir el panel: solo si han pasado más de 30 min desde la última actualización. */
    fun refreshIfStale() {
        viewModelScope.launch {
            if (feeds.value.isEmpty() && repo.lastFetchedAt() == null) return@launch
            if (FeedFormat.needsRefresh(repo.lastFetchedAt(), System.currentTimeMillis())) refresh()
        }
    }

    /** Tirar hacia abajo. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val ok = try { repo.refreshAll() } finally { _refreshing.value = false }
            if (!ok) say("NO SE PUDIERON ACTUALIZAR TODOS")
        }
    }

    fun addFeed(url: String, onAdded: () -> Unit) {
        viewModelScope.launch {
            _refreshing.value = true
            val result = try { repo.addFeed(url) } finally { _refreshing.value = false }
            when (result) {
                AddResult.Added -> onAdded()
                AddResult.InvalidUrl -> say("URL NO VÁLIDA")
                AddResult.Duplicate -> say("YA LO TIENES")
                AddResult.NetworkError -> say("NO SE PUDO LEER EL FEED")
            }
        }
    }

    fun rename(id: Long, title: String) { viewModelScope.launch { repo.rename(id, title) } }
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }

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
