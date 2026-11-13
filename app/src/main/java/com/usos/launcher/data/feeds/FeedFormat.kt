package com.usos.launcher.data.feeds

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

    /** true si no hay datos o han pasado más de 10 min desde la última actualización. */
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
