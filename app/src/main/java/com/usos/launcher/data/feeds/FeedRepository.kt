package com.usos.launcher.data.feeds

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Descarga y analiza un feed. Interfaz para poder sustituirla en tests. */
interface FeedFetcher {
    @Throws(IOException::class)
    suspend fun fetch(url: String): ParsedFeed
}

class OkHttpFeedFetcher(private val http: OkHttpClient) : FeedFetcher {
    override suspend fun fetch(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val parser = Xml.newPullParser()
            parser.setInput(response.body.byteStream(), null)
            try {
                FeedParser.parse(parser)
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                throw IOException("Feed no válido", e)
            }
        }
    }
}

sealed interface AddResult {
    data object Added : AddResult
    data object InvalidUrl : AddResult
    data object Duplicate : AddResult
    data object NetworkError : AddResult
}

class FeedRepository(
    private val dao: FeedDao,
    private val fetcher: FeedFetcher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val feeds = dao.feeds()
    val articles = dao.articles()

    suspend fun addFeed(rawUrl: String): AddResult {
        val url = FeedFormat.normalizeUrl(rawUrl) ?: return AddResult.InvalidUrl
        if (dao.feedsNow().any { it.url == url }) return AddResult.Duplicate
        val parsed = try {
            fetcher.fetch(url)
        } catch (e: IOException) {
            return AddResult.NetworkError
        }
        val title = parsed.title?.takeIf { it.isNotBlank() }
            ?: url.substringAfter("://").substringBefore('/')
        val id = dao.insertFeed(Feed(url = url, title = title, lastFetchedAt = now()))
        store(id, parsed)
        return AddResult.Added
    }

    /** Actualiza todos los feeds; true si todos respondieron. Un feed caído no detiene a los demás. */
    suspend fun refreshAll(): Boolean {
        var ok = true
        for (feed in dao.feedsNow()) {
            try {
                store(feed.id, fetcher.fetch(feed.url))
                dao.setFetched(feed.id, now())
            } catch (e: IOException) {
                ok = false
            }
        }
        return ok
    }

    /** Crea los feeds por defecto (sin descargar) si no hay ninguno; true si los creo. */
    suspend fun seedDefaults(defaults: List<Pair<String, String>>): Boolean {
        if (dao.feedsNow().isNotEmpty()) return false
        defaults.forEach { (title, url) -> runCatching { dao.insertFeed(Feed(url = url, title = title)) } }
        return true
    }

    suspend fun lastFetchedAt(): Long? = dao.feedsNow().minOfOrNull { it.lastFetchedAt }

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
        dao.prune(feedId)
    }

    suspend fun rename(id: Long, title: String) {
        val t = title.trim()
        if (t.isNotEmpty()) dao.rename(id, t)
    }

    suspend fun delete(id: Long) = dao.deleteFeed(id)
    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)
    suspend fun reveal(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.reveal(ids, now())
    }
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
                val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
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

/** Feeds que se crean en el primer arranque (URLs verificadas con curl: RSS 2.0 valido). */
object DefaultFeeds {
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
