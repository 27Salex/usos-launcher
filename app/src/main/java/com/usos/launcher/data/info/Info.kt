package com.usos.launcher.data.info

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.domain.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class InfoKind { AGENDA, MAPS }

/** Pieza de información del mosaico: etiqueta mono arriba y dato abajo. */
data class InfoPiece(val kind: InfoKind, val label: String, val data: String)

data class CalEvent(val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean)

/** Lógica pura de las piezas de información. */
object InfoPieces {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Tamaño por defecto de cada pieza: Agenda 2×1, Ir a casa 1×2. */
    fun defaultSize(kind: InfoKind): TileSize = when (kind) {
        InfoKind.AGENDA -> TileSize.S2X1
        InfoKind.MAPS -> TileSize.S1X2
    }

    /** Sección donde vive cada pieza. */
    fun sectionOf(kind: InfoKind): Section = when (kind) {
        InfoKind.AGENDA -> Section.WORK
        InfoKind.MAPS -> Section.MOVE
    }

    /**
     * Próximo evento de hoy o mañana que no haya terminado. Los eventos de todo el día se ignoran
     * (no tienen hora y sus instantes están en UTC). Devuelve null si no hay ninguno.
     */
    fun nextEvent(events: List<CalEvent>, now: Long, zone: ZoneId): CalEvent? {
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        val limit = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        return events
            .filter { !it.allDay && it.endMs > now && it.beginMs < limit }
            .minByOrNull { it.beginMs }
    }

    /** «AGENDA · 10:30», «AGENDA · MAÑ 10:30»; AHORA si ya ha empezado. */
    fun agendaPiece(event: CalEvent?, now: Long, zone: ZoneId): InfoPiece? {
        event ?: return null
        val begin = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.beginMs), zone)
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        val whenText = when {
            event.beginMs <= now -> "AHORA"
            begin.toLocalDate() == today -> "%02d:%02d".format(begin.hour, begin.minute)
            else -> "MAÑ %02d:%02d".format(begin.hour, begin.minute)
        }
        return InfoPiece(InfoKind.AGENDA, "AGENDA · $whenText", event.title)
    }

    /** «Ir a casa»: siempre visible si Maps está instalado. */
    fun mapsPiece(mapsInstalled: Boolean): InfoPiece? =
        if (mapsInstalled) InfoPiece(InfoKind.MAPS, "MAPAS", "IR A CASA") else null

    fun bySection(pieces: List<InfoPiece?>): Map<Section, InfoPiece> =
        pieces.filterNotNull().associateBy { sectionOf(it.kind) }
}

/** Acceso a CalendarContract.Instances; solo se llama con READ_CALENDAR concedido. */
class AgendaRepository(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Instancias entre ahora y el final de mañana (zona local). */
    suspend fun upcoming(now: Long, zone: ZoneId): List<CalEvent> {
        if (!hasPermission()) return emptyList()
        val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            .toLocalDate().plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(now.toString()).appendPath(end.toString()).build()
                val projection = arrayOf(
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                )
                val out = mutableListOf<CalEvent>()
                context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext()) {
                        out += CalEvent(
                            title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "Sin título",
                            beginMs = c.getLong(1), endMs = c.getLong(2), allDay = c.getInt(3) == 1,
                        )
                    }
                }
                out.toList()
            }.getOrDefault(emptyList())
        }
    }
}
