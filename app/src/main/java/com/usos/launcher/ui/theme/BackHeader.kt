package com.usos.launcher.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.usos.launcher.R

/** Botón «←»: flecha fina gris dibujada a mano, zona táctil de 44 dp. Hace lo mismo que el gesto atrás. */
@Composable
fun BackGlyph(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.back)
    Box(
        modifier.size(UsosDimens.MinTouch).semantics { contentDescription = description }.tap { onClick() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.size(20.dp, 16.dp)) {
            val w = 1.5.dp.toPx()
            val y = size.height / 2f
            drawLine(UsosColors.TextSecondary, Offset(w / 2, y), Offset(size.width, y), w, StrokeCap.Round)
            drawLine(UsosColors.TextSecondary, Offset(w / 2, y), Offset(size.height / 2f, w / 2), w, StrokeCap.Round)
            drawLine(UsosColors.TextSecondary, Offset(w / 2, y), Offset(size.height / 2f, size.height - w / 2), w, StrokeCap.Round)
        }
    }
}

/** Cabecera común de las pantallas superpuestas: «←» a la izquierda del título y, opcional, contenido a la derecha. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = UsosType.PanelTitle,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BackGlyph(onBack)
        UsosText(title, style, UsosColors.Text, Modifier.weight(1f), maxLines = 2)
        trailing?.invoke(this)
    }
}
