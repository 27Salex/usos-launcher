package com.usos.launcher.ui.home

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usos.launcher.R
import com.usos.launcher.UsosApp
import com.usos.launcher.data.sections.NightClock
import com.usos.launcher.data.sections.NightstandPrefs
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.SpaceGrotesk
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosTheme
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.onColor
import com.usos.launcher.ui.theme.tap
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields

/**
 * Modo mesita: reloj grande en horizontal (flip, digital o puntos), mini calendario opcional, próxima alarma.
 * Pantalla siempre encendida mientras está abierto; se abre solo a mano (Ajustes → Reloj o pulsación larga en el reloj).
 */
class NightstandActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val store = (application as UsosApp).container.settingsStore
        setContent {
            val accentArgb by store.accent.collectAsStateWithLifecycle(UsosColors.DefaultAccent.toArgb())
            val prefs by store.nightstand.collectAsStateWithLifecycle(null)
            val p = prefs ?: return@setContent
            LaunchedEffect(p.dim) {
                window.attributes = window.attributes.apply {
                    screenBrightness = if (p.dim) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            UsosTheme(Color(accentArgb)) { Nightstand(p, onExit = ::finish) }
        }
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, NightstandActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
private fun Nightstand(prefs: NightstandPrefs, onExit: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000 + 5)
        }
    }
    var controls by remember { mutableStateOf(false) }
    LaunchedEffect(controls) { if (controls) { delay(4000); controls = false } }
    // Contra el quemado OLED: el contenido se desplaza unos dp cada minuto.
    val shiftX = ((now.minute % 5) - 2) * 3
    val shiftY = ((now.minute / 5 % 3) - 1) * 3

    Box(Modifier.fillMaxSize().background(Color.Black).tap { controls = !controls }) {
        Row(
            Modifier.fillMaxSize().offset(shiftX.dp, shiftY.dp).padding(horizontal = 40.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BigClock(prefs, now, maxWidth)
                }
                Spacer(Modifier.height(18.dp))
                val locale = LocalConfiguration.current.locales[0]
                UsosText(
                    DateText.format(now.toLocalDate(), locale, stringResource(R.string.clock_date_format)),
                    UsosType.Date.copy(textAlign = TextAlign.Center), UsosColors.TextSecondary,
                )
                nextAlarm()?.let {
                    Spacer(Modifier.height(6.dp))
                    UsosText(stringResource(R.string.night_alarm, it), UsosType.Date, LocalAccent.current)
                }
            }
            if (prefs.calendar) {
                Spacer(Modifier.width(36.dp))
                MiniMonth(now.toLocalDate())
            }
        }
        AnimatedVisibility(controls, Modifier.align(Alignment.TopEnd).padding(20.dp), enter = fadeIn(), exit = fadeOut()) {
            UsosPill(stringResource(R.string.night_exit), filled = false, onClick = onExit)
        }
    }
}

@Composable
private fun BigClock(prefs: NightstandPrefs, now: LocalDateTime, width: Dp) {
    val hm = "%02d%02d".format(now.hour, now.minute)
    when (prefs.clock) {
        NightClock.FLIP -> {
            // 4 tarjetas + hueco central (+ 2 pequeñas si hay segundos); alto = 1.4 x ancho.
            val units = if (prefs.seconds) 5.6f else 4.5f
            val w = min(width / units, 170.dp)
            val h = w * 1.4f
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                FlipDigit(hm[0], w, h); Spacer(Modifier.width(w * 0.08f)); FlipDigit(hm[1], w, h)
                Spacer(Modifier.width(w * 0.3f))
                FlipDigit(hm[2], w, h); Spacer(Modifier.width(w * 0.08f)); FlipDigit(hm[3], w, h)
                if (prefs.seconds) {
                    val s = "%02d".format(now.second)
                    Spacer(Modifier.width(w * 0.2f))
                    FlipDigit(s[0], w * 0.45f, h * 0.45f); Spacer(Modifier.width(w * 0.05f)); FlipDigit(s[1], w * 0.45f, h * 0.45f)
                }
            }
        }
        NightClock.DIGITAL -> {
            val size = with(LocalDensity.current) { min(width / if (prefs.seconds) 4.6f else 3.1f, 190.dp).toSp() }
            val style = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = size, fontFeatureSettings = "tnum")
            Row(verticalAlignment = Alignment.Bottom) {
                UsosText("%02d:%02d".format(now.hour, now.minute), style, UsosColors.Text)
                if (prefs.seconds) {
                    UsosText(":%02d".format(now.second), style.copy(fontSize = size * 0.4f), UsosColors.TextSecondary, Modifier.padding(start = 8.dp, bottom = 12.dp))
                }
            }
        }
        NightClock.DOTS -> {
            val text = if (prefs.seconds) "%02d:%02d:%02d".format(now.hour, now.minute, now.second) else "%02d:%02d".format(now.hour, now.minute)
            val scale = (width.value / DotMatrix.widthDp(text)).coerceAtMost(3.2f)
            DotText(text, scale, fade = true, sepAlpha = null)
        }
    }
}

