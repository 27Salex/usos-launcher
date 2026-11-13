package com.usos.launcher.data.feeds

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException

/** User-Agent de navegador normal: algunos sitios bloquean el de OkHttp o el de curl. */
const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/** Respuesta de una URL: un feed ya analizado u otra cosa (normalmente HTML). */
sealed interface Fetched {
    data class Parsed(val feed: ParsedFeed) : Fetched
    data class Other(val body: String, val finalUrl: String) : Fetched
}

class HttpException(val code: Int) : IOException("HTTP $code")
class NotAFeedException : IOException("NOT_A_FEED")

/** Descarga y analiza un feed. Interfaz para poder sustituirla en tests. */
interface FeedFetcher {
    /** Descarga [url] siguiendo redirecciones. HttpException si el código no es 2xx; otra IOException = sin conexión. */
    @Throws(IOException::class)
    suspend fun probe(url: String, limit: Int = FeedLimits.DEFAULT_KEEP): Fetched

    @Throws(IOException::class)
    suspend fun fetch(url: String, limit: Int = FeedLimits.DEFAULT_KEEP): ParsedFeed = (probe(url, limit) as? Fetched.Parsed)?.feed ?: throw NotAFeedException()
}

class OkHttpFeedFetcher(private val http: OkHttpClient) : FeedFetcher {
    override suspend fun probe(url: String, limit: Int): Fetched = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.5")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.7")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code)
            val bytes = response.body.source().let { src ->
                src.request(MAX_BYTES + 1)
                if (src.buffer.size > MAX_BYTES) throw NotAFeedException()
                src.readByteArray()
            }
            val charset = response.body.contentType()?.charset() ?: Charsets.UTF_8
            val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.ISO_8859_1)
            if (!FeedDiscovery.looksLikeFeed(head)) {
                return@use Fetched.Other(String(bytes, charset), response.request.url.toString())
            }
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), null)
            try {
                Fetched.Parsed(FeedParser.parse(parser, limit))
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                throw NotAFeedException()
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 6L * 1024 * 1024
    }
}

/** Un feed encontrado al descubrir, con su vista previa ya descargada. */
class FoundFeed internal constructor(val url: String, val title: String, val itemCount: Int, internal val parsed: ParsedFeed)

sealed interface DiscoverResult {
    data class Found(val feeds: List<FoundFeed>) : DiscoverResult
    data class Failed(val message: String) : DiscoverResult
}

sealed interface AddOutcome {
    data class Added(val feedId: Long) : AddOutcome
    data object Duplicate : AddOutcome
    data class Failed(val message: String) : AddOutcome
}

object FeedErrors {
    // Códigos, no texto: la UI los traduce (ver FeedsViewModel.errorText).
    const val INVALID = "INVALID"
    const val NOT_A_FEED = "NOT_A_FEED"
    const val OFFLINE = "OFFLINE"
    fun http(code: Int) = "HTTP $code"
}

