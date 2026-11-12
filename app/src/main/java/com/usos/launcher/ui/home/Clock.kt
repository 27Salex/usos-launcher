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

/** Reloj de puntos HH:MM, en blanco, solo con los puntos encendidos. */
@Composable
fun DotClock(time: LocalDateTime, modifier: Modifier = Modifier) {
    val text = "%02d:%02d".format(time.hour, time.minute)
    val positions = remember(text) { DotMatrix.layout(text) }
    val color = UsosColors.Text
    Canvas(modifier.size(DotMatrix.widthDp(text).dp, DotMatrix.HEIGHT_DP.dp)) {
        val dot = DotMatrix.DOT_DP.dp.toPx()
        positions.forEach { p ->
            drawCircle(
                color = color,
                radius = dot / 2f,
                center = Offset(p.x.dp.toPx() + dot / 2f, p.y.dp.toPx() + dot / 2f),
            )
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
