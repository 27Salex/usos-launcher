package com.usos.launcher.data.sections

import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import kotlinx.serialization.Serializable

enum class Highlight { NORMAL, ACCENT, WHITE, OUTLINE, COLOR }

@Serializable
data class AppConfig(
    val packageName: String,
    val userSerial: Long,
    val section: Section,
    val order: Int,
    val inMosaic: Boolean,
    val code: String? = null,
    val highlight: Highlight = Highlight.NORMAL,
    val showUsage: Boolean = false,
    val manual: Boolean = false,
    val size: TileSize = TileSize.S1X1,
    /** Color propio de la pieza (ARGB) cuando highlight = COLOR. */
    val highlightColor: Int? = null,
) {
    val key: String get() = AppEntry.configKey(packageName, userSerial)
}
