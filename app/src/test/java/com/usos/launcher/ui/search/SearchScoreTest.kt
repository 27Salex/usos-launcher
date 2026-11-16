package com.usos.launcher.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchScoreTest {
    private fun s(label: String, q: String) = SearchFilter.score(label, SearchFilter.normalize(q))

    @Test fun prefixAndCase() { assertEquals(0, s("WhatsApp", "whats")); assertEquals(0, s("WhatsApp", "WHATS APP")) }
    @Test fun wordStart() = assertEquals(1, s("Google Maps", "map"))
    @Test fun initials() = assertEquals(3, s("DG Roster App", "dra"))
    @Test fun typos() { assertNotNull(s("Spotify", "spotfy")); assertNotNull(s("Crunchyroll", "crunchyrol")); assertNotNull(s("Telegram", "telgeram")) }
    @Test fun accents() = assertEquals(0, s("Cámara", "camara"))
    @Test fun unrelated() = assertNull(s("Spotify", "waze"))
}
