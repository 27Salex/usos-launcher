package com.usos.launcher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Tokens de color. Ningún componente define colores propios. */
object UsosColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF111111)
    /** Fondo de elementos dentro de una superficie (eventos del calendario). */
    val SurfaceRaised = Color(0xFF1E1E1E)
    val Border = Color(0xFF2A2A2A)
    val BorderStrong = Color(0xFF4A4A4A)
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9A9A9A)
    val TextTertiary = Color(0xFFB0B0B0)
    val LeisureFrozen = Color(0xFF8A8A8A)
    val OnAccent = Color(0xFF000000)
    val DefaultAccent = Color(0xFFFF6A3D)

    /** Alternativas de acento configurables. */
    val AccentChoices = listOf(Color(0xFFFF6A3D), Color(0xFFD7FF3F), Color(0xFF7FD4FF), Color(0xFFFFFFFF))
}

object UsosDimens {
    val RadiusPiece = 18.dp
    val RadiusSearch = 24.dp
    val RadiusPill = 22.dp
    val RadiusThumb = 12.dp
    val MinTouch = 44.dp
    val ScreenMargin = 24.dp
}

object UsosMotion {
    const val DurationMs = 250
    val Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

/** Animación estándar de 250 ms; se omite (snap) si el sistema tiene las animaciones reducidas. */
fun <T> usosTween(animationsEnabled: Boolean): FiniteAnimationSpec<T> =
    if (animationsEnabled) tween(UsosMotion.DurationMs, easing = UsosMotion.Easing) else snap()
