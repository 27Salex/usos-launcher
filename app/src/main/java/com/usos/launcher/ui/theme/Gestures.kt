package com.usos.launcher.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/** Borde discontinuo (modo edición). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
    )
}

/**
 * Toque y pulsación larga (por defecto 500 ms). La pulsación larga solo salta si el dedo no se mueve más de
 * 2x el umbral de arrastre del sistema ni lo consume un padre (scroll). Sin ripple.
 */
@Composable
fun Modifier.pressGestures(
    longMs: Long = 500,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop * 2f
    return pointerInput(onLongPress != null) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var clicked = false
            val result = withTimeoutOrNull(if (onLongPress != null) longMs else Long.MAX_VALUE / 4) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val c = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Unit
                    if (c.isConsumed) return@withTimeoutOrNull Unit
                    if (c.changedToUp()) { clicked = true; return@withTimeoutOrNull Unit }
                    if ((c.position - down.position).getDistance() > slop) return@withTimeoutOrNull Unit
                }
            }
            if (result == null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress?.invoke()
                waitForUpOrCancellation()?.consume()
            } else if (clicked) {
                onClick()
            }
        }
    }
}

/**
 * Detecta un deslizamiento vertical sin consumirlo (el scroll de los hijos sigue funcionando). [accept] se evalúa
 * en el DOWN (posición local); si devuelve false el gesto se ignora.
 */
fun Modifier.swipeVertical(
    enabled: Boolean = true,
    thresholdDp: Float = 72f,
    accept: (Offset) -> Boolean = { true },
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
): Modifier = if (!enabled) this else pointerInput(onUp != null, onDown != null) {
    val threshold = thresholdDp * density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!accept(down.position)) return@awaitEachGesture
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) return@awaitEachGesture
            val c = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (c.changedToUp()) return@awaitEachGesture
            val d = c.position - down.position
            if (kotlin.math.abs(d.y) > threshold && kotlin.math.abs(d.y) > 1.6f * kotlin.math.abs(d.x)) {
                if (d.y < 0) onUp?.invoke() else onDown?.invoke()
                return@awaitEachGesture
            }
            if (kotlin.math.abs(d.x) > threshold) return@awaitEachGesture
        }
    }
}

/** Botón discreto de ajustes: tres puntos grises, zona táctil de 44 dp. */
@Composable
fun SettingsGlyph(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(UsosDimens.MinTouch).tap { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp, 4.dp)) {
            val r = 2.dp.toPx()
            for (i in 0..2) drawCircle(UsosColors.TextSecondary, r, Offset(r + i * (size.width - 2 * r) / 2f, size.height / 2))
        }
    }
}
