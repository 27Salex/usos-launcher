package com.usos.launcher.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class BubbleKind { RESIZE, SETTINGS, REMOVE }

/** Una «bolita» de acción flotante junto a la pieza seleccionada. */
class Bubble(val kind: BubbleKind, val description: String, val onClick: () -> Unit)

private val BubbleTouch = 44.dp
private val BubbleGap = 8.dp
private val BubbleMargin = 6.dp

/**
 * Fila de bolitas alrededor de la pieza [rect] (coordenadas del contenedor de tamaño [bounds]): encima si cabe en
 * [viewport], si no debajo y, si tampoco, sobre la propia pieza. Aparece con escala + fundido de 200 ms.
 */
@Composable
fun BubbleRow(rect: Rect, bounds: IntSize, viewport: Rect?, bubbles: List<Bubble>, appearKey: Any, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val animations = LocalAnimationsEnabled.current
    val progress = remember(appearKey) { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(appearKey) { if (animations) progress.animateTo(1f, tween(200)) }
    val touch = with(density) { BubbleTouch.toPx() }
    val gap = with(density) { BubbleGap.toPx() }
    val margin = with(density) { BubbleMargin.toPx() }
    val width = bubbles.size * touch + (bubbles.size - 1).coerceAtLeast(0) * gap
    val top = viewport?.top ?: 0f
    val bottom = viewport?.bottom ?: bounds.height.toFloat()
    val y = when {
        rect.top - margin - touch >= top -> rect.top - margin - touch
        rect.bottom + margin + touch <= bottom -> rect.bottom + margin
        else -> rect.center.y - touch / 2f
    }
    val x = (rect.center.x - width / 2f).coerceIn(margin, (bounds.width - width - margin).coerceAtLeast(margin))
    Row(
        modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .graphicsLayer {
                alpha = progress.value
                val sc = 0.6f + 0.4f * progress.value
                scaleX = sc; scaleY = sc
            },
        horizontalArrangement = Arrangement.spacedBy(BubbleGap),
    ) {
        bubbles.forEach { b ->
            Box(
                Modifier.size(BubbleTouch).semantics { contentDescription = b.description }.tap { b.onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(38.dp).background(UsosColors.Background, CircleShape)
                        .border(1.5.dp, if (b.kind == BubbleKind.REMOVE) UsosColors.Text else UsosColors.BorderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val accent = LocalAccent.current
                    Canvas(Modifier.size(18.dp)) { drawBubbleGlyph(b.kind, if (b.kind == BubbleKind.RESIZE) accent else UsosColors.Text) }
                }
            }
        }
    }
}

private fun DrawScope.drawBubbleGlyph(kind: BubbleKind, color: Color) {
    val w = 1.8.dp.toPx()
    val s = size.width
    val m = s / 2f
    when (kind) {
        BubbleKind.RESIZE -> {
            drawLine(color, Offset(1f, m), Offset(s - 1f, m), w, StrokeCap.Round)
            val h = s * 0.28f
            drawLine(color, Offset(1f, m), Offset(1f + h, m - h), w, StrokeCap.Round)
            drawLine(color, Offset(1f, m), Offset(1f + h, m + h), w, StrokeCap.Round)
            drawLine(color, Offset(s - 1f, m), Offset(s - 1f - h, m - h), w, StrokeCap.Round)
            drawLine(color, Offset(s - 1f, m), Offset(s - 1f - h, m + h), w, StrokeCap.Round)
        }
        BubbleKind.SETTINGS -> {
            val r = 1.8.dp.toPx()
            for (i in 0..2) drawCircle(color, r, Offset(r + i * (s - 2 * r) / 2f, m))
        }
        BubbleKind.REMOVE -> {
            val a = s * 0.18f
            drawLine(color, Offset(a, a), Offset(s - a, s - a), w, StrokeCap.Round)
            drawLine(color, Offset(s - a, a), Offset(a, s - a), w, StrokeCap.Round)
        }
    }
}

/** Qué ejes mueve un tirador. */
enum class ResizeAxes(val x: Boolean, val y: Boolean) { WIDTH(true, false), HEIGHT(false, true), BOTH(true, true) }

/**
 * Tiradores de tamaño de la pieza [rect] (coordenadas del contenedor): borde derecho (ancho), inferior (alto) y
 * esquina (ambos). Deben quedar fijos mientras dura el gesto (el llamador congela [rect]); [onDelta] recibe el
 * desplazamiento total del dedo desde el DOWN y [onEnd] true si el gesto terminó levantando el dedo.
 */
@Composable
fun ResizeHandles(
    rect: Rect,
    onStart: (ResizeAxes) -> Unit,
    onDelta: (dx: Float, dy: Float, axes: ResizeAxes) -> Unit,
    onEnd: (completed: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val half = with(density) { BubbleTouch.toPx() } / 2f
    val start by rememberUpdatedState(onStart)
    val delta by rememberUpdatedState(onDelta)
    val end by rememberUpdatedState(onEnd)
    @Composable
    fun Handle(axes: ResizeAxes, cx: Float, cy: Float) {
        val accent = LocalAccent.current
        Box(
            Modifier
                .offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
                .size(BubbleTouch)
                .pointerInput(axes) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        start(axes)
                        var completed = false
                        try {
                            while (true) {
                                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (c.changedToUpIgnoreConsumed()) { c.consume(); completed = true; break }
                                if (!c.pressed) break
                                c.consume()
                                delta(c.position.x - down.position.x, c.position.y - down.position.y, axes)
                            }
                        } finally {
                            end(completed) // también si el gesto se cancela por una excepción
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when (axes) {
                ResizeAxes.WIDTH -> Box(Modifier.size(5.dp, 30.dp).background(accent, CircleShape))
                ResizeAxes.HEIGHT -> Box(Modifier.size(30.dp, 5.dp).background(accent, CircleShape))
                ResizeAxes.BOTH -> Box(Modifier.size(16.dp).background(accent, CircleShape).border(2.dp, UsosColors.Background, CircleShape))
            }
        }
    }
    Handle(ResizeAxes.WIDTH, rect.right, rect.center.y)
    Handle(ResizeAxes.HEIGHT, rect.center.x, rect.bottom)
    Handle(ResizeAxes.BOTH, rect.right, rect.bottom)
}
