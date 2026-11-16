package com.usos.launcher.ui.widgets

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.usos.launcher.R
import com.usos.launcher.data.info.AgendaRepository
import com.usos.launcher.data.info.CalEvent
import com.usos.launcher.ui.home.DateText
import com.usos.launcher.ui.home.DotMatrix
import com.usos.launcher.ui.home.DotText
import com.usos.launcher.ui.home.rememberNow
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.LocalOnAccent
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

private val PieceShape = RoundedCornerShape(UsosDimens.RadiusPiece)

/** Superficie común de las piezas propias: #111111 con borde blanco destacado (config `border=off` = borde gris fino). */
@Composable
private fun PieceSurface(modifier: Modifier = Modifier, bordered: Boolean = true, content: @Composable () -> Unit) {
    val border = if (bordered) BorderStroke(1.5.dp, UsosColors.Text) else BorderStroke(1.dp, UsosColors.Border)
    Box(modifier.clip(PieceShape).background(UsosColors.Surface).border(border, PieceShape)) { content() }
}

/** Pieza propia colocada en la rejilla (la edición la gestiona WidgetsPage, igual que las de Android). */
@Composable
fun OwnWidgetBox(widget: HostedWidget, width: Dp, height: Dp, calendarGranted: Boolean, onRequestCalendar: () -> Unit) {
    PieceSurface(Modifier.size(width, height), OwnWidgets.bordered(widget.config)) {
        when (widget.widgetKind) {
            WidgetKind.USOS_MUSIC -> MusicWidget(OwnWidgets.get(widget.config, OwnWidgets.KEY_BARS) == "accent")
            WidgetKind.USOS_CALENDAR -> CalendarWidget(CalMode.fromKey(OwnWidgets.get(widget.config, OwnWidgets.KEY_MODE)), height, calendarGranted, onRequestCalendar)
            WidgetKind.USOS_CLOCK -> ClockWidget(width, height, OwnWidgets.get(widget.config, OwnWidgets.KEY_TZ).orEmpty())
            WidgetKind.ANDROID -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Música
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun MusicWidget(accentBars: Boolean) {
    val context = LocalContext.current
    val ui by rememberMediaUi()
    when (val m = ui) {
        MediaUi.NoAccess -> Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            UsosPill(
                stringResource(R.string.widget_music_access), filled = false,
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            )
        }
        MediaUi.Idle -> MusicContent("", "", false, accentBars, null, null, null, null)
        is MediaUi.Session -> {
            val c = m.controller.transportControls
            MusicContent(
                m.title, m.artist, m.playing, accentBars,
                onTitle = { openMediaApp(context, m) },
                onPrev = { runCatching { c.skipToPrevious() } },
                onPlay = { runCatching { if (m.playing) c.pause() else c.play() } },
                onNext = { runCatching { c.skipToNext() } },
            )
        }
    }
}

@Composable
private fun MusicContent(
    title: String, artist: String, playing: Boolean, accentBars: Boolean,
    onTitle: (() -> Unit)?, onPrev: (() -> Unit)?, onPlay: (() -> Unit)?, onNext: (() -> Unit)?,
) {
    val barColor = if (accentBars) LocalAccent.current else UsosColors.Text
    val shownTitle = title.ifBlank { stringResource(R.string.widget_music_idle) }.uppercase()
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val compact = maxHeight < 110.dp
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().then(if (onTitle != null) Modifier.tap { onTitle() } else Modifier)) {
                UsosText(shownTitle, UsosType.PanelState, UsosColors.Text)
                UsosText(artist.uppercase(), UsosType.Label, UsosColors.TextSecondary)
            }
            if (!compact) {
                Bars(playing, barColor, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
            } else Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Transport(Glyph.PREV, onPrev)
                Transport(if (playing) Glyph.PAUSE else Glyph.PLAY, onPlay)
                Transport(Glyph.NEXT, onNext)
                if (compact) Bars(playing, barColor, Modifier.weight(1f).height(28.dp).padding(start = 12.dp))
            }
        }
    }
}

private enum class Glyph { PREV, PLAY, PAUSE, NEXT }

