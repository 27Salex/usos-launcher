package com.usos.launcher.data.feeds

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object FeedLimits {
    const val MIN_PER_HOUR = 3
    const val MAX_PER_HOUR = 10
    const val DEFAULT_PER_HOUR = 5

    fun clamp(n: Int): Int = n.coerceIn(MIN_PER_HOUR, MAX_PER_HOUR)

    /** Ventana entre actualizaciones automáticas al abrir el panel. */
    const val REFRESH_AFTER_MS = 30 * 60 * 1000L
}

/** Resultado de aplicar el límite por hora a la lista de artículos. */
data class QueuePlan(
    /** Artículos visibles (ya desvelados, incluidos los que se desvelan ahora), más recientes primero. */
    val visible: List<Article>,
    /** Ids que hay que marcar con revealedAt = now. */
    val toReveal: List<Long>,
    /** Artículos que siguen en cola tras desvelar. */
    val queued: Int,
    /** Inicio de la próxima hora de reloj; null si la cola está vacía. */
    val nextRevealAt: Long?,
)

/**
 * Límite por hora de reloj. Lógica pura.
 *
 * - Cupo de la hora = perHour - artículos con revealedAt dentro de la hora actual (leídos incluidos).
 * - Los artículos en cola (revealedAt = null) se desvelan del más reciente al más antiguo hasta agotar el cupo.
 * - Visible = desvelado y (no leído o con estrella). Un leído sin estrella desaparece; con estrella se queda.
 */
object FeedQueue {
    fun plan(articles: List<Article>, now: Long, zone: ZoneId, perHour: Int): QueuePlan {
        val hourStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).truncatedTo(ChronoUnit.HOURS)
        val start = hourStart.toInstant().toEpochMilli()
        val nextStart = hourStart.plusHours(1).toInstant().toEpochMilli()
        val limit = FeedLimits.clamp(perHour)
        val usedThisHour = articles.count { a -> a.revealedAt?.let { it in start until nextStart } == true }
        val slots = (limit - usedThisHour).coerceAtLeast(0)

        val newest = compareByDescending<Article> { it.publishedAt }.thenByDescending { it.id }
        val waiting = articles.filter { it.revealedAt == null }.sortedWith(newest)
        val reveal = waiting.take(slots)
        val revealedIds = reveal.mapTo(HashSet()) { it.id }

        val visible = articles
            .filter { (it.revealedAt != null || it.id in revealedIds) && (!it.read || it.starred) }
            .sortedWith(newest)
        val queued = waiting.size - reveal.size
        return QueuePlan(visible, reveal.map { it.id }, queued, if (queued > 0) nextStart else null)
    }

    /** «5 MÁS A LAS 19:00»; null si no hay cola. */
    fun queueLabel(plan: QueuePlan, perHour: Int, zone: ZoneId): String? {
        val at = plan.nextRevealAt ?: return null
        val n = minOf(plan.queued, FeedLimits.clamp(perHour))
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
        return "%d MÁS A LAS %02d:%02d".format(n, time.hour, time.minute)
    }
}

object FeedFormat {
    /** Antigüedad corta para la tarjeta: AHORA, 12 MIN, 3 H, 2 D. */
    fun age(publishedAt: Long, now: Long): String {
        val min = ((now - publishedAt) / 60_000L).coerceAtLeast(0)
        return when {
            min < 1 -> "AHORA"
            min < 60 -> "$min MIN"
            min < 24 * 60 -> "${min / 60} H"
            else -> "${min / (24 * 60)} D"
        }
    }

    /** true si no hay datos o han pasado más de 30 min desde la última actualización. */
    fun needsRefresh(lastFetchedAt: Long?, now: Long): Boolean =
        lastFetchedAt == null || now - lastFetchedAt > FeedLimits.REFRESH_AFTER_MS

    /** Completa el esquema (https por defecto) y valida; null si no es una URL http(s) razonable. */
    fun normalizeUrl(raw: String): String? {
        var t = raw.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        if (!t.contains("://")) t = "https://$t"
        val scheme = t.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = t.substringAfter("://").substringBefore('/').substringBefore('?')
        if (host.isEmpty() || !host.contains('.') && host != "localhost") return null
        return t
    }
}
