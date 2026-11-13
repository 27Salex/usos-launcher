package com.usos.launcher.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

val LocalAccent = compositionLocalOf { UsosColors.DefaultAccent }

/** Negro o blanco segun la luminancia del acento (WCAG): texto e iconos sobre piezas de acento. */
val LocalOnAccent = compositionLocalOf { UsosColors.OnAccent }

/** false si el sistema tiene las animaciones reducidas (escala de duración = 0). */
val LocalAnimationsEnabled = compositionLocalOf { true }

@Composable
fun UsosTheme(accent: Color, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(animationsEnabled(context.contentResolver)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = animationsEnabled(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        enabled = animationsEnabled(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalOnAccent provides accent.onColor(),
        LocalAnimationsEnabled provides enabled,
        content = content,
    )
}

private fun animationsEnabled(resolver: android.content.ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/** Texto con estilo y color de los tokens. */
@Composable
fun UsosText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Toque (y pulsación larga opcional) sin ripple ni indicación de color. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
