package com.usos.launcher.ui.search

/** Qué hace Intro en la búsqueda. Lógica pura. */
sealed interface SearchIntent {
    data class OpenUrl(val url: String) : SearchIntent
    data class OpenApp<T>(val app: T) : SearchIntent
    data class WebSearch(val text: String) : SearchIntent
}

object SearchClassifier {
    private val TLD = Regex("^[a-z]{2,24}$|^xn--[a-z0-9-]{2,59}$")
    private val HOST_LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

    /**
     * Devuelve la URL a abrir (https:// si falta) si el texto parece una URL o un dominio: sin espacios,
     * con esquema http(s) o con host de varias etiquetas terminado en un TLD de letras («example.com», «a.b.es/ruta»).
     */
    fun asUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val hasScheme = t.startsWith("http://", true) || t.startsWith("https://", true)
        val rest = if (hasScheme) t.substringAfter("://") else t
        if (!hasScheme && t.contains("://")) return null
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        if (host.isEmpty()) return null
        val isIp = host.split('.').let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }
        val labels = host.split('.')
        val domainOk = labels.size >= 2 && labels.all { HOST_LABEL.matches(it) } && TLD.matches(labels.last())
        val localhost = hasScheme && host == "localhost"
        if (!domainOk && !isIp && !localhost) return null
        return if (hasScheme) t else "https://$t"
    }

    /** URL > app con nombre exacto (sin mayúsculas ni acentos) > búsqueda web. */
    fun <T> classify(text: String, apps: List<T>, label: (T) -> String): SearchIntent {
        val t = text.trim()
        asUrl(t)?.let { return SearchIntent.OpenUrl(it) }
        val q = SearchFilter.normalize(t)
        apps.firstOrNull { SearchFilter.normalize(label(it).trim()) == q }?.let { return SearchIntent.OpenApp(it) }
        return SearchIntent.WebSearch(t)
    }
}
