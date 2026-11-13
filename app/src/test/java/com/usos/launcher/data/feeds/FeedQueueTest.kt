package com.usos.launcher.data.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FeedQueueTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, 13, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun art(id: Long, published: Long = id, revealedAt: Long? = null, read: Boolean = false, starred: Boolean = false) =
        Article(id = id, feedId = 1, title = "t$id", summary = "", url = "u$id", imageUrl = null,
            publishedAt = published, read = read, starred = starred, revealedAt = revealedAt)

    @Test
    fun revealsOnlyPerHourNewestFirst() {
        val plan = FeedQueue.plan((1L..8L).map { art(it) }, at(18, 10), zone, 5)
        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), plan.toReveal)
        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), plan.visible.map { it.id })
        assertEquals(3, plan.queued)
        assertEquals(at(19), plan.nextRevealAt)
    }

    @Test
    fun alreadyRevealedThisHourUseUpTheQuota() {
        val articles = (1L..3L).map { art(it, revealedAt = at(18, 5)) } + (4L..9L).map { art(it) }
        val plan = FeedQueue.plan(articles, at(18, 40), zone, 5)
        assertEquals(listOf(9L, 8L), plan.toReveal)
        assertEquals(4, plan.queued)
    }

    @Test
    fun readOnesStillCountForTheHour() {
        val articles = (1L..5L).map { art(it, revealedAt = at(18, 1), read = true) } + art(6)
        val plan = FeedQueue.plan(articles, at(18, 50), zone, 5)
        assertTrue(plan.toReveal.isEmpty())
        assertEquals(1, plan.queued)
        assertTrue(plan.visible.isEmpty()) // leídos sin estrella desaparecen
    }

    @Test
    fun newClockHourResetsQuota() {
        val articles = (1L..5L).map { art(it, revealedAt = at(18, 59)) } + (6L..8L).map { art(it) }
        assertTrue(FeedQueue.plan(articles, at(18, 59), zone, 5).toReveal.isEmpty())
        val plan = FeedQueue.plan(articles, at(19, 0), zone, 5)
        assertEquals(listOf(8L, 7L, 6L), plan.toReveal)
        assertEquals(0, plan.queued)
        assertNull(plan.nextRevealAt)
    }

    @Test
    fun revealedStayVisibleUntilReadExceptStarred() {
        val articles = listOf(
            art(1, revealedAt = at(10)),
            art(2, revealedAt = at(10), read = true),
            art(3, revealedAt = at(10), read = true, starred = true),
            art(4, revealedAt = at(10), starred = true),
        )
        val plan = FeedQueue.plan(articles, at(18), zone, 5)
        assertEquals(listOf(4L, 3L, 1L), plan.visible.map { it.id })
    }

    @Test
    fun limitIsClampedBetween3And10() {
        val articles = (1L..20L).map { art(it) }
        assertEquals(3, FeedQueue.plan(articles, at(9), zone, 1).toReveal.size)
        assertEquals(10, FeedQueue.plan(articles, at(9), zone, 99).toReveal.size)
    }

    @Test
    fun queueLabel() {
        val plan = FeedQueue.plan((1L..12L).map { art(it) }, at(18, 10), zone, 5)
        assertEquals("5 MÁS A LAS 19:00", FeedQueue.queueLabel(plan, 5, zone))
        val small = FeedQueue.plan((1L..6L).map { art(it) }, at(18, 10), zone, 5)
        assertEquals("1 MÁS A LAS 19:00", FeedQueue.queueLabel(small, 5, zone))
        assertNull(FeedQueue.queueLabel(FeedQueue.plan(emptyList(), at(18), zone, 5), 5, zone))
    }

    @Test
    fun ageFormat() {
        val now = at(12)
        assertEquals("AHORA", FeedFormat.age(now - 20_000, now))
        assertEquals("12 MIN", FeedFormat.age(now - 12 * 60_000, now))
        assertEquals("3 H", FeedFormat.age(now - 3 * 3_600_000, now))
        assertEquals("2 D", FeedFormat.age(now - 49 * 3_600_000, now))
    }

    @Test
    fun refreshPolicy() {
        val now = at(12)
        assertTrue(FeedFormat.needsRefresh(null, now))
        assertFalse(FeedFormat.needsRefresh(now - 29 * 60_000, now))
        assertTrue(FeedFormat.needsRefresh(now - 31 * 60_000, now))
    }

    @Test
    fun urlNormalization() {
        assertEquals("https://ejemplo.com/rss", FeedFormat.normalizeUrl(" ejemplo.com/rss "))
        assertEquals("http://a.org/f.xml", FeedFormat.normalizeUrl("http://a.org/f.xml"))
        assertNull(FeedFormat.normalizeUrl("ftp://a.org/f"))
        assertNull(FeedFormat.normalizeUrl("hola"))
        assertNull(FeedFormat.normalizeUrl(""))
    }
}