@Composable
private fun Transport(glyph: Glyph, onClick: (() -> Unit)?) {
    Box(
        Modifier.size(40.dp).then(if (onClick != null) Modifier.tap { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (onClick != null) UsosColors.Text else UsosColors.TextSecondary
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            fun tri(x0: Float, x1: Float) = Path().apply {
                moveTo(x0, 0f); lineTo(x1, h / 2f); lineTo(x0, h); close()
            }
            when (glyph) {
                Glyph.PLAY -> drawPath(tri(w * 0.15f, w), color)
                Glyph.PAUSE -> {
                    drawRect(color, Offset(w * 0.15f, 0f), androidx.compose.ui.geometry.Size(w * 0.25f, h))
                    drawRect(color, Offset(w * 0.6f, 0f), androidx.compose.ui.geometry.Size(w * 0.25f, h))
                }
                Glyph.NEXT -> {
                    drawPath(tri(0f, w * 0.8f), color)
                    drawRect(color, Offset(w * 0.8f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
                }
                Glyph.PREV -> {
                    drawRect(color, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
                    drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.2f, h / 2f); lineTo(w, h); close() }, color)
                }
            }
        }
    }
}

/**
 * Barras procedurales (sin micrófono ni Visualizer): `MusicBars` con un reloj de ~30 fps que solo corre mientras
 * suena, la pantalla está en primer plano y las animaciones del sistema están activas; el reloj se lee en el dibujo.
 */
@Composable
private fun Bars(playing: Boolean, color: Color, modifier: Modifier) {
    val animate = playing && LocalAnimationsEnabled.current
    val timeMs = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate, lifecycle) {
        if (!animate) { timeMs.longValue = 0L; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { n ->
                    if (n - last >= 33_000_000L) { last = n; timeMs.longValue = n / 1_000_000L }
                }
            }
        }
    }
    Canvas(modifier) {
        val barW = 3.dp.toPx()
        val gap = 3.dp.toPx()
        val n = ((size.width + gap) / (barW + gap)).toInt().coerceIn(4, 40)
        val total = n * barW + (n - 1) * gap
        val x0 = (size.width - total) / 2f
        val t = timeMs.longValue / 1000f
        for (i in 0 until n) {
            val hh = (MusicBars.height(i, n, t, playing) * size.height).coerceAtLeast(2.dp.toPx())
            drawRect(color, Offset(x0 + i * (barW + gap), size.height - hh), androidx.compose.ui.geometry.Size(barW, hh))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Calendario
// ---------------------------------------------------------------------------------------------------------------

private fun openEvent(context: Context, e: CalEvent) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.beginMs)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.endMs)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openDay(context: Context, date: LocalDate, zone: ZoneId) {
    runCatching {
        val ms = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, ms) }.build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun CalendarWidget(mode: CalMode, height: Dp, granted: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val nowState = rememberNow()
    val today by remember { derivedStateOf { nowState.value.toLocalDate() } }
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(granted) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { tick++ }
        }
        if (granted) runCatching { context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val large = height >= 240.dp
    val shown = if (mode != CalMode.AUTO) mode else if (large) CalMode.MONTH else CalMode.AGENDA
    // Página (semana/mes/7 días) que se ve: 0 = la actual; se cambia deslizando dentro del widget o con las flechas.
    var offset by remember(today, shown) { mutableIntStateOf(0) }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    val repo = remember { AgendaRepository(context) }
    // Una consulta por página: lo que se ve más (en la página actual) hasta pasado mañana. Se repite al cambiar el calendario o el día.
    LaunchedEffect(granted, today, tick, locale, offset, shown) {
        events = if (!granted) emptyList() else {
            val (ws, we) = CalendarLogic.window(shown, today, offset, DateText.firstDayOfWeek(locale))
            val start = if (offset == 0) minOf(ws, today) else ws
            val end = if (offset == 0) maxOf(we, today.plusDays(2)) else we
            repo.range(start.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli())
        }
    }
    if (!granted) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            UsosPill(stringResource(R.string.widget_cal_permission), filled = false, onClick = onRequest)
        }
        return
    }
    var picked by remember(today, offset) { mutableStateOf<LocalDate?>(null) }
    val nowMs = nowState.value.atZone(zone).toInstant().toEpochMilli()
    val defaultSel = if (offset == 0) today else CalendarLogic.window(shown, today, offset, DateText.firstDayOfWeek(locale)).first
    CalendarView(
        mode = shown, large = large, today = today, selected = picked ?: defaultSel, nowMs = nowMs, zone = zone, events = events,
        onEvent = { openEvent(context, it) }, onDay = { openDay(context, it, zone) }, onSelect = { picked = it },
        offset = offset, onOffset = { offset = it },
    )
}

