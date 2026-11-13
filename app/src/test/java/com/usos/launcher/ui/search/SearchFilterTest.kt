package com.usos.launcher.ui.search

import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {
    private fun item(label: String, section: Section, frozen: Boolean = false) = SearchItem(
        AppEntry("p." + label.filter { it.isLetterOrDigit() }, "A", 0, label, 0, null),
        AppEntry.defaultCodeFor(label), section, frozen,
    )

    private val items = listOf(
        item("Zeta", Section.WORK),
        item("Álbum", Section.PERSONAL),
        item("Cámara", Section.PERSONAL),
        item("Netflix", Section.LEISURE, frozen = true),
        item("Calculadora", Section.NONE),
        item("WhatsApp", Section.TALK),
        item("Mapas", Section.MOVE),
        item("Alfa", Section.WORK),
    )

    private fun labels(g: List<SearchGroup>) = g.flatMap { it.items.map { i -> i.app.label } }

    @Test fun emptyQueryGroupsInOrder() {
        val g = SearchFilter.groups(items, "")
        assertEquals(
            listOf(Section.WORK, Section.TALK, Section.MOVE, Section.PERSONAL, Section.NONE, Section.LEISURE),
            g.map { it.section },
        )
    }

    @Test fun alphabeticalIgnoringAccents() {
        val g = SearchFilter.groups(items, "")
        assertEquals(listOf("Alfa", "Zeta"), g[0].items.map { it.app.label })
        assertEquals(listOf("Álbum", "Cámara"), g[3].items.map { it.app.label })
    }

    @Test fun ignoresCase() = assertEquals(listOf("Netflix"), labels(SearchFilter.groups(items, "NETFLIX")))

    @Test fun ignoresAccentsInQuery() = assertEquals(listOf("Cámara"), labels(SearchFilter.groups(items, "camara")))

    @Test fun ignoresAccentsInLabel() = assertEquals(listOf("Álbum"), labels(SearchFilter.groups(items, "album")))

    @Test fun accentedQueryMatchesPlain() = assertEquals(listOf("Calculadora"), labels(SearchFilter.groups(items, "CÁLCU")))

    @Test fun substringNotOnlyPrefix() =
        assertEquals(listOf("Cámara", "Mapas"), labels(SearchFilter.groups(items, "ma")).sorted())

    @Test fun emptyGroupsDisappear() {
        val g = SearchFilter.groups(items, "whats")
        assertEquals(listOf(Section.TALK), g.map { it.section })
    }

    @Test fun noMatchesGivesNoGroups() = assertTrue(SearchFilter.groups(items, "zzzz").isEmpty())

    @Test fun queryIsTrimmed() = assertEquals(listOf("Netflix"), labels(SearchFilter.groups(items, "  netflix ")))

    @Test fun keepsFrozenFlag() = assertTrue(SearchFilter.groups(items, "net")[0].items[0].frozen)

    @Test fun settingsResultMatchesAjustesAndUsos() {
        assertTrue(SearchFilter.matchesSettings("ajustes"))
        assertTrue(SearchFilter.matchesSettings(" Aju"))
        assertTrue(SearchFilter.matchesSettings("usos"))
        assertTrue(SearchFilter.matchesSettings("us"))
        assertFalse(SearchFilter.matchesSettings("a"))
        assertFalse(SearchFilter.matchesSettings("maps"))
        assertFalse(SearchFilter.matchesSettings(""))
    }
}
