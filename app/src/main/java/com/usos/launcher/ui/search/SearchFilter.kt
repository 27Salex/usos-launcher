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

    /** Grupos con coincidencias, en orden, y apps en orden alfabetico. Sin texto, todas las apps. */
    fun groups(items: List<SearchItem>, query: String): List<SearchGroup> {
        val q = normalize(query.trim())
        val matching = if (q.isEmpty()) items else items.filter { normalize(it.app.label).contains(q) }
        val bySection = matching.groupBy { it.section }
        return groupOrder.mapNotNull { section ->
            bySection[section]?.let { list ->
                SearchGroup(section, list.sortedWith(compareBy({ normalize(it.app.label) }, { it.app.userSerial })))
            }
        }
    }
}
