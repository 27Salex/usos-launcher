package com.usos.launcher.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usos.launcher.data.icons.IconRepository
import com.usos.launcher.data.icons.LoadedIcon
import com.usos.launcher.data.sections.IconMode
import com.usos.launcher.data.sections.IconPrefs
import com.usos.launcher.domain.AppEntry

val LocalIconPrefs = compositionLocalOf { IconPrefs() }

/** Aviso de notificaciones: modo elegido y claves (`paquete/serial`) con notificaciones activas. */
class NotifBadges(
    val mode: com.usos.launcher.data.notify.NotifMode = com.usos.launcher.data.notify.NotifMode.NONE,
    val counts: Map<String, Int> = emptyMap(),
) {
    val active: Set<String> get() = counts.keys
    fun show(appKey: String, frozen: Boolean = false) =
        com.usos.launcher.data.notify.NotifBadge.show(mode, active, appKey, frozen)
    fun count(appKey: String): Int = counts[appKey] ?: 0
    /** Suma de las apps dadas (para el nombre de la sección); 0 si los avisos están apagados. */
    fun total(appKeys: Collection<String>): Int =
        if (mode == com.usos.launcher.data.notify.NotifMode.NONE) 0 else appKeys.sumOf { count(it) }
}

/** Bolita con el número de notificaciones ("9+" a partir de 10). */
@Composable
fun CountBubble(count: Int, color: Color, textColor: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .background(color, androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        UsosText(if (count > 9) "9+" else count.toString(), UsosType.SmallTag.copy(fontSize = 10.sp, lineHeight = 10.sp), textColor)
    }
}

val LocalNotifBadges = compositionLocalOf { NotifBadges() }
val LocalIcons = compositionLocalOf<IconRepository?> { null }

/** Icono de la app (null mientras carga o si el modo es Ninguno). */
@Composable
internal fun rememberIcon(app: AppEntry, mode: IconMode, invert: Boolean, size: Dp): LoadedIcon? {
    val repo = LocalIcons.current
    if (repo == null || mode == IconMode.NONE) return null
    val px = with(LocalDensity.current) { size.roundToPx() }
    val version by repo.version.collectAsState()
    var loaded by remember(app.key, mode, invert, px, version) { mutableStateOf(repo.peek(app, mode, invert, px)) }
    LaunchedEffect(app.key, mode, invert, px, version) {
        if (loaded == null) loaded = repo.load(app, mode, invert, px)
    }
    return loaded
}

/**
 * Cabecera de una pieza: código de 2 letras, icono, o ambos juntos si "Mostrar código" y caben.
 * [invert] = pieza clara con texto negro (la máscara gris se invierte).
 */
@Composable
fun TileHead(app: AppEntry, code: String, codeStyle: TextStyle, color: Color, iconSize: Dp, invert: Boolean) {
    val prefs = LocalIconPrefs.current
    if (prefs.mode == IconMode.NONE) {
        UsosText(code, codeStyle, color)
        return
    }
    val icon = rememberIcon(app, prefs.mode, invert, iconSize)
    val bmp = icon?.bitmap
    when {
        icon != null && bmp == null -> UsosText(code, codeStyle, color) // sin icono utilizable
        icon == null -> if (prefs.showCode) UsosText(code, codeStyle, color) else Spacer(Modifier.size(iconSize))
        else -> BoxWithConstraints {
            val codeWidth = (codeStyle.fontSize.value * 1.25f).dp
            val both = prefs.showCode && maxWidth >= iconSize + 6.dp + codeWidth
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = remember(bmp) { bmp!!.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    colorFilter = if (icon.tint) ColorFilter.tint(color) else null,
                )
                if (both) {
                    Spacer(Modifier.width(6.dp))
                    UsosText(code, codeStyle, color)
                }
            }
        }
    }
}

/** Icono de la app según el ajuste de iconos; [fallback] si no hay (modo Ninguno, cargando o sin icono utilizable). */
@Composable
fun AppIconOrNull(app: AppEntry, size: Dp, color: Color, fallback: @Composable () -> Unit) {
    val prefs = LocalIconPrefs.current
    val icon = rememberIcon(app, prefs.mode, false, size)
    val bmp = icon?.bitmap
    if (bmp == null) { fallback(); return }
    Image(
        bitmap = remember(bmp) { bmp.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(size),
        colorFilter = if (icon.tint) ColorFilter.tint(color) else null,
    )
}

/** Icono suelto (ficha de app). */
@Composable
fun AppIconImage(app: AppEntry, size: Dp, color: Color) {
    val prefs = LocalIconPrefs.current
    val icon = rememberIcon(app, prefs.mode, false, size)
    val bmp = icon?.bitmap ?: return
    Image(
        bitmap = remember(bmp) { bmp!!.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(size),
        colorFilter = if (icon.tint) ColorFilter.tint(color) else null,
    )
}
