package com.usos.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.usos.launcher.R
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap

/** Pregunta al salir de la edición si quitar de inicio las [hiddenCount] piezas que no caben. Atrás = seguir editando. */
@Composable
fun ConfirmEditDialog(hiddenCount: Int, onConfirm: () -> Unit, onKeepEditing: () -> Unit) {
    BackHandler { onKeepEditing() }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    // Fondo oscurecido que captura los toques (tocar fuera = seguir editando).
    Box(
        Modifier.fillMaxSize().background(UsosColors.Background.copy(alpha = 0.7f)).tap { onKeepEditing() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = UsosDimens.ScreenMargin).fillMaxWidth()
                .background(UsosColors.Surface, shape).border(1.dp, UsosColors.BorderStrong, shape)
                .tap { }.padding(20.dp),
        ) {
            UsosText(stringResource(R.string.home_edit_confirm_title), UsosType.PanelState, UsosColors.Text)
            Spacer(Modifier.height(10.dp))
            UsosText(stringResource(R.string.home_edit_confirm_body, hiddenCount), UsosType.Label, UsosColors.TextSecondary, maxLines = 4)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsosPill(stringResource(R.string.home_edit_confirm), filled = true, onClick = onConfirm)
                UsosPill(stringResource(R.string.home_edit_keep), filled = false, onClick = onKeepEditing)
            }
        }
    }
}
