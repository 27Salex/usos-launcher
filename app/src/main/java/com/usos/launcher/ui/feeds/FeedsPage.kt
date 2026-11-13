package com.usos.launcher.ui.feeds

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.usos.launcher.data.feeds.ThumbnailLoader
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap
import kotlin.math.cos
import kotlin.math.sin

private val ThumbSize = 56.dp

/** Página derecha del pager: lector de feeds. */
@Composable
fun FeedsPage(
    vm: FeedsViewModel,
    topSpacing: Dp,
    onManage: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = LocalAccent.current
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) { vm.refreshIfStale() }

    Column(Modifier.fillMaxSize().padding(horizontal = UsosDimens.ScreenMargin)) {
        Spacer(Modifier.height(topSpacing))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UsosText("Noticias", UsosType.PanelTitle, UsosColors.Text, Modifier.weight(1f))
            Box(Modifier.height(UsosDimens.MinTouch).tap { onManage() }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                UsosText("FEEDS", UsosType.Label, UsosColors.TextSecondary)
            }
            Box(Modifier.size(UsosDimens.MinTouch).tap { vm.toggleHidden() }, contentAlignment = Alignment.Center) {
                EyeIcon(crossed = ui?.hidden == true, color = if (ui?.hidden == true) accent else UsosColors.Text)
            }
        }
        val state = ui
        if (state == null) return@Column
        val status = message ?: state.queueLabel.takeIf { !state.hidden }
        if (state.hidden) {
            UsosText("CONTENIDO OCULTO", UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 8.dp))
            return@Column
        }
        Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
            if (status != null) UsosText(status, UsosType.Label, accent)
        }
        LazyRow(
            Modifier.fillMaxWidth().height(UsosDimens.MinTouch),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(state.pills, key = { it.feedId ?: -1L }) { pill ->
                FilterPill(pill, active = pill.feedId == state.filter) { vm.setFilter(pill.feedId) }
            }
        }
        Spacer(Modifier.height(8.dp))
        PullToRefresh(refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.cards.isEmpty()) {
                    item {
                        val text = when {
                            !state.hasFeeds -> "AÑADE UN FEED EN «FEEDS»"
                            state.queueLabel != null -> "AL DÍA POR AHORA"
                            else -> "AL DÍA"
                        }
                        UsosText(text, UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 24.dp))
                    }
                }
                items(state.cards, key = { it.article.id }) { card ->
                    ArticleCard(card, vm.thumbnails, onOpen = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(card.article.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        vm.markRead(card.article.id)
                    }, onStar = { vm.toggleStar(card.article) })
                }
                item { Spacer(Modifier.height(navBar + 12.dp)) }
            }
        }
    }
}

@Composable
private fun FilterPill(pill: PillUi, active: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(UsosDimens.RadiusPill)
    val label = if (pill.unread > 0) "${pill.label} ${pill.unread}" else pill.label
    Box(
        Modifier
            .height(UsosDimens.MinTouch)
            .background(if (active) accent else UsosColors.Background, shape)
            .border(1.dp, if (active) accent else UsosColors.BorderStrong, shape)
            .tap { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        UsosText(label, UsosType.Pill, if (active) UsosColors.OnAccent else UsosColors.Text)
    }
}

@Composable
private fun ArticleCard(card: CardUi, loader: ThumbnailLoader, onOpen: () -> Unit, onStar: () -> Unit) {
    val a = card.article
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Row(
        Modifier.fillMaxWidth().background(UsosColors.Surface, shape).tap { onOpen() }
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            UsosText(a.title, UsosType.PieceData, UsosColors.Text, maxLines = 3)
            if (a.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                UsosText(a.summary, UsosType.Empty, UsosColors.TextSecondary, maxLines = 2)
            }
            Row(Modifier.fillMaxWidth().height(UsosDimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
                if (!a.read) {
                    Box(Modifier.size(8.dp).background(accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                UsosText(
                    "${card.source.uppercase()} · ${card.age}", UsosType.Label, UsosColors.TextSecondary,
                    Modifier.weight(1f),
                )
                Box(Modifier.size(UsosDimens.MinTouch).tap { onStar() }, contentAlignment = Alignment.Center) {
                    StarIcon(filled = a.starred, color = if (a.starred) accent else UsosColors.TextSecondary)
                }
            }
        }
        if (a.imageUrl != null) {
            Spacer(Modifier.width(12.dp))
            Thumbnail(a.imageUrl, loader)
        }
    }
}

@Composable
private fun Thumbnail(url: String, loader: ThumbnailLoader) {
    val px = with(LocalDensity.current) { ThumbSize.roundToPx() }
    val bitmap by produceState(loader.cached(url), url) {
        if (value == null) value = loader.load(url, px)
    }
    Box(Modifier.size(ThumbSize).clip(RoundedCornerShape(UsosDimens.RadiusThumb)).background(UsosColors.Border)) {
        bitmap?.let {
            Image(
                it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Pull-to-refresh propio con nestedScroll de foundation (material3 no es dependencia del proyecto). */
@Composable
fun PullToRefresh(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val isRefreshing by rememberUpdatedState(refreshing)
    val refresh by rememberUpdatedState(onRefresh)
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !isRefreshing) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(threshold * 1.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull >= threshold && !isRefreshing) refresh()
                pull = 0f
                return Velocity.Zero
            }
        }
    }
    Column(modifier.nestedScroll(connection)) {
        val headerPx = if (refreshing) threshold * 0.6f else pull.coerceAtMost(threshold)
        val header = with(density) { headerPx.toDp() }
        Box(Modifier.fillMaxWidth().height(header), contentAlignment = Alignment.Center) {
            if (header > 16.dp) {
                UsosText(
                    when {
                        refreshing -> "ACTUALIZANDO…"
                        pull >= threshold -> "SUELTA PARA ACTUALIZAR"
                        else -> "TIRA PARA ACTUALIZAR"
                    },
                    UsosType.Label, UsosColors.TextSecondary,
                )
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun EyeIcon(crossed: Boolean, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx())
        val eye = Path().apply {
            moveTo(w * 0.04f, h / 2)
            quadraticTo(w / 2, h * 0.02f, w * 0.96f, h / 2)
            quadraticTo(w / 2, h * 0.98f, w * 0.04f, h / 2)
            close()
        }
        drawPath(eye, color, style = stroke)
        drawCircle(color, radius = w * 0.14f, center = Offset(w / 2, h / 2), style = stroke)
        if (crossed) drawLine(color, Offset(w * 0.15f, h * 0.9f), Offset(w * 0.85f, h * 0.1f), strokeWidth = 1.8.dp.toPx())
    }
}

@Composable
private fun StarIcon(filled: Boolean, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(20.dp)) {
        val c = Offset(size.width / 2, size.height / 2 + size.height * 0.04f)
        val outer = size.width / 2
        val inner = outer * 0.42f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val ang = Math.toRadians(-90.0 + i * 36.0)
            val p = Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(width = 1.6.dp.toPx()))
    }
}
