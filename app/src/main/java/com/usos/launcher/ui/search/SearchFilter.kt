package com.usos.launcher.ui.search

import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import java.text.Normalizer

/** App tal como la muestra la busqueda. `frozen`: app de ocio con el ocio cerrado. */
data class SearchItem(val app: AppEntry, val code: String, val section: Section, val frozen: Boolean)

data class SearchGroup(val section: Section, val items: List<SearchItem>)

/** Filtro y agrupacion de la busqueda. Funcion pura. */
object SearchFilter {
    /** Orden de los grupos: Otros va antes que Ocio. */
    val groupOrder: List<Section> =
        listOf(Section.WORK, Section.TALK, Section.MOVE, Section.PERSONAL, Section.NONE, Section.LEISURE)

    private val combiningMarks = Regex("\\p{Mn}+")

    /** Minusculas y sin acentos. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase()

    /** true si el texto (2+ letras) es el inicio de «ajustes» o de «usos»: aparece el resultado AJUSTES. */
    fun matchesSettings(query: String): Boolean {
        val q = normalize(query.trim())
        return q.length >= 2 && ("ajustes".startsWith(q) || "usos".startsWith(q))
    }

    /** Web apps instaladas desde un navegador (Chrome, Edge, Samsung Internet...): paquetes WebAPK. */
    fun isWebApp(app: AppEntry): Boolean = app.packageName.contains("webapk")

    /**
     * Grupos con coincidencias, en orden; dentro de cada grupo, por relevancia y luego alfabético. Sin texto,
     * todas. [pwaKeywords] (normalizadas, guardadas solo en el dispositivo): si la búsqueda empieza por una,
     * solo se ven las web apps y el resto del texto filtra entre ellas.
     */
    fun groups(items: List<SearchItem>, query: String, pwaKeywords: Set<String> = emptySet()): List<SearchGroup> {
        var q = normalize(query.trim())
        var pool = items
        pwaKeywords.firstOrNull { it.isNotEmpty() && (q == it || q.startsWith("$it ")) }?.let { k ->
            pool = items.filter { isWebApp(it.app) }
            q = q.removePrefix(k).trim()
        }
        val scored = if (q.isEmpty()) pool.map { it to 0 } else pool.mapNotNull { item -> score(item.app.label, q)?.let { item to it } }
        val bySection = scored.groupBy { it.first.section }
        return groupOrder.mapNotNull { section ->
            bySection[section]?.let { list ->
                SearchGroup(
                    section,
                    list.sortedWith(compareBy({ it.second }, { normalize(it.first.app.label) }, { it.first.app.userSerial })).map { it.first },
                )
            }
        }
    }

    private fun compact(s: String) = s.filter { it.isLetterOrDigit() }

    /**
     * Relevancia (menor = mejor) o null si no coincide. Sin mayúsculas, acentos, espacios ni signos.
     * 0 empieza igual · 1 empieza una palabra · 2 contiene · 3 iniciales · 4 letras en orden · 5 con erratas
     * (desde 4 letras: 1 errata hasta 5, 2 desde 6, contra el inicio del nombre o de cada palabra).
     */
    fun score(label: String, normalizedQuery: String): Int? {
        val q = compact(normalizedQuery)
        if (q.isEmpty()) return 0
        val name = normalize(label)
        val full = compact(name)
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return when {
            full.startsWith(q) -> 0
            words.any { it.startsWith(q) } -> 1
            full.contains(q) -> 2
            q.length >= 2 && words.joinToString("") { it.take(1) }.startsWith(q) -> 3
            q.length >= 3 && isSubsequence(q, full) -> 4
            q.length >= 4 && (listOf(full) + words).any { typoMatch(q, it) } -> 5
            else -> null
        }
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        var i = 0
        for (c in s) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    /** [q] coincide con el inicio de [s] salvo erratas (Damerau-Levenshtein contra prefijos de largo parecido). */
    private fun typoMatch(q: String, s: String): Boolean {
        val max = if (q.length >= 6) 2 else 1
        for (len in (q.length - max).coerceAtLeast(1)..minOf(s.length, q.length + max)) {
            if (distance(q, s.substring(0, len)) <= max) return true
        }
        return false
    }

    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
}
