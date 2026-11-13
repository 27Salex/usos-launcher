package com.usos.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.usos.launcher.data.sections.ClockPrefs
import com.usos.launcher.data.sections.ClockType
import com.usos.launcher.data.sections.WeatherPrefs
import com.usos.launcher.data.weather.Weather
import com.usos.launcher.data.weather.WeatherIconKind
import com.usos.launcher.data.weather.WeatherIcons
import com.usos.launcher.data.weather.WeatherNow
import com.usos.launcher.ui.theme.JetBrainsMono
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.SpaceGrotesk
import com.usos.launcher.ui.theme.tap
import com.usos.launcher.ui.theme.usosTween
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import java.time.LocalDate
import java.time.LocalDateTime

/** Hora actual, actualizada en cada cambio de minuto, de hora o de zona, y al volver a primer plano. */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val context = LocalContext.current
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                now.value = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        now.value = LocalDateTime.now()
        onDispose { context.unregisterReceiver(receiver) }
    }
    LifecycleResumeEffect(Unit) {
        now.value = LocalDateTime.now()
        onPauseOrDispose { }
    }
    return now
}

/** Segundo actual, actualizado cada segundo solo mientras la pantalla está en primer plano y [enabled]. */
@Composable
private fun rememberSecondTick(enabled: Boolean): State<Long> {
    val tick = remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled, lifecycle) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val ms = System.currentTimeMillis()
                tick.longValue = ms / 1000
                delay(1000 - ms % 1000 + 5)
            }
        }
    }
    return tick
}

/**
 * Reloj (según [ClockPrefs]), tiempo y fecha. Solo este bloque se recompone cada segundo, y solo si hace falta
 * (segundos visibles o animación del separador).
 */
@Composable
fun ClockBlock(
    now: LocalDateTime,
    clock: ClockPrefs,
    weatherPrefs: WeatherPrefs,
    weather: WeatherNow?,
    onWeather: () -> Unit,
) {
    val animated = clock.animated && LocalAnimationsEnabled.current
    val breathes = animated && (clock.type == ClockType.DOTS || clock.type == ClockType.DIGITAL)
    val needsTick = clock.seconds || breathes
    val tick = rememberSecondTick(needsTick)
    val time = if (needsTick) { tick.value; LocalDateTime.now() } else now
    val sep: State<Float>? = if (breathes) {
        animateFloatAsState(if (time.second % 2 == 0) 1f else 0.4f, usosTween(true), label = "sep")
    } else null

    val shown = weather?.takeIf { weatherPrefs.on && Weather.isVisible(it.fetchedAt, System.currentTimeMillis()) }
    val weatherColor = if (weatherPrefs.accentColor) LocalAccent.current else UsosColors.Text

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (clock.type) {
            ClockType.DOTS -> {
                DotText("%02d:%02d".format(time.hour, time.minute), 1f, animated, sep)
                if (clock.seconds) {
                    Spacer(Modifier.height(10.dp))
                    DotText("%02d".format(time.second), 0.5f, false, null)
                }
            }
            ClockType.DIGITAL -> DigitalClock(time, clock.seconds, sep)
            ClockType.MONO -> UsosText(
                if (clock.seconds) "%02d:%02d:%02d".format(time.hour, time.minute, time.second)
                else "%02d:%02d".format(time.hour, time.minute),
                MonoClockStyle, UsosColors.Text,
            )
            ClockType.ANALOG -> AnalogClock(time, clock.seconds)
        }
        if (shown != null && weatherPrefs.belowClock) {
            Spacer(Modifier.height(8.dp))
            WeatherLabel(shown, weatherPrefs, weatherColor, onWeather)
        }
        Spacer(Modifier.height(if (shown != null && weatherPrefs.belowClock) 7.dp else 15.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            DateLabel(now.toLocalDate())
            if (shown != null && !weatherPrefs.belowClock) {
                Spacer(Modifier.width(12.dp))
                WeatherLabel(shown, weatherPrefs, weatherColor, onWeather, Modifier.weight(1f, fill = false))
            }
        }
    }
}

private val DigitalStyle = TextStyle(
    fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 72.sp, fontFeatureSettings = "tnum",
)
private val DigitalSecondsStyle = DigitalStyle.copy(fontSize = 28.sp)
private val MonoClockStyle = TextStyle(
    fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 0.1.em,
)

/** Digital grande en Space Grotesk; el separador "respira" si hay [sep]. */
@Composable
private fun DigitalClock(time: LocalDateTime, seconds: Boolean, sep: State<Float>?) {
    Row(verticalAlignment = Alignment.Top) {
        UsosText("%02d".format(time.hour), DigitalStyle, UsosColors.Text, Modifier.alignByBaseline())
        UsosText(
            ":", DigitalStyle, UsosColors.Text,
            Modifier.alignByBaseline().graphicsLayer { alpha = sep?.value ?: 1f },
        )
        UsosText("%02d".format(time.minute), DigitalStyle, UsosColors.Text, Modifier.alignByBaseline())
        if (seconds) {
            UsosText(
                ":%02d".format(time.second), DigitalSecondsStyle, UsosColors.TextSecondary,
                Modifier.alignByBaseline().padding(start = 6.dp),
            )
        }
    }
}