class FeedRepository(
    private val dao: FeedDao,
    private val fetcher: FeedFetcher,
    private val now: () -> Long = System::currentTimeMillis,
    /** Artículos que se conservan por feed (Ajustes > NOTICIAS). */
    private val keep: suspend () -> Int = { FeedLimits.DEFAULT_KEEP },
) {
    val feeds = dao.feeds()
    val articles = dao.articles()

    /**
     * Acepta URL de feed, URL de web o un dominio. Si la respuesta no es un feed busca los `<link rel="alternate">`
     * del HTML, los feeds conocidos del sitio y, por último, rutas habituales. Devuelve los feeds válidos con vista previa.
     */
    suspend fun discover(raw: String): DiscoverResult {
        val url = FeedFormat.normalizeUrl(raw) ?: return DiscoverResult.Failed(FeedErrors.INVALID)
        var firstError: String? = null
        val first = try {
            fetcher.probe(url, keep())
        } catch (e: HttpException) {
            firstError = FeedErrors.http(e.code)
            null
        } catch (e: NotAFeedException) {
            firstError = FeedErrors.NOT_A_FEED
            null
        } catch (e: IOException) {
            return DiscoverResult.Failed(FeedErrors.OFFLINE)
        }
        if (first is Fetched.Parsed) return DiscoverResult.Found(listOf(found(url, first.feed)))

        val html = first as? Fetched.Other
        val base = html?.finalUrl ?: url
        val candidates = LinkedHashSet<String>()
        html?.let { h -> FeedDiscovery.alternateLinks(h.body, base).forEach { candidates += it.url } }
        FeedDiscovery.knownFeeds(base).forEach { candidates += it }
        FeedDiscovery.commonCandidates(base).forEach { candidates += it }
        candidates.remove(url)

        val limit = keep()
        val results = coroutineScope {
            candidates.map { c ->
                async {
                    try {
                        (fetcher.probe(c, limit) as? Fetched.Parsed)?.let { found(c, it.feed) }
                    } catch (e: IOException) {
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull().distinctBy { it.url }
        if (results.isNotEmpty()) return DiscoverResult.Found(results)
        return DiscoverResult.Failed(firstError ?: FeedErrors.NOT_A_FEED)
    }

    private fun found(url: String, parsed: ParsedFeed) = FoundFeed(
        url = url,
        title = parsed.title?.takeIf { it.isNotBlank() } ?: (FeedDiscovery.hostOf(url) ?: url),
        itemCount = parsed.items.size,
        parsed = parsed,
    )

    /** Guarda el feed y sus artículos ya descargados. Devuelve el id, o null si ya existía. */
    suspend fun add(found: FoundFeed, titleOverride: String? = null): Long? {
        if (dao.feedsNow().any { it.url == found.url }) return null
        val title = titleOverride?.takeIf { it.isNotBlank() } ?: found.title
        val id = dao.insertFeed(Feed(url = found.url, title = title, lastFetchedAt = now()))
        store(id, found.parsed)
        return id
    }

    /** Descarga una URL concreta de feed (sugeridos) y la añade con el nombre dado. */
    suspend fun addByUrl(url: String, title: String?): AddOutcome {
        if (dao.feedsNow().any { it.url == url }) return AddOutcome.Duplicate
        val f = try {
            found(url, fetcher.fetch(url, keep()))
        } catch (e: HttpException) {
            return AddOutcome.Failed(FeedErrors.http(e.code))
        } catch (e: NotAFeedException) {
            return AddOutcome.Failed(FeedErrors.NOT_A_FEED)
        } catch (e: IOException) {
            return AddOutcome.Failed(FeedErrors.OFFLINE)
        }
        return add(f, title)?.let { AddOutcome.Added(it) } ?: AddOutcome.Duplicate
    }

    /** Actualiza todos los feeds en paralelo; true si todos respondieron. Un feed caído no detiene a los demás. */
    suspend fun refreshAll(): Boolean = refresh(dao.feedsNow())

    /** Actualiza los feeds de [ids] (null = todos); sin [force] solo los que llevan más de 10 min sin descargarse. */
    suspend fun refreshFeeds(ids: Collection<Long>? = null, force: Boolean = false): Boolean {
        val t = now()
        val list = dao.feedsNow().filter {
            (ids == null || it.id in ids) && (force || FeedFormat.needsRefresh(it.lastFetchedAt.takeIf { l -> l > 0 }, t))
        }
        return refresh(list)
    }

    private suspend fun refresh(list: List<Feed>): Boolean = coroutineScope {
        list.map { feed ->
            async {
                try {
                    store(feed.id, fetcher.fetch(feed.url, keep()))
                    dao.setFetched(feed.id, now())
                    true
                } catch (e: IOException) {
                    false
                }
            }
        }.awaitAll().all { it }
    }

    /** Crea los feeds por defecto (sin descargar) si no hay ninguno; true si los creo. */
    suspend fun seedDefaults(defaults: List<Pair<String, String>>): Boolean {
        if (dao.feedsNow().isNotEmpty()) return false
        defaults.forEach { (title, url) -> runCatching { dao.insertFeed(Feed(url = url, title = title)) } }
        return true
    }

    suspend fun feedsNow(): List<Feed> = dao.feedsNow()

    private suspend fun store(feedId: Long, parsed: ParsedFeed) {
        val fetchedAt = now()
        dao.insertArticles(
            parsed.items.map {
                Article(
                    feedId = feedId, title = it.title, summary = it.summary, url = it.url,
                    imageUrl = it.imageUrl, publishedAt = it.publishedAt ?: fetchedAt,
                )
            },
        )
        prune(feedId)
    }

    suspend fun rename(id: Long, title: String) {
        val t = title.trim()
        if (t.isNotEmpty()) dao.rename(id, t)
    }

    private suspend fun prune(feedId: Long) = dao.prune(feedId, keep())

    /** Poda todos los feeds al límite actual (al cambiar el ajuste y al abrir Noticias). */
    suspend fun pruneAll() = dao.feedsNow().forEach { prune(it.id) }

    /** Borra el feed y sus artículos, salvo los guardados: pasan a huérfanos con el nombre de la fuente. */
    suspend fun delete(id: Long) {
        dao.orphanStarred(id)
        dao.deleteFeed(id)
        dao.deleteArticlesOf(id)
    }
    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)
}

/** Miniaturas: descarga con OkHttp, decodifica con BitmapFactory y guarda en una caché LRU en memoria. */
class ThumbnailLoader(private val http: OkHttpClient) {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtMost(24 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String, targetPx: Int): Bitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
                val bytes = http.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    r.body.source().use { src ->
                        src.request(MAX_BYTES + 1)
                        if (src.buffer.size > MAX_BYTES) return@runCatching null
                        src.readByteArray()
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.also { cache.put(url, it) }
            }.getOrNull()
        }
    }

    private companion object {
        const val MAX_BYTES = 3L * 1024 * 1024
    }
}

/**
 * Feeds que se crean en el primer arranque (URLs verificadas con curl: RSS/Atom valido). [list] es el conjunto
 * en español (el de siempre); [english] el de quien instala con la app en inglés. Solo se siembra con la base vacia.
 */
object DefaultFeeds {
    val english: List<Pair<String, String>> = listOf(
        "BBC News" to "https://feeds.bbci.co.uk/news/rss.xml",
        "The Guardian" to "https://www.theguardian.com/world/rss",
        "NPR News" to "https://feeds.npr.org/1001/rss.xml",
        "The Verge" to "https://www.theverge.com/rss/index.xml",
        "Ars Technica" to "https://feeds.arstechnica.com/arstechnica/index",
        "Hacker News" to "https://news.ycombinator.com/rss",
        "TechCrunch" to "https://techcrunch.com/feed/",
        "9to5Google" to "https://9to5google.com/feed/",
    )

    /** Conjunto según el idioma de la app (recurso `feeds_language`: «es» / «en»). */
    fun forLanguage(language: String): List<Pair<String, String>> = if (language == "en") english else list

    val list: List<Pair<String, String>> = listOf(
        "El País" to "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada",
        "elDiario.es" to "https://www.eldiario.es/rss/",
        "20minutos" to "https://www.20minutos.es/rss/",
        "Xataka" to "https://www.xataka.com/index.xml",
        "Genbeta" to "https://www.genbeta.com/index.xml",
        "Applesfera" to "https://www.applesfera.com/index.xml",
        "La Vanguardia" to "https://www.lavanguardia.com/rss/home.xml",
        "El Periódico" to "https://www.elperiodico.com/es/rss/rss_portada.xml",
        "324" to "https://api.3cat.cat/noticies?_format=rss&origen=frontal&frontal=n324-portada-noticia&version=2.0",
    )
}
