package com.usos.launcher.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.usos.launcher.data.background.BackgroundImage
import com.usos.launcher.data.sections.BackgroundMode
import com.usos.launcher.data.sections.BackgroundPrefs

/**
 * Fondo detrás de inicio, Widgets y Noticias. Negro por defecto; las superficies (#111111) no cambian.
 * SYSTEM no pinta nada: la ventana es transparente y se ve el fondo del sistema.
 */
@Composable
fun AppBackground(prefs: BackgroundPrefs, modifier: Modifier = Modifier.fillMaxSize()) {
    when (prefs.mode) {
        BackgroundMode.BLACK -> Box(modifier.background(UsosColors.Background))
        BackgroundMode.COLOR -> Box(modifier.background(Color(prefs.color)))
        BackgroundMode.SYSTEM -> Box(modifier)
        BackgroundMode.IMAGE -> {
            val context = LocalContext.current
            val bmp by produceState<ImageBitmap?>(null, prefs.imageVersion) {
                value = BackgroundImage.load(context)?.asImageBitmap()
            }
            Box(modifier.background(UsosColors.Background).clipToBounds()) {
                bmp?.let {
                    Image(
                        it, null,
                        Modifier.fillMaxSize()
                            .then(if (prefs.blur) Modifier.blur(24.dp, BlurredEdgeTreatment.Rectangle).graphicsLayer { scaleX = 1.1f; scaleY = 1.1f } else Modifier),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.dim / 100f)))
                }
            }
        }
    }
}
