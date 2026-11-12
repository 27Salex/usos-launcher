package com.usos.launcher.data.sections

import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import kotlinx.serialization.Serializable

enum class Highlight { NORMAL, ACCENT, WHITE, OUTLINE }

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
) {
    val key: String get() = AppEntry.configKey(packageName, userSerial)
}