private val EventRowH = 30.dp
private val EventPillH = 26.dp
private val HeaderRowH = 20.dp
private val NavRowH = 24.dp

@Composable
private fun CalendarView(
    mode: CalMode, large: Boolean, today: LocalDate, selected: LocalDate, nowMs: Long, zone: ZoneId, events: List<CalEvent>,
    onEvent: (CalEvent) -> Unit, onDay: (LocalDate) -> Unit, onSelect: (LocalDate) -> Unit,
    offset: Int = 0, onOffset: (Int) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val shown = if (mode != CalMode.AUTO) mode else if (large) CalMode.MONTH else CalMode.AGENDA
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val currentOffset by rememberUpdatedState(offset)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            // Deslizar en horizontal dentro del widget cambia de página (consume el gesto: el pager no se mueve).
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = {
                        if (acc <= -swipeThreshold) onOffset(currentOffset + 1) else if (acc >= swipeThreshold) onOffset(currentOffset - 1)
                    },
                ) { change, dx -> change.consume(); acc += dx }
            }
            .padding(12.dp),
    ) {
        val h = maxHeight
        when (shown) {
            CalMode.WEEK -> WeekView(today, selected, nowMs, zone, locale, events, maxHeight, onEvent, onSelect, offset, onOffset)
            CalMode.MONTH -> MonthView(today, nowMs, zone, locale, events, maxHeight, onEvent, onDay, offset, onOffset)
            else -> {
                val start = today.plusDays(7L * offset)
                Column(Modifier.fillMaxSize()) {
                    PageNav(rangeLabel(start, start.plusDays(6), locale), offset, onOffset)
                    AgendaList(
                        CalendarLogic.agenda(events, nowMs, zone, 7, if (offset == 0) null else start),
                        today, zone, locale, nowMs, h - NavRowH, onEvent,
                    )
                }
            }
        }
    }
}

private fun rangeLabel(a: LocalDate, b: LocalDate, locale: Locale): String {
    fun mon(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).trimEnd('.')
    return if (a.month == b.month) "${a.dayOfMonth} – ${b.dayOfMonth} ${mon(b)}" else "${a.dayOfMonth} ${mon(a)} – ${b.dayOfMonth} ${mon(b)}"
}

/** Cabecera de página: título (toque = volver a hoy) y flechas ‹ ›. */
@Composable
private fun PageNav(title: String, offset: Int, onOffset: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(NavRowH), verticalAlignment = Alignment.CenterVertically) {
        UsosText(
            title, UsosType.PanelState, if (offset == 0) UsosColors.Text else LocalAccent.current,
            Modifier.weight(1f).tap { onOffset(0) }, maxLines = 1,
        )
        NavArrow("‹") { onOffset(offset - 1) }
        Spacer(Modifier.width(4.dp))
        NavArrow("›") { onOffset(offset + 1) }
    }
}

@Composable
private fun NavArrow(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(28.dp, NavRowH).background(UsosColors.SurfaceRaised, RoundedCornerShape(8.dp)).tap { onClick() }, contentAlignment = Alignment.Center) {
        UsosText(glyph, UsosType.PanelState, UsosColors.Text)
    }
}

