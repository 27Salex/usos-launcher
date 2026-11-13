package com.usos.launcher.data.suggest

import java.net.URLEncoder

/** Buscador al que se envía el texto de la barra (ACTION_VIEW en el navegador por defecto). */
enum class SearchEngine(val label: String, private val base: String) {
    GOOGLE("GOOGLE", "https://www.google.com/search?q="),
    DUCKDUCKGO("DUCKDUCKGO", "https://duckduckgo.com/?q="),
    BING("BING", "https://www.bing.com/search?q="),
    ECOSIA("ECOSIA", "https://www.ecosia.org/search?q="),
    BRAVE("BRAVE", "https://search.brave.com/search?q=");

    fun urlFor(text: String): String = base + URLEncoder.encode(text.trim(), "UTF-8")

    companion object {
        fun fromName(name: String?): SearchEngine = entries.firstOrNull { it.name == name } ?: GOOGLE
    }
}
