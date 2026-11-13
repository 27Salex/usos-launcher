package com.usos.launcher.data.feeds

import java.net.URI

/**
 * Descubrimiento de feeds a partir de lo que escribe el usuario (URL de feed, de web o dominio). Lógica pura:
 * detectar si un texto es un feed, sacar los `<link rel="alternate">` de un HTML y generar rutas habituales.
 */
object FeedDiscovery {
    val COMMON_PATHS = listOf("/feed", "/rss", "/rss.xml", "/atom.xml", "/feed.xml", "/index.xml", "/news/rss")

    private val FEED_TAG = Regex("""<(rss|feed|rdf:rdf)[\s>]""", RegexOption.IGNORE_CASE)

    /** true si el cuerpo es RSS/Atom/RDF (mira solo el comienzo; un HTML nunca cuenta). */
    fun looksLikeFeed(body: String): Boolean {
        val head = body.take(4000).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (head.startsWith("<!doctype html", ignoreCase = true) || head.startsWith("<html", ignoreCase = true)) return false
        return FEED_TAG.containsMatchIn(head)
    }

    data class AlternateLink(val url: String, val title: String?)

    private val LINK = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z][a-zA-Z0-9:_-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")

    /** `<link rel="alternate" type="application/rss+xml|atom+xml" href=…>` con href resuelto contra [baseUrl]. */
    fun alternateLinks(html: String, baseUrl: String): List<AlternateLink> {
        val out = LinkedHashMap<String, AlternateLink>()
        for (tag in LINK.findAll(html)) {
            val attrs = HashMap<String, String>()
            for (m in ATTR.findAll(tag.value)) {
                val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
                attrs[m.groupValues[1].lowercase()] = value
            }
            val rel = attrs["rel"]?.lowercase()?.split(' ') ?: continue
            val type = attrs["type"]?.lowercase() ?: continue
            if ("alternate" !in rel) continue
            if (!type.contains("rss+xml") && !type.contains("atom+xml")) continue
            val href = attrs["href"]?.let { ParserHtml.decode(it) }?.takeIf { it.isNotBlank() } ?: continue
            val abs = resolve(baseUrl, href) ?: continue
            out.putIfAbsent(abs, AlternateLink(abs, attrs["title"]?.let { ParserHtml.decode(it) }?.takeIf { it.isNotBlank() }))
        }
        return out.values.toList()
    }

    /** Rutas habituales sobre el origen de [url] (y bajo su ruta si la tiene), sin repetir. */
    fun commonCandidates(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return emptyList()
        val scheme = uri.scheme ?: return emptyList()
        val host = uri.rawAuthority ?: return emptyList()
        val origin = "$scheme://$host"
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val list = LinkedHashSet<String>()
        if (path.isNotEmpty()) {
            list += "$origin$path/feed"
            list += "$origin$path/rss"
        }
        COMMON_PATHS.forEach { list += origin + it }
        list.remove(url)
        return list.toList()
    }

    /** Host sin «www.». */
    fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.lowercase()?.removePrefix("www.")

    fun resolve(base: String, href: String): String? = runCatching {
        val r = URI(base).resolve(href.trim())
        if (r.scheme == "http" || r.scheme == "https") r.toString() else null
    }.getOrNull()

    /** Feeds conocidos de webs que no anuncian el suyo en el HTML. */
    private val KNOWN: Map<String, String> = mapOf(
        "crunchyroll.com" to CRUNCHYROLL_RSS,
    )

    fun knownFeeds(url: String): List<String> {
        val host = hostOf(url) ?: return emptyList()
        return KNOWN.filterKeys { host == it || host.endsWith(".$it") }.values.toList()
    }
}

/** Decodificación mínima de entidades en atributos HTML. */
internal object ParserHtml {
    fun decode(s: String): String = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#38;", "&")
        .replace("&#x2F;", "/").replace("&lt;", "<").replace("&gt;", ">")
}

const val CRUNCHYROLL_RSS = "https://cr-news-api-service.prd.crunchyrollsvc.com/v1/es-ES/rss"

/** Feeds de un toque. Todos verificados con curl (RSS/Atom válido). */
object SuggestedFeeds {
    val list: List<Pair<String, String>> = DefaultFeeds.list + listOf(
        "Crunchyroll News" to CRUNCHYROLL_RSS,
        "Hipertextual" to "https://hipertextual.com/feed",
        "Xataka Android" to "https://www.xatakandroid.com/index.xml",
        "Vida Extra" to "https://www.vidaextra.com/index.xml",
        "WWWhatsnew" to "https://wwwhatsnew.com/feed/",
        "Computer Hoy" to "https://computerhoy.20minutos.es/rss",
        "Ramen Para Dos" to "https://www.ramenparados.com/feed/",
        "Anime News Network" to "https://www.animenewsnetwork.com/all/rss.xml",
        "IGN España" to "https://es.ign.com/feed.xml",
        "Sensacine" to "https://www.sensacine.com/rss/noticias.xml",
    )
}
