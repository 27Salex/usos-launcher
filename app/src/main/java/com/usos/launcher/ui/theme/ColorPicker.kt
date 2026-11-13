package com.usos.launcher.ui.theme

import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val Hues = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color(ColorMath.hsvToArgb(it, 1f, 1f)) }

/**
 * Selector de color propio: cuadro saturación/valor + barra de tono (dibujados con Canvas), campo HEX y muestras
 * recientes. Los degradados solo existen aquí. [onCommit] se llama al soltar el dedo o al escribir un HEX válido.
 */
@Composable
fun ColorPicker(initial: Int, recent: List<Int>, onCommit: (Int) -> Unit, modifier: Modifier = Modifier) {
    val start = remember { ColorMath.argbToHsv(initial) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(ColorMath.toHex(initial)) }
    val commit by rememberUpdatedState(onCommit)
    val current = ColorMath.hsvToArgb(hue, sat, value)

    fun setFrom(argb: Int) {
        val h = ColorMath.argbToHsv(argb)
        hue = h[0]; sat = h[1]; value = h[2]
        hex = ColorMath.toHex(argb)
    }

    // Un cambio externo del color (no el que acabamos de confirmar nosotros) reinicia el selector;
    // así confirmar no devuelve el tono a 0 cuando S o V valen 0.
    LaunchedEffect(initial) { if (initial != ColorMath.hsvToArgb(hue, sat, value)) setFrom(initial) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Cuadro saturación (x) / valor (y).
        Canvas(
            Modifier.fillMaxWidth().height(170.dp)
                .dragPosition(
                    onPos = { o, sz ->
                        sat = (o.x / sz.width).coerceIn(0f, 1f)
                        value = 1f - (o.y / sz.height).coerceIn(0f, 1f)
                        hex = ColorMath.toHex(ColorMath.hsvToArgb(hue, sat, value))
                    },
                    onEnd = { commit(ColorMath.hsvToArgb(hue, sat, value)) },
                ),
        ) {
            val pure = Color(ColorMath.hsvToArgb(hue, 1f, 1f))
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)), size = size)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), size = size)
            val c = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color.Black, 9.dp.toPx(), c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, 8.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }

        // Barra de tono.
        Canvas(
            Modifier.fillMaxWidth().height(32.dp)
                .dragPosition(
                    onPos = { o, sz ->
                        hue = (o.x / sz.width).coerceIn(0f, 1f) * 360f
                        hex = ColorMath.toHex(ColorMath.hsvToArgb(hue, sat, value))
                    },
                    onEnd = { commit(ColorMath.hsvToArgb(hue, sat, value)) },
                ),
        ) {
            drawRect(Brush.horizontalGradient(Hues), size = Size(size.width, size.height))
            val x = hue / 360f * size.width
            drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }

        // HEX editable y vista previa.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(UsosDimens.RadiusPill)
            Box(
                Modifier.size(UsosDimens.MinTouch).background(Color(current), CircleShape)
                    .border(1.dp, UsosColors.BorderStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) { UsosText("Aa", UsosType.Label, Color(ColorMath.onColor(current))) }
            Box(
                Modifier.weight(1f).height(UsosDimens.MinTouch)
                    .background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsosText("#", UsosType.Pill, UsosColors.TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = hex,
                        onValueChange = { raw ->
                            val t = raw.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                            hex = t
                            if (t.length == 6) ColorMath.parseHex(t)?.let { argb ->
                                val h = ColorMath.argbToHsv(argb)
                                hue = h[0]; sat = h[1]; value = h[2]
                                commit(argb)
                            }
                        },
                        singleLine = true,
                        textStyle = UsosType.Pill.copy(color = UsosColors.Text),
                        cursorBrush = SolidColor(UsosColors.Text),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { ColorMath.parseHex(hex)?.let { setFrom(it); commit(it) } }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            UsosText(stringResource(R.string.color_recent), UsosType.Label, UsosColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.take(8).forEach { c ->
                    Box(
                        Modifier.size(32.dp).background(Color(c), CircleShape)
                            .border(1.dp, if (c == current) UsosColors.Text else UsosColors.BorderStrong, CircleShape)
                            .tap { setFrom(c); commit(c) },
                    )
                }
            }
        }
        Spacer(Modifier.padding(0.dp))
    }
}

/**
 * Toque o arrastre sobre un área: [onPos] con cada posición (también el DOWN) y [onEnd] al soltar. Consume los
 * eventos, así el scroll de un padre no roba el gesto.
 */
private fun Modifier.dragPosition(onPos: (Offset, IntSize) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            onPos(down.position, size)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) { change.consume(); break }
                if (!change.pressed) break
                onPos(change.position, size)
                change.consume()
            }
            onEnd()
        }
    }
