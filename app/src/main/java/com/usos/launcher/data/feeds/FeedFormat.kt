package com.usos.launcher.data.feeds

object FeedFormat {
    enum class AgeUnit { NOW, MIN, HOUR, DAY }

    /** Antigüedad corta para la tarjeta (la UI la escribe según el idioma): ahora, 12 min, 3 h, 2 d. */
    data class Age(val unit: AgeUnit, val value: Int)

    fun age(publishedAt: Long, now: Long): Age {
        val min = ((now - publishedAt) / 60_000L).coerceAtLeast(0)
        return when {
            min < 1 -> Age(AgeUnit.NOW, 0)
            min < 60 -> Age(AgeUnit.MIN, min.toInt())
            min < 24 * 60 -> Age(AgeUnit.HOUR, (min / 60).toInt())
            else -> Age(AgeUnit.DAY, (min / (24 * 60)).toInt())
        }
    }

    /** true si no hay datos o han pasado más de 10 min desde la última actualización. */
    fun needsRefresh(lastFetchedAt: Long?, now: Long, maxAgeMs: Long = FeedLimits.REFRESH_AFTER_MS): Boolean =
        lastFetchedAt == null || now - lastFetchedAt > maxAgeMs

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
