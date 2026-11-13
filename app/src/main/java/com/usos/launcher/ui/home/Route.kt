package com.usos.launcher.ui.home

/** Pantalla superpuesta al inicio. Atras vuelve siempre a [Home]. */
sealed interface Route {
    data object Home : Route
    data object Onboarding : Route
    data object Settings : Route
    data object MosaicEditor : Route
    data class Detail(val key: String) : Route
}
