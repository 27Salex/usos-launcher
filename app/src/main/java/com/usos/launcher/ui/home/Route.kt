package com.usos.launcher.ui.home

/** Pantalla superpuesta al inicio. Atras vuelve siempre a [Home]. */
sealed interface Route {
    data object Home : Route
    data object Onboarding : Route
    /** [category]: subpantalla inicial de Ajustes (nombre de [SettingsCat]); null = lista de categorias. */
    data class Settings(val category: String? = null) : Route
    data class Detail(val key: String) : Route
}

/** Categorias de Ajustes que se pueden abrir directamente (coinciden con la enumeracion interna de SettingsScreen). */
object SettingsCat {
    const val WIDGETS = "WIDGETS"
    const val NEWS = "NEWS"
}