private val FlipCard = Color(0xFF151515)
private val FlipText = Color(0xFFEDEDED)

/**
 * Tarjeta de reloj flip: al cambiar, la mitad superior del número viejo cae (rotationX) y descubre la nueva;
 * luego la mitad inferior nueva baja y tapa la vieja. 600 ms.
 */
@Composable
private fun FlipDigit(digit: Char, w: Dp, h: Dp) {
    var current by remember { mutableStateOf(digit) }
    var previous by remember { mutableStateOf(digit) }
    val anim = remember { Animatable(1f) }
    LaunchedEffect(digit) {
        if (digit != current) {
            previous = current
            current = digit
            anim.snapTo(0f)
            anim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }
    val p = anim.value
    val density = LocalDensity.current
    val style = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
        fontSize = with(density) { (h * 0.72f).toSp() }, textAlign = TextAlign.Center,
    )
    val r = w * 0.12f
    Box(Modifier.size(w, h)) {
        Half(current, top = true, w, h, r, style, Modifier.align(Alignment.TopCenter))
        Half(if (p < 0.5f) previous else current, top = false, w, h, r, style, Modifier.align(Alignment.BottomCenter))
        if (p < 0.5f) {
            Half(
                previous, top = true, w, h, r, style,
                Modifier.align(Alignment.TopCenter).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationX = -180f * p
                    cameraDistance = 16f * density.density
                },
            )
        } else {
            Half(
                current, top = false, w, h, r, style,
                Modifier.align(Alignment.BottomCenter).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    rotationX = 180f * (1f - p)
                    cameraDistance = 16f * density.density
                },
            )
        }
        // Ranura central entre las dos mitades.
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(maxOf(2.dp, h * 0.012f)).background(Color.Black))
    }
}

@Composable
private fun Half(c: Char, top: Boolean, w: Dp, h: Dp, r: Dp, style: TextStyle, modifier: Modifier) {
    val shape = if (top) RoundedCornerShape(topStart = r, topEnd = r) else RoundedCornerShape(bottomStart = r, bottomEnd = r)
    Box(modifier.size(w, h / 2).clip(shape).background(FlipCard)) {
        Box(
            Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(h)
                .offset(y = if (top) 0.dp else -h / 2),
            contentAlignment = Alignment.Center,
        ) { UsosText(c.toString(), style, FlipText) }
    }
}

/** Mes actual en rejilla, empezando por el primer día de la semana del idioma; hoy en acento. */
@Composable
private fun MiniMonth(today: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val first = WeekFields.of(locale).firstDayOfWeek
    val start = today.withDayOfMonth(1)
    val lead = (start.dayOfWeek.value - first.value + 7) % 7
    val days = today.lengthOfMonth()
    val accent = LocalAccent.current
    val cell = 30.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        UsosText(
            today.month.getDisplayName(JTextStyle.FULL_STANDALONE, locale).uppercase(locale) + " " + today.year,
            UsosType.Date, UsosColors.Text,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            (0 until 7).forEach { i ->
                val d = DayOfWeek.of((first.value - 1 + i) % 7 + 1)
                Box(Modifier.size(cell, 22.dp), contentAlignment = Alignment.Center) {
                    UsosText(d.getDisplayName(JTextStyle.NARROW_STANDALONE, locale).uppercase(locale), UsosType.Label, UsosColors.TextSecondary)
                }
            }
        }
        val rows = (lead + days + 6) / 7
        (0 until rows).forEach { row ->
            Row {
                (0 until 7).forEach { col ->
                    val day = row * 7 + col - lead + 1
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val isToday = day == today.dayOfMonth
                            if (isToday) Box(Modifier.size(cell - 4.dp).background(accent, CircleShape))
                            UsosText(
                                day.toString(), UsosType.WeekNumber,
                                when {
                                    isToday -> accent.onColor()
                                    day < today.dayOfMonth -> UsosColors.TextSecondary.copy(alpha = 0.5f)
                                    else -> UsosColors.TextTertiary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Próxima alarma del sistema en HH:mm, o null. */
@Composable
private fun nextAlarm(): String? {
    val context = LocalContext.current
    val t = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: return null
    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault())
    if (dt.isAfter(LocalDateTime.now().plusHours(24))) return null
    return "%02d:%02d".format(dt.hour, dt.minute)
}
