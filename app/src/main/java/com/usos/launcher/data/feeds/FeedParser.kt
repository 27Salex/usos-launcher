package com.usos.launcher.data.feeds

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class ParsedItem(
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Long?,
)

data class ParsedFeed(val title: String?, val items: List<ParsedItem>)

/**
 * Analizador puro de RSS 2.0 y Atom sobre un XmlPullParser ya configurado con su entrada.
 * Sin espacios de nombres: se comparan los nombres con prefijo (`media:thumbnail`, `content:encoded`).
 */
object FeedParser {
    private const val RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"
    private const val SUMMARY_MAX = 300

    fun parse(parser: XmlPullParser): ParsedFeed {
        runCatching { parser.setFeature(RELAXED, true) } // entidades HTML sueltas (&nbsp;) en feeds reales
        var feedTitle: String? = null
        val items = mutableListOf<ParsedItem>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item", "entry" -> parseItem(parser)?.let { items += it }
                        "image" -> skip(parser) // <image><title> del canal no es el título del feed
                        "title" -> {
                            val t = readText(parser).trim()
                            if (feedTitle == null && t.isNotEmpty()) feedTitle = stripHtml(t)
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: XmlPullParserException) {
            if (items.isEmpty()) throw e
        }
        return ParsedFeed(feedTitle, items)
    }

    private fun parseItem(p: XmlPullParser): ParsedItem? {
        val end = p.name
        var title = ""
        var link: String? = null
        var altLink: String? = null
        var guid: String? = null
        var description: String? = null
        var content: String? = null
        var date: String? = null
        var thumb: String? = null
        var mediaContent: String? = null
        var enclosure: String? = null
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && p.name == end) break
            if (ev != XmlPullParser.START_TAG) continue
            when (val n = p.name) {
                "title" -> title = readText(p)
                "link" -> {
                    val href = p.getAttributeValue(null, "href")
                    if (href != null) { // Atom
                        val rel = p.getAttributeValue(null, "rel")
                        val type = p.getAttributeValue(null, "type")
                        if (rel == "enclosure") {
                            if (type?.startsWith("image") == true && enclosure == null) enclosure = href
                        } else if (rel == null || rel == "alternate") {
                            if (altLink == null) altLink = href
                        }
                        skip(p)
                    } else {
                        link = readText(p).trim()
                    }
                }
                "guid", "id" -> {
                    val t = readText(p).trim()
                    if (guid == null && t.startsWith("http")) guid = t
                }
                "description", "summary" -> description = readText(p)
                "content:encoded", "content" -> content = readText(p)
                "pubDate", "published", "dc:date", "updated" -> {
                    val t = readText(p).trim()
                    if (date == null || n == "pubDate" || n == "published") date = t
                }
                "media:thumbnail" -> {
                    if (thumb == null) thumb = p.getAttributeValue(null, "url")
                    skip(p)
                }
                "media:content" -> {
                    val type = p.getAttributeValue(null, "type")
                    val medium = p.getAttributeValue(null, "medium")
                    val url = p.getAttributeValue(null, "url")
                    val isImage = medium == "image" || type?.startsWith("image") == true ||
                        (type == null && medium == null && url != null && IMAGE_EXT.containsMatchIn(url))
                    if (isImage && mediaContent == null) mediaContent = url
                    skip(p)
                }
                "enclosure" -> {
                    val type = p.getAttributeValue(null, "type")
                    val url = p.getAttributeValue(null, "url")
                    if (type?.startsWith("image") == true && enclosure == null) enclosure = url
                    skip(p)
                }
                // media:group y otros contenedores: se sigue recorriendo sus hijos con este mismo bucle.
            }
        }
        val url = (link?.takeIf { it.isNotEmpty() } ?: altLink ?: guid)?.trim() ?: return null
        val plainTitle = stripHtml(title)
        if (plainTitle.isEmpty()) return null
        val html = description ?: content ?: ""
        val image = listOf(thumb, mediaContent, enclosure, firstImg(content), firstImg(description))
            .firstOrNull { it != null && it.startsWith("http") }
        return ParsedItem(
            title = plainTitle,
            summary = stripHtml(html).take(SUMMARY_MAX),
            url = url,
            imageUrl = image,
            publishedAt = date?.let(::parseDate),
        )
    }

    /** Texto completo del elemento actual (incluye CDATA y descendientes); termina en su END_TAG. */
    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(p.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private val IMG = Regex("<img\\s[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val IMAGE_EXT = Regex("\\.(jpe?g|png|webp|gif)(\\?|$)", RegexOption.IGNORE_CASE)

    private fun firstImg(html: String?): String? {
        if (html == null) return null
        val src = IMG.find(html) ?: IMG.find(decodeEntities(html)) // HTML escapado (&lt;img ...)
        return src?.groupValues?.get(1)?.let(::decodeEntities)
    }

    private val TAG = Regex("<[^>]*>")
    private val SPACES = Regex("\\s+")

    /** Quita etiquetas y decodifica entidades. Un feed puede traer HTML escapado o dentro de CDATA. */
    fun stripHtml(raw: String): String {
        var text = raw
        // Contenido escapado (&lt;p&gt;): una pasada de decodificación antes de quitar etiquetas.
        if (text.contains("&lt;") && !text.contains('<')) text = decodeEntities(text)
        text = TAG.replace(text, " ")
        return SPACES.replace(decodeEntities(text), " ").trim()
    }

    /** Entidades HTML habituales en feeds en español (el XML solo define cinco). */
    private val NAMED: Map<String, String> = mapOf(
        "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
        "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í", "Oacute" to "Ó", "Uacute" to "Ú",
        "ntilde" to "ñ", "Ntilde" to "Ñ", "uuml" to "ü", "Uuml" to "Ü", "ccedil" to "ç",
        "agrave" to "à", "egrave" to "è", "ograve" to "ò", "iexcl" to "¡", "iquest" to "¿",
        "laquo" to "«", "raquo" to "»", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
        "euro" to "€", "copy" to "©",
    )

    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        val decoded: String? = when {
            e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> when (e) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                "hellip" -> "…"
                "ndash" -> "–"
                "mdash" -> "—"
                else -> NAMED[e]
            }
        }
        decoded ?: m.value
    }

    /** RFC 822 (RSS) o ISO 8601 (Atom). null si no se entiende. */
    fun parseDate(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(t).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
            .getOrNull()
    }
}