/** Analógico minimal: 12 puntos de hora y agujas finas (la de segundos, en acento). */
@Composable
private fun AnalogClock(time: LocalDateTime, seconds: Boolean) {
    val accent = LocalAccent.current
    val fg = UsosColors.Text
    val dim = UsosColors.TextSecondary
    val hour = (time.hour % 12 + time.minute / 60.0) * 30.0
    val minute = (time.minute + if (seconds) time.second / 60.0 else 0.0) * 6.0
    val second = time.second * 6.0
    Canvas(Modifier.size(160.dp)) {
        val c = center
        val r = size.minDimension / 2f
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            val major = i % 3 == 0
            val at = r - 6.dp.toPx()
            drawCircle(
                if (major) fg else dim, if (major) 3.dp.toPx() else 1.8.dp.toPx(),
                Offset(c.x + (cos(a) * at).toFloat(), c.y + (sin(a) * at).toFloat()),
            )
        }
        fun hand(deg: Double, len: Float, width: Float, color: Color) {
            val a = Math.toRadians(deg - 90.0)
            drawLine(
                color, c, Offset(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat()),
                strokeWidth = width, cap = StrokeCap.Round,
            )
        }
        hand(hour, r * 0.45f, 2.5.dp.toPx(), fg)
        hand(minute, r * 0.7f, 1.5.dp.toPx(), fg)
        if (seconds) hand(second, r * 0.8f, 1.dp.toPx(), accent)
        drawCircle(fg, 2.5.dp.toPx(), c)
    }
}

/**
 * Texto en matriz de puntos. [scale] reduce el tamaño (segundos). Con [fade], los dígitos que cambian
 * hacen un fundido de 250 ms; el separador usa [sepAlpha] si se da. Todo se lee en la fase de dibujo.
 */
@Composable
private fun DotText(text: String, scale: Float, fade: Boolean, sepAlpha: State<Float>?) {
    val chars = remember(text) { DotMatrix.layoutByChar(text) }
    val color = UsosColors.Text
    val prev = remember { arrayOf(text) }
    val progress = remember { mutableFloatStateOf(1f) }
    val mask = remember(text) {
        val old = prev[0]
        prev[0] = text
        val m = if (!fade || old.length != text.length) 0L
        else text.indices.fold(0L) { acc, i -> if (old[i] != text[i]) acc or (1L shl i) else acc }
        if (m != 0L) progress.floatValue = 0f
        m
    }
    LaunchedEffect(text) {
        if (mask != 0L) animate(0f, 1f, animationSpec = usosTween<Float>(true)) { v, _ -> progress.floatValue = v }
    }
    Canvas(Modifier.size((DotMatrix.widthDp(text) * scale).dp, (DotMatrix.HEIGHT_DP * scale).dp)) {
        val dot = DotMatrix.DOT_DP.dp.toPx() * scale
        chars.forEachIndexed { i, positions ->
            val alpha = when {
                text[i] == ':' -> sepAlpha?.value ?: 1f
                (mask shr i) and 1L != 0L -> progress.floatValue
                else -> 1f
            }
            val c = color.copy(alpha = alpha)
            positions.forEach { p ->
                drawCircle(c, dot / 2f, Offset(p.x.dp.toPx() * scale + dot / 2f, p.y.dp.toPx() * scale + dot / 2f))
            }
        }
    }
}

@Composable
fun DateLabel(date: LocalDate) {
    UsosText(
        text = SpanishDate.format(date),
        style = UsosType.Date.copy(textAlign = TextAlign.Center),
        color = UsosColors.TextSecondary,
    )
}

/** «14° · NUBES» con icono de puntos opcional; al tocarlo se abre la app del tiempo. */
@Composable
private fun WeatherLabel(
    w: WeatherNow,
    prefs: WeatherPrefs,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.tap { onClick() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (prefs.icon) {
            WeatherIcon(Weather.icon(w.code, w.isDay), color)
            Spacer(Modifier.width(6.dp))
        }
        UsosText(Weather.label(w, prefs.fahrenheit, prefs.text, prefs.minMax), UsosType.Label, color, Modifier.weight(1f, fill = false))
    }
}

/** Icono de 9x9 puntos, mismo lenguaje que el reloj. */
@Composable
private fun WeatherIcon(kind: WeatherIconKind, color: Color) {
    val pattern = remember(kind) { WeatherIcons.pattern(kind) }
    val step = 2.2f
    val dotDp = 1.6f
    Canvas(Modifier.size(((WeatherIcons.SIZE - 1) * step + dotDp).dp)) {
        val d = dotDp.dp.toPx()
        pattern.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                if (ch == '#') drawCircle(color, d / 2f, Offset((c * step).dp.toPx() + d / 2f, (r * step).dp.toPx() + d / 2f))
            }
        }
    }
}

/** Tira semanal L M X J V S D con hoy resaltado. */
@Composable
fun WeekStrip(today: LocalDate, modifier: Modifier = Modifier) {
    val week = SpanishDate.week(today)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, day ->
            val isToday = day == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                UsosText(SpanishDate.WEEK_LETTERS[i], UsosType.WeekLetter, UsosColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.size(30.dp).then(if (isToday) Modifier.background(UsosColors.Text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    UsosText(
                        text = day.dayOfMonth.toString(),
                        style = if (isToday) UsosType.WeekNumber.copy(fontWeight = FontWeight.Bold) else UsosType.WeekNumber,
                        color = if (isToday) UsosColors.OnAccent else UsosColors.TextTertiary,
                    )
                }
            }
        }
    }
}
