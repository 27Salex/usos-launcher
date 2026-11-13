package com.usos.launcher.ui.search

import com.usos.launcher.data.suggest.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchClassifierTest {
    private val apps = listOf("Cámara", "WhatsApp", "Mapas")
    private fun classify(t: String) = SearchClassifier.classify(t, apps) { it }

    @Test
    fun urlsAndDomains() {
        assertEquals("https://example.com", SearchClassifier.asUrl("example.com"))
        assertEquals("https://sub.dominio.es/ruta?x=1", SearchClassifier.asUrl("sub.dominio.es/ruta?x=1"))
        assertEquals("http://foo.org", SearchClassifier.asUrl("http://foo.org"))
        assertEquals("https://1.2.3.4:8080/x", SearchClassifier.asUrl("1.2.3.4:8080/x"))
        assertNull(SearchClassifier.asUrl("hola mundo.com"))
        assertNull(SearchClassifier.asUrl("receta de tortilla"))
        assertNull(SearchClassifier.asUrl("whatsapp"))
        assertNull(SearchClassifier.asUrl("version 1.5"))
        assertNull(SearchClassifier.asUrl("ftp://a.com"))
    }

    @Test
    fun orderUrlThenAppThenSearch() {
        assertTrue(classify("example.com") is SearchIntent.OpenUrl)
        assertEquals(SearchIntent.OpenApp("Cámara"), classify("camara"))
        assertEquals(SearchIntent.OpenApp("WhatsApp"), classify(" WHATSAPP "))
        assertEquals(SearchIntent.WebSearch("what"), classify("what")) // solo coincidencia exacta
        assertEquals(SearchIntent.WebSearch("tiempo en madrid"), classify("tiempo en madrid"))
    }

    @Test
    fun engineUrls() {
        assertEquals("https://www.google.com/search?q=tiempo+madrid", SearchEngine.GOOGLE.urlFor(" tiempo madrid "))
        assertEquals("https://duckduckgo.com/?q=a%26b", SearchEngine.DUCKDUCKGO.urlFor("a&b"))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromName("nope"))
    }
}