@Composable
private fun MonthView(
    today: LocalDate, nowMs: Long, zone: ZoneId, locale: Locale, events: List<CalEvent>, maxHeight: Dp,
    onEvent: (CalEvent) -> Unit, onDay: (LocalDate) -> Unit, offset: Int = 0, onOffset: (Int) -> Unit = {},
) {
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    val grid = remember(month, locale) { CalendarLogic.monthGrid(month, DateText.firstDayOfWeek(locale)) }
    val marked = remember(events, zone) { CalendarLogic.eventDays(events, zone) }
    val accent = LocalAccent.current
    val cellH = 26.dp
    val fixed = NavRowH + 18.dp + cellH * grid.size + 6.dp
    val room = maxHeight - fixed
    Column(Modifier.fillMaxSize()) {
        PageNav(month.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale) + " " + month.year, offset, onOffset)
        Row(Modifier.fillMaxWidth().height(18.dp)) {
            grid.first().forEach { c ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    UsosText(DateText.weekLetter(c.date, locale), UsosType.Label, UsosColors.TextSecondary)
                }
            }
        }
        grid.forEach { week ->
            Row(Modifier.fillMaxWidth().height(cellH)) {
                week.forEach { c ->
                    Box(Modifier.weight(1f).height(cellH).tap { onDay(c.date) }, contentAlignment = Alignment.Center) {
                        val isToday = c.date == today
                        if (!c.inMonth) {
                            Box(Modifier.size(2.dp).background(UsosColors.Border, CircleShape))
                        } else {
                            Box(
                                Modifier.size(22.dp).then(if (isToday) Modifier.background(UsosColors.Text, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                UsosText(
                                    c.date.dayOfMonth.toString(), UsosType.Label,
                                    if (isToday) UsosColors.OnAccent else UsosColors.TextTertiary,
                                )
                            }
                            if (c.date in marked) {
                                Box(Modifier.align(Alignment.BottomCenter).size(3.dp).background(accent, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (room >= EventRowH) {
            val start = if (offset == 0) null else month.atDay(1)
            AgendaList(CalendarLogic.agenda(events, nowMs, zone, 62, start), today, zone, locale, nowMs, room, onEvent)
        }
    }
}

/** Tira de la semana (como la del inicio) + eventos del día elegido. */
@Composable
private fun WeekView(
    today: LocalDate, selected: LocalDate, nowMs: Long, zone: ZoneId, locale: Locale, events: List<CalEvent>, maxHeight: Dp,
    onEvent: (CalEvent) -> Unit, onSelect: (LocalDate) -> Unit, offset: Int = 0, onOffset: (Int) -> Unit = {},
) {
    val week = remember(today, locale, offset) { CalendarLogic.weekOf(today.plusWeeks(offset.toLong()), DateText.firstDayOfWeek(locale)) }
    val marked = remember(events, zone) { CalendarLogic.eventDays(events, zone) }
    val dayEvents = remember(events, selected, zone) { CalendarLogic.eventsOn(events, selected, zone) }
    val accent = LocalAccent.current
    val stripH = 14.dp + 4.dp + 28.dp + 7.dp
    val room = maxHeight - NavRowH - stripH - 6.dp - HeaderRowH
    val fit = (room / EventRowH).toInt().coerceAtLeast(0)
    Column(Modifier.fillMaxSize()) {
        PageNav(rangeLabel(week.first(), week.last(), locale), offset, onOffset)
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                val isToday = day == today
                val isSel = day == selected
                Column(Modifier.weight(1f).height(stripH).tap { onSelect(day) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    UsosText(DateText.weekLetter(day, locale), UsosType.WeekLetter, UsosColors.TextSecondary, Modifier.height(14.dp))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(28.dp)
                            .then(if (isToday) Modifier.background(UsosColors.Text, CircleShape) else Modifier)
                            .then(if (isSel && !isToday) Modifier.border(1.dp, UsosColors.Text, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        UsosText(
                            day.dayOfMonth.toString(),
                            if (isToday) UsosType.WeekNumber.copy(fontWeight = FontWeight.Bold) else UsosType.WeekNumber,
                            if (isToday) UsosColors.OnAccent else if (isSel) UsosColors.Text else UsosColors.TextTertiary,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(3.dp).then(if (day in marked) Modifier.background(accent, CircleShape) else Modifier))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        UsosText(dayLabel(selected, today, locale), UsosType.Label, UsosColors.TextSecondary, Modifier.height(HeaderRowH))
        when {
            dayEvents.isEmpty() -> UsosText(stringResource(R.string.widget_cal_empty), UsosType.Label, UsosColors.TextTertiary)
            fit <= 0 -> Unit
            else -> {
                val rows = (if (dayEvents.size > fit && fit >= 2) fit - 1 else fit).coerceAtLeast(1)
                dayEvents.take(rows).forEach { e ->
                    EventRow(e, zone, nowMs, dim = selected == today && !e.allDay && e.endMs <= nowMs, onEvent)
                }
                val hidden = dayEvents.size - rows
                if (hidden > 0) {
                    UsosText(stringResource(R.string.widget_cal_more, hidden), UsosType.Label, UsosColors.TextTertiary, Modifier.height(EventRowH))
                }
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale): String = when (date) {
    today -> stringResource(R.string.widget_cal_today)
    today.plusDays(1) -> stringResource(R.string.widget_cal_tomorrow)
    else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).trimEnd('.') + " " + date.dayOfMonth + " " +
        date.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).trimEnd('.')
}

/** Evento como píldora: barra de acento si está en curso, hora en mono (o «todo el día») y título. */
@Composable
private fun EventRow(e: CalEvent, zone: ZoneId, nowMs: Long, dim: Boolean, onEvent: (CalEvent) -> Unit) {
    val ongoing = CalendarLogic.isOngoing(e, nowMs)
    val accent = LocalAccent.current
    val time = if (e.allDay) stringResource(R.string.widget_cal_allday) else
        java.time.Instant.ofEpochMilli(e.beginMs).atZone(zone).let { z -> "%02d:%02d".format(z.hour, z.minute) }
    val titleColor = if (dim) UsosColors.TextTertiary else UsosColors.Text
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.fillMaxWidth().height(EventRowH), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.fillMaxWidth().height(EventPillH).clip(shape).background(UsosColors.SurfaceRaised, shape)
                .then(if (ongoing) Modifier.border(1.dp, accent, shape) else Modifier)
                .tap { onEvent(e) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(if (ongoing) accent else if (dim) UsosColors.Border else UsosColors.BorderStrong))
            Spacer(Modifier.width(8.dp))
            UsosText(
                time, UsosType.Label,
                if (ongoing) accent else if (e.allDay || dim) UsosColors.TextTertiary else UsosColors.TextSecondary,
                Modifier.width(if (e.allDay) 64.dp else 42.dp), maxLines = 1,
            )
            UsosText(e.title.ifBlank { "-" }, UsosType.Label, titleColor, Modifier.weight(1f).padding(end = 8.dp), maxLines = 1)
        }
    }
}

/** Agenda agrupada por día (HOY / MAÑANA / fecha) con marcador del evento en curso. */
@Composable
private fun AgendaList(
    items: List<CalendarLogic.AgendaItem>, today: LocalDate, zone: ZoneId, locale: Locale, nowMs: Long, room: Dp,
    onEvent: (CalEvent) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(minOf(room, 40.dp).coerceAtLeast(0.dp)), contentAlignment = Alignment.CenterStart) {
            UsosText(stringResource(R.string.widget_cal_empty), UsosType.Label, UsosColors.TextSecondary)
        }
        return
    }
    val rows = remember(items) { CalendarLogic.agendaRows(items) }
    val visible = ArrayList<CalendarLogic.AgendaRow>()
    var used = 0.dp
    for (r in rows) {
        val h = if (r is CalendarLogic.AgendaRow.Header) HeaderRowH else EventRowH
        if (used + h > room) break
        visible.add(r); used += h
    }
    while (visible.lastOrNull() is CalendarLogic.AgendaRow.Header) visible.removeAt(visible.lastIndex)
    Column {
        visible.forEach { r ->
            when (r) {
                is CalendarLogic.AgendaRow.Header -> Row(Modifier.fillMaxWidth().height(HeaderRowH), verticalAlignment = Alignment.CenterVertically) {
                    UsosText(dayLabel(r.date, today, locale), UsosType.PanelState, UsosColors.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(UsosColors.Border))
                }
                is CalendarLogic.AgendaRow.Item -> EventRow(r.event, zone, nowMs, dim = false, onEvent)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Reloj de puntos
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun ClockWidget(width: Dp, height: Dp, tz: String) {
    val nowState = rememberNow()
    val zone = remember(tz) { runCatching { ZoneId.of(tz) }.getOrNull() ?: ZoneId.systemDefault() }
    nowState.value // se recompone cada minuto
    val time = ZonedDateTime.now(zone)
    val scale = minOf(1f, (width.value - 24f) / DotMatrix.widthDp("00:00"), (height.value - 24f - 22f) / DotMatrix.HEIGHT_DP)
        .coerceAtLeast(0.3f)
    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        DotText("%02d:%02d".format(time.hour, time.minute), scale, false, null)
        Spacer(Modifier.height(8.dp))
        UsosText(zoneLabel(tz).let { if (it.isEmpty()) stringResource(R.string.widget_clock_local) else it }, UsosType.Label, UsosColors.TextSecondary)
    }
}

/** «Europe/Madrid» -> «MADRID»; vacío = zona del sistema. */
fun zoneLabel(tz: String): String = if (tz.isEmpty()) "" else tz.substringAfterLast('/').replace('_', ' ').uppercase()

// ---------------------------------------------------------------------------------------------------------------
// Vistas previas del selector (datos de ejemplo, sin barras animadas)
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun OwnWidgetPreview(kind: WidgetKind, tall: Boolean, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val h = when { kind == WidgetKind.USOS_CALENDAR && tall -> 280.dp; kind == WidgetKind.USOS_CALENDAR -> 100.dp; else -> 100.dp }
    PieceSurface(modifier.fillMaxWidth().height(h)) {
        when (kind) {
            WidgetKind.USOS_MUSIC -> MusicContent("Usos", "Artist", false, false, null, null, null, null)
            WidgetKind.USOS_CALENDAR -> {
                val base = today.atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
                val sample = listOf(
                    CalEvent("Sample", base, base + 3_600_000L, false),
                    CalEvent("Sample", base + 86_400_000L, base + 90_000_000L, false),
                )
                CalendarView(CalMode.AUTO, tall, today, today, base + 1_800_000L, zone, sample, {}, {}, {})
            }
            WidgetKind.USOS_CLOCK -> ClockWidget(300.dp, h, "")
            WidgetKind.ANDROID -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Ajustes de la pieza
// ---------------------------------------------------------------------------------------------------------------

private val Zones = listOf(
    "", "UTC", "Europe/Madrid", "Europe/London", "Europe/Berlin", "America/New_York", "America/Chicago",
    "America/Los_Angeles", "America/Mexico_City", "America/Bogota", "America/Argentina/Buenos_Aires",
    "America/Sao_Paulo", "Asia/Dubai", "Asia/Kolkata", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney",
)

@Composable
fun OwnConfigPanel(widget: HostedWidget, onChange: (String) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    Column(
        Modifier.fillMaxSize().background(UsosColors.Background)
            .padding(top = top, start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin, bottom = bottom),
    ) {
        ScreenHeader(stringResource(R.string.bubble_reconfigure), onBack = onClose)
        Spacer(Modifier.height(12.dp))
        val bordered = OwnWidgets.bordered(widget.config)
        UsosText(stringResource(R.string.widget_config_border), UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill(stringResource(R.string.widget_border_on), filled = bordered, onClick = { onChange(OwnWidgets.with(widget.config, OwnWidgets.KEY_BORDER, null)) }, modifier = Modifier.weight(1f))
            UsosPill(stringResource(R.string.widget_border_off), filled = !bordered, onClick = { onChange(OwnWidgets.with(widget.config, OwnWidgets.KEY_BORDER, "off")) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        when (widget.widgetKind) {
            WidgetKind.USOS_CALENDAR -> {
                val cur = CalMode.fromKey(OwnWidgets.get(widget.config, OwnWidgets.KEY_MODE))
                UsosText(stringResource(R.string.widget_config_cal_mode), UsosType.Label, UsosColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalMode.entries.forEach { m ->
                        UsosPill(
                            stringResource(when (m) {
                                CalMode.AUTO -> R.string.widget_cal_mode_auto
                                CalMode.WEEK -> R.string.widget_cal_mode_week
                                CalMode.MONTH -> R.string.widget_cal_mode_month
                                CalMode.AGENDA -> R.string.widget_cal_mode_agenda
                            }),
                            filled = m == cur,
                            onClick = { onChange(OwnWidgets.with(widget.config, OwnWidgets.KEY_MODE, m.key)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            WidgetKind.USOS_MUSIC -> {
                val accent = OwnWidgets.get(widget.config, OwnWidgets.KEY_BARS) == "accent"
                UsosText(stringResource(R.string.widget_config_bars), UsosType.Label, UsosColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UsosPill(stringResource(R.string.widget_bars_white), filled = !accent, onClick = { onChange(OwnWidgets.with(widget.config, OwnWidgets.KEY_BARS, null)) }, modifier = Modifier.weight(1f))
                    UsosPill(stringResource(R.string.widget_bars_accent), filled = accent, onClick = { onChange(OwnWidgets.with(widget.config, OwnWidgets.KEY_BARS, "accent")) }, modifier = Modifier.weight(1f))
                }
            }
            WidgetKind.USOS_CLOCK -> {
                val cur = OwnWidgets.get(widget.config, OwnWidgets.KEY_TZ).orEmpty()
                UsosText(stringResource(R.string.widget_config_tz), UsosType.Label, UsosColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Zones.forEach { z ->
                        UsosPill(
                            if (z.isEmpty()) stringResource(R.string.widget_tz_system) else zoneLabel(z),
                            filled = z == cur,
                            onClick = { onChange(OwnWidgets.with(widget.config, OwnWidgets.KEY_TZ, z.ifEmpty { null })) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}
