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
}
