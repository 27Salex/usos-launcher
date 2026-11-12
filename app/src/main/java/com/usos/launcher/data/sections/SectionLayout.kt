package com.usos.launcher.data.sections

import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section

data class PlacedApp(val app: AppEntry, val config: AppConfig) {
    val code: String get() = config.code?.takeIf { it.isNotBlank() } ?: app.defaultCode
}

/** Agrupa las apps instaladas por sección, ordenadas. Función pura. */
object SectionLayout {
    fun build(apps: List<AppEntry>, configs: Map<String, AppConfig>): Map<Section, List<PlacedApp>> =
        apps.mapNotNull { app -> configs[app.key]?.let { PlacedApp(app, it) } }
            .groupBy { it.config.section }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy<PlacedApp> { it.config.order }.thenBy { it.app.label.lowercase() })
            }

    /**
     * Piezas del mosaico: las apps con inMosaic primero; si faltan (p. ej. una app desinstalada),
     * las siguientes de la seccion con inMosaic = false ocupan su sitio. Orden final por `order`.
     */
    fun mosaic(placed: List<PlacedApp>, capacity: Int = MOSAIC_CAPACITY): List<PlacedApp> {
        val chosen = placed.filter { it.config.inMosaic }.take(capacity).toMutableList()
        if (chosen.size < capacity) chosen += placed.filterNot { it.config.inMosaic }.take(capacity - chosen.size)
        return chosen.sortedWith(compareBy<PlacedApp> { it.config.order }.thenBy { it.app.label.lowercase() })
    }
}
