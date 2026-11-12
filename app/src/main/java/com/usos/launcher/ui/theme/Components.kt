package com.usos.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Interruptor en blanco y negro; encendido en acento. Zona tactil de 44 dp. */
@Composable
fun UsosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val accent = LocalAccent.current
    Box(
        Modifier.size(width = 56.dp, height = UsosDimens.MinTouch).tap(enabled = enabled) { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        val track = RoundedCornerShape(50)
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .background(if (checked) accent else UsosColors.Surface, track)
                .border(1.dp, if (checked) accent else UsosColors.BorderStrong, track)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(18.dp)
                    .background(if (checked) UsosColors.OnAccent else UsosColors.TextSecondary, CircleShape),
            )
        }
    }
}

/** Boton en pildora: relleno blanco (principal) o solo borde. */
@Composable
fun UsosPill(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPill)
    Box(
        modifier
            .height(UsosDimens.MinTouch)
            .background(if (filled) UsosColors.Text else UsosColors.Background, shape)
            .border(1.dp, if (filled) UsosColors.Text else UsosColors.BorderStrong, shape)
            .tap(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        UsosText(text, UsosType.Button, if (filled) UsosColors.OnAccent else UsosColors.Text)
    }
}

/** Fila de etiqueta a la izquierda y contenido a la derecha. */
@Composable
fun UsosRow(label: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().widthIn(min = 0.dp).height(UsosDimens.MinTouch + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        UsosText(label, UsosType.Label, UsosColors.Text, Modifier.weight(1f, fill = false))
        trailing()
    }
}
