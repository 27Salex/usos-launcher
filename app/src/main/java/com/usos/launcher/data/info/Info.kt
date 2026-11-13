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

/** Cuándo empieza el evento de la agenda (el texto lo pone la UI según el idioma). */
enum class InfoWhen { NONE, NOW, TODAY, TOMORROW }

/** Pieza de información del mosaico; la UI compone etiqueta y dato con recursos (sin texto fijo aquí). */
data class InfoPiece(val kind: InfoKind, val whenAt: InfoWhen = InfoWhen.NONE, val time: String = "", val title: String = "")

data class CalEvent(val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean, val eventId: Long = 0)

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

    /** Hoy a las 10:30, mañana a las 10:30, o ahora si ya ha empezado. */
    fun agendaPiece(event: CalEvent?, now: Long, zone: ZoneId): InfoPiece? {
        event ?: return null
        val begin = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.beginMs), zone)
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        val time = "%02d:%02d".format(begin.hour, begin.minute)
        val whenAt = when {
            event.beginMs <= now -> InfoWhen.NOW
            begin.toLocalDate() == today -> InfoWhen.TODAY
            else -> InfoWhen.TOMORROW
        }
        return InfoPiece(InfoKind.AGENDA, whenAt, time, event.title)
    }

    /** «Ir a casa»: siempre visible si Maps está instalado. */
    fun mapsPiece(mapsInstalled: Boolean): InfoPiece? =
        if (mapsInstalled) InfoPiece(InfoKind.MAPS) else null

    fun bySection(pieces: List<InfoPiece?>): Map<Section, InfoPiece> =
        pieces.filterNotNull().associateBy { sectionOf(it.kind) }
}

/** Acceso a CalendarContract.Instances; solo se llama con READ_CALENDAR concedido. */
class AgendaRepository(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Instancias entre ahora y el final de mañana (zona local). */
    suspend fun upcoming(now: Long, zone: ZoneId): List<CalEvent> {
        val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            .toLocalDate().plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        return range(now, end)
    }

    /** Instancias que solapan [startMs, endMs) (CalendarContract.Instances); vacío sin permiso. */
    suspend fun range(startMs: Long, endMs: Long): List<CalEvent> {
        if (!hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(startMs.toString()).appendPath(endMs.toString()).build()
                val projection = arrayOf(
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_ID,
                )
                val out = mutableListOf<CalEvent>()
                context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext()) {
                        out += CalEvent(
                            title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "",
                            beginMs = c.getLong(1), endMs = c.getLong(2), allDay = c.getInt(3) == 1,
                            eventId = c.getLong(4),
                        )
                    }
                }
                out.toList()
            }.getOrDefault(emptyList())
        }
    }
}
