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
 * en el DOWN (posición local); si devuelve false el gesto se ignora. El eje se decide una sola vez, al superar el
 * touchSlop, por el eje dominante: un gesto horizontal se ignora por completo.
 */
fun Modifier.swipeVertical(
    enabled: Boolean = true,
    thresholdDp: Float = 72f,
    accept: (Offset) -> Boolean = { true },
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
): Modifier = if (!enabled) this else pointerInput(onUp != null, onDown != null) {
    val threshold = thresholdDp * density
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!accept(down.position)) return@awaitEachGesture
        var vertical = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) return@awaitEachGesture
            val c = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (c.changedToUp()) return@awaitEachGesture
            val d = c.position - down.position
            val ax = kotlin.math.abs(d.x)
            val ay = kotlin.math.abs(d.y)
            if (!vertical) {
                if (maxOf(ax, ay) <= slop) continue
                if (ax >= ay) return@awaitEachGesture // eje horizontal: no es del cajón
                vertical = true
            }
            if (ay > threshold) {
                if (d.y < 0) onUp?.invoke() else onDown?.invoke()
                return@awaitEachGesture
            }
        }
    }
}

/**
 * Avisa de si el gesto en curso es vertical (eje dominante tras superar ~el touchSlop), sin consumir nada. Sirve
 * para bloquear el pager horizontal mientras dura un gesto vertical. [onVertical] vuelve a false al soltar.
 */
fun Modifier.verticalGestureLock(onVertical: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop * 0.6f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var decided = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                if (c.changedToUp() || !c.pressed) break
                if (!decided) {
                    val d = c.position - down.position
                    val ax = kotlin.math.abs(d.x)
                    val ay = kotlin.math.abs(d.y)
                    if (maxOf(ax, ay) > slop) {
                        decided = true
                        if (ay > ax) onVertical(true)
                    }
                }
            }
        } finally {
            onVertical(false)
        }
    }
}

/**
 * Un toque (sin arrastre) fuera de [inside] (rect en coordenadas de raíz, ampliado 56 dp) llama a [onOutside].
 * [origin] = posición de este nodo en la raíz. Observa en el pase inicial sin consumir, así no interfiere.
 */
fun Modifier.tapOutside(
    enabled: Boolean,
    inside: () -> androidx.compose.ui.geometry.Rect?,
    origin: () -> Offset,
    onOutside: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val reach = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val rect = inside()?.inflate(reach) ?: return@awaitEachGesture
        if (rect.contains(down.position + origin())) return@awaitEachGesture
        while (true) {
            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ?: return@awaitEachGesture
            if ((c.position - down.position).getDistance() > slop) return@awaitEachGesture
            if (c.changedToUp()) { onOutside(); return@awaitEachGesture }
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
