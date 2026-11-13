package com.usos.launcher.data.sections

import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section

/**
 * Disposición inicial propuesta del plan. Fija sección y orden de apps conocidas
 * (no cuenta como manual). Solo aplica a apps del perfil principal.
 */
object InitialLayout {
    data class Slot(val section: Section, val index: Int)

    private class Entry(
        val section: Section,
        val packages: Set<String>,
        val labelMatch: ((String) -> Boolean)? = null,
    )

    private fun pkgs(vararg p: String) = p.toSet()

    private fun byLabel(section: Section, match: (String) -> Boolean) =
        Entry(section, emptySet(), match)

    private val entries: List<Entry> = listOf(
        // Trabajo (Agenda es una pieza de información de la Fase 8)
        Entry(Section.WORK, pkgs("com.Slack")),
        Entry(Section.WORK, pkgs("com.microsoft.teams")),
        Entry(Section.WORK, pkgs("com.atlassian.android.jira.core")),
        Entry(Section.WORK, pkgs("com.github.android")),
        // Hablar
        Entry(Section.TALK, pkgs("com.whatsapp")),
        Entry(Section.TALK, pkgs("org.telegram.messenger")),
        Entry(Section.TALK, pkgs("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms")),
        Entry(Section.TALK, pkgs("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer")),
        Entry(Section.TALK, pkgs("com.google.android.gm")),
        // Moverse (Mapas es pieza de información en la Fase 8; aquí es una app más)
        Entry(Section.MOVE, pkgs("com.google.android.apps.maps")),
        Entry(Section.MOVE, pkgs("com.waze")),
        byLabel(Section.MOVE) { it.contains("smou", ignoreCase = true) },
        Entry(Section.MOVE, pkgs("com.cabify.rider")),
        byLabel(Section.MOVE) {
            it.replace("-", "").replace(" ", "").contains("tmobilitat", ignoreCase = true)
        },
        // Personal
        Entry(Section.PERSONAL, pkgs("org.mozilla.firefox")),
        Entry(Section.PERSONAL, pkgs(
            "com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.motorola.camera3", "com.oplus.camera", "com.oneplus.camera",
        )),
        Entry(Section.PERSONAL, pkgs("com.spotify.music")),
        Entry(Section.PERSONAL, pkgs("com.anthropic.claude")),
        byLabel(Section.PERSONAL) { it.trim().equals("lumi", ignoreCase = true) },
        Entry(Section.PERSONAL, pkgs("com.samsung.android.calendar", "com.google.android.calendar", "com.android.calendar")),
        // Ocio
        Entry(Section.LEISURE, pkgs("com.netflix.mediaclient")),
        Entry(Section.LEISURE, pkgs("com.disney.disneyplus")),
        Entry(Section.LEISURE, pkgs("com.amazon.avod.thirdpartyclient")),
        Entry(Section.LEISURE, pkgs("com.crunchyroll.crunchyroid")),
    )

    fun slotFor(app: AppEntry): Slot? {
        if (!app.isMainProfile) return null
        val i = entries.indexOfFirst { e ->
            app.packageName in e.packages || e.labelMatch?.invoke(app.label) == true
        }
        return if (i < 0) null else Slot(entries[i].section, i)
    }
}
