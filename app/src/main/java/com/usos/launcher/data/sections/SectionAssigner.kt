package com.usos.launcher.data.sections

import com.usos.launcher.domain.AppClassifier
import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section

/** Número de piezas que caben en el mosaico 3 x 2. */
const val MOSAIC_CAPACITY = 6

/**
 * Reparte apps en secciones. Las configuraciones existentes con manual = true no se tocan;
 * las demás se reclasifican solo si la sección calculada cambia. Función pura.
 */
object SectionAssigner {
    private const val UNPINNED_BASE = 100

    fun targetSection(app: AppEntry): Section =
        InitialLayout.slotFor(app)?.section ?: AppClassifier.classify(app)

    fun reconcile(apps: List<AppEntry>, existing: Map<String, AppConfig>): Map<String, AppConfig> {
        val result = existing.toMutableMap()
        val dirty = mutableListOf<AppEntry>()
        val kept = mutableListOf<AppConfig>()
        for (app in apps) {
            val cfg = existing[app.key]
            when {
                cfg == null -> dirty += app
                cfg.manual -> kept += cfg
                cfg.section != targetSection(app) -> dirty += app
                else -> kept += cfg
            }
        }
        val comparator = compareBy<AppEntry> { InitialLayout.slotFor(it)?.index ?: Int.MAX_VALUE }
            .thenBy { it.label.lowercase() }
        for ((section, list) in dirty.groupBy { targetSection(it) }) {
            val keptHere = kept.filter { it.section == section }
            var mosaic = keptHere.count { it.inMosaic }
            var nextOrder = maxOf(UNPINNED_BASE, (keptHere.maxOfOrNull { it.order } ?: -1) + 1)
            for (app in list.sortedWith(comparator)) {
                val slot = InitialLayout.slotFor(app)
                val order = slot?.index ?: nextOrder++
                val inMosaic = section != Section.NONE && mosaic < MOSAIC_CAPACITY
                if (inMosaic) mosaic++
                val old = existing[app.key]
                result[app.key] = AppConfig(
                    packageName = app.packageName,
                    userSerial = app.userSerial,
                    section = section,
                    order = order,
                    inMosaic = inMosaic,
                    code = old?.code,
                    highlight = old?.highlight ?: Highlight.NORMAL,
                    showUsage = old?.showUsage ?: false,
                    manual = false,
                    size = old?.size ?: TileSize.S1X1,
                    highlightColor = old?.highlightColor,
                )
            }
        }
        return result
    }
}
