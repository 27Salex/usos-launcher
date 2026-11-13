package com.usos.launcher.data.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class FeedDiscoveryTest {
    @Test
    fun detectsFeedsAndRejectsHtml() {
        assertTrue(FeedDiscovery.looksLikeFeed("<?xml version=\"1.0\"?>\n  <rss version=\"2.0\"><channel>"))
        assertTrue(FeedDiscovery.looksLikeFeed("﻿<?xml version=\"1.0\"?><feed xmlns=\"http://www.w3.org/2005/Atom\">"))
        assertTrue(FeedDiscovery.looksLikeFeed("<rdf:RDF xmlns:rdf=\"x\">"))
        assertFalse(FeedDiscovery.looksLikeFeed("<!DOCTYPE html><html><head><link rel=\"alternate\" type=\"application/rss+xml\"></head>"))
        assertFalse(FeedDiscovery.looksLikeFeed("{\"json\": true}"))
    }

    @Test
    fun findsAlternateLinks() {
        val html = """
            <html><head>
            <link rel="stylesheet" href="/a.css">
            <link rel="alternate" type="application/rss+xml" title="Principal" href="/feed.xml">
            <link href='https://other.test/atom?a=1&amp;b=2' type='application/atom+xml' rel='alternate'>
            <link rel="alternate" hreflang="es" href="/es/">
            <link rel="alternate" type="application/rss+xml" href="/feed.xml">
            </head></html>
        """.trimIndent()
        val links = FeedDiscovery.alternateLinks(html, "https://site.test/blog/")
        assertEquals(listOf("https://site.test/feed.xml", "https://other.test/atom?a=1&b=2"), links.map { it.url })
        assertEquals("Principal", links[0].title)
    }

    @Test
    fun commonCandidatesUseOriginAndPath() {
        val c = FeedDiscovery.commonCandidates("https://site.test/noticias")
        assertEquals("https://site.test/noticias/feed", c.first())
        assertTrue("https://site.test/rss.xml" in c)
        assertTrue("https://site.test/news/rss" in c)
        assertEquals(7, FeedDiscovery.commonCandidates("https://site.test").size)
        assertFalse("https://site.test/feed" in FeedDiscovery.commonCandidates("https://site.test/feed"))
    }

    @Test
    fun knownFeedForCrunchyroll() {
        assertEquals(listOf(CRUNCHYROLL_RSS), FeedDiscovery.knownFeeds("https://www.crunchyroll.com/es/news"))
        assertTrue(FeedDiscovery.knownFeeds("https://example.com").isEmpty())
    }

    @Test
    fun domainBecomesUrl() {
        assertEquals("https://crunchyroll.com", FeedFormat.normalizeUrl(" crunchyroll.com "))
        assertEquals("http://a.org/f.xml", FeedFormat.normalizeUrl("http://a.org/f.xml"))
        assertNull(FeedFormat.normalizeUrl("ftp://a.org/f"))
        assertNull(FeedFormat.normalizeUrl("dos palabras.com"))
        assertNull(FeedFormat.normalizeUrl("sinpunto"))
    }

    @Test
    fun crunchyrollFeedParses() {
        val parser = KXmlParser()
        parser.setInput(javaClass.classLoader!!.getResourceAsStream("sample_crunchyroll.xml")!!, null)
        val feed = FeedParser.parse(parser)
        assertEquals("Últimas Noticias de Anime", feed.title)
        assertEquals(3, feed.items.size)
        assertTrue(feed.items[0].url.startsWith("https://crunchyroll.com/es-es/news/"))
        assertTrue(feed.items[0].imageUrl!!.startsWith("https://a.storyblok.com/"))
        assertNotNull(feed.items[0].publishedAt)
    }

    @Test
    fun formatHelpers() {
        val now = 1_000_000_000L
        assertEquals("12 MIN", FeedFormat.age(now - 12 * 60_000, now))
        assertEquals("2 D", FeedFormat.age(now - 49 * 3_600_000, now))
        assertTrue(FeedFormat.needsRefresh(null, now))
        assertFalse(FeedFormat.needsRefresh(now - 9 * 60_000, now))
        assertTrue(FeedFormat.needsRefresh(now - 11 * 60_000, now))
    }
}
