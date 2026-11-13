package com.usos.launcher.data.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.time.Instant

class FeedParserTest {
    private fun parse(resource: String): ParsedFeed {
        val stream = javaClass.classLoader!!.getResourceAsStream(resource)!!
        val parser = KXmlParser()
        parser.setInput(stream, null)
        return FeedParser.parse(parser)
    }

    @Test
    fun rss_titleAndItems() {
        val feed = parse("sample_rss.xml")
        assertEquals("Diario de Prueba", feed.title) // no el título de <image>
        assertEquals(4, feed.items.size) // el último no tiene título ni enlace
        assertEquals("Primera noticia & sus consecuencias", feed.items[0].title)
        assertEquals("https://ejemplo.test/noticias/1", feed.items[0].url)
    }

    @Test
    fun rss_summaryWithoutHtml() {
        val feed = parse("sample_rss.xml")
        assertEquals("Resumen con HTML y entidades & más.", feed.items[0].summary)
        assertEquals("Texto plano sin etiquetas.", feed.items[1].summary)
        assertEquals("HTML escapado", feed.items[3].summary)
    }

    @Test
    fun rss_imagePriority() {
        val items = parse("sample_rss.xml").items
        assertEquals("https://ejemplo.test/img/thumb1.jpg", items[0].imageUrl) // media:thumbnail gana a la <img>
        assertEquals("https://ejemplo.test/img/enc2.png", items[1].imageUrl) // enclosure
        assertEquals("https://ejemplo.test/img/body3.jpg", items[2].imageUrl) // primera <img> de content:encoded
        assertNull(items[3].imageUrl) // media:content de vídeo no vale
    }

    @Test
    fun rss_dates() {
        val items = parse("sample_rss.xml").items
        assertEquals(Instant.parse("2026-11-12T17:30:00Z").toEpochMilli(), items[0].publishedAt)
        assertEquals(Instant.parse("2026-11-12T16:00:00Z").toEpochMilli(), items[1].publishedAt)
        assertEquals(Instant.parse("2026-11-11T09:00:00Z").toEpochMilli(), items[2].publishedAt) // dc:date
        assertNull(items[3].publishedAt)
    }

    @Test
    fun atom_titleAndItems() {
        val feed = parse("sample_atom.xml")
        assertEquals("Blog Atom de Prueba", feed.title)
        assertEquals(3, feed.items.size)
        assertEquals("Entrada uno", feed.items[0].title)
        assertEquals("https://atom.test/posts/1", feed.items[0].url) // rel=alternate, no self
        assertEquals("https://atom.test/posts/2", feed.items[1].url)
    }

    @Test
    fun atom_summaryDatesAndImages() {
        val items = parse("sample_atom.xml").items
        assertEquals("Resumen escapado de la entrada.", items[0].summary)
        assertEquals("Contenido largo", items[1].summary)
        assertEquals(Instant.parse("2026-11-12T18:15:00Z").toEpochMilli(), items[0].publishedAt) // published gana a updated
        assertEquals(Instant.parse("2026-11-10T08:30:00Z").toEpochMilli(), items[1].publishedAt) // solo updated
        assertEquals("https://atom.test/img/a1.jpg", items[0].imageUrl)
        assertEquals("https://atom.test/img/a2.png", items[1].imageUrl)
        assertEquals("https://atom.test/img/a3.jpg", items[2].imageUrl)
    }

    @Test
    fun stripHtml_decodesEntities() {
        assertEquals("a & b < c", FeedParser.stripHtml("<p>a &amp; b &lt; c</p>"))
        assertEquals("ñ", FeedParser.stripHtml("&#241;"))
    }

    private fun rss(vararg dates: String): String = "<rss><channel><title>T</title>" +
        dates.mapIndexed { i, d -> "<item><title>n$i</title><link>https://e.test/$i</link><pubDate>$d</pubDate></item>" }.joinToString("") + "</channel></rss>"

    private fun parseLimited(xml: String, limit: Int): ParsedFeed {
        val p = KXmlParser()
        p.setInput(java.io.StringReader(xml))
        return FeedParser.parse(p, limit)
    }

    @Test
    fun limit_stopsEarlyWhenNewestFirst() {
        val days = (30 downTo 1).map { "2024-01-%02dT10:00:00Z".format(it) }
        val feed = parseLimited(rss(*days.toTypedArray()), 5)
        assertEquals(listOf("n0", "n1", "n2", "n3", "n4"), feed.items.map { it.title })
    }

    @Test
    fun limit_unorderedFeedKeepsMostRecent() {
        val feed = parseLimited(rss("2024-01-01T10:00:00Z", "2024-01-03T10:00:00Z", "2024-01-02T10:00:00Z"), 2)
        assertEquals(listOf("n1", "n2"), feed.items.map { it.title })
    }
}
