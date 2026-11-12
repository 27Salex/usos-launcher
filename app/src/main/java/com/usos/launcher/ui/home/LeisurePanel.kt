package com.usos.launcher.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.usos.launcher.data.leisure.LeisureLimits
import com.usos.launcher.data.leisure.LeisureStatus
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap

/** Panel de Ocio para los estados Congelado, Descongelando y Sin presupuesto (el Abierto es un mosaico). */
@Composable
fun LeisurePanel(
    status: LeisureStatus?,
    onThaw: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = UsosColors.BorderStrong,
                    cornerRadius = CornerRadius(UsosDimens.RadiusPiece.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            }
            .padding(16.dp),
    ) {
        when (status) {
            is LeisureStatus.Thawing -> Thawing(status, accent, onCancel)
            is LeisureStatus.NoBudget ->
                UsosText("Sin tiempo de ocio por hoy. Mañana se repone.", UsosType.Empty, UsosColors.Text, maxLines = 3)
            is LeisureStatus.Frozen -> Frozen(status, accent, onThaw)
            else -> Frozen(LeisureStatus.Frozen(0), accent, onThaw) // aun cargando
        }
    }
}

@Composable
private fun Frozen(status: LeisureStatus.Frozen, accent: androidx.compose.ui.graphics.Color, onThaw: (Int) -> Unit) {
    val options = status.durations
    var picked by remember { mutableIntStateOf(LeisureLimits.DEFAULT_DURATION_MIN) }
    val selected = if (picked in options) picked else LeisureLimits.defaultDuration(options)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UsosText("CONGELADO", UsosType.PanelState, accent)
        UsosText("QUEDAN ${status.remainingMin} MIN HOY", UsosType.Label, UsosColors.TextSecondary)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { min ->
            val on = min == selected
            val shape = RoundedCornerShape(UsosDimens.RadiusPill)
            Box(
                Modifier
                    .size(56.dp, 44.dp)
                    .background(if (on) UsosColors.Text else UsosColors.Background, shape)
                    .border(1.dp, if (on) UsosColors.Text else UsosColors.BorderStrong, shape)
                    .tap { picked = min },
                contentAlignment = Alignment.Center,
            ) {
                UsosText(min.toString(), UsosType.Pill, if (on) UsosColors.OnAccent else UsosColors.Text)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    val shape = RoundedCornerShape(UsosDimens.RadiusPill)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(UsosColors.Text, shape)
            .tap(enabled = options.isNotEmpty()) { onThaw(selected) },
        contentAlignment = Alignment.Center,
    ) {
        UsosText("DESCONGELAR", UsosType.Button, UsosColors.OnAccent)
    }
}

@Composable
private fun Thawing(status: LeisureStatus.Thawing, accent: androidx.compose.ui.graphics.Color, onCancel: () -> Unit) {
    val animations = LocalAnimationsEnabled.current
    UsosText("DESCONGELANDO · ${status.secondsLeft} S", UsosType.PanelState, accent)
    Spacer(Modifier.height(14.dp))
    val target = 1f - status.secondsLeft.toFloat() / status.totalSeconds.coerceAtLeast(1)
    val progress by animateFloatAsState(
        target,
        if (animations) tween(1000, easing = LinearEasing) else snap(),
        label = "thaw",
    )
    Box(Modifier.fillMaxWidth().height(3.dp).background(UsosColors.Border)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(accent))
    }
    Spacer(Modifier.height(14.dp))
    val shape = RoundedCornerShape(UsosDimens.RadiusPill)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, UsosColors.BorderStrong, shape)
            .tap { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        UsosText("CANCELAR", UsosType.Button, UsosColors.Text)
    }
}
