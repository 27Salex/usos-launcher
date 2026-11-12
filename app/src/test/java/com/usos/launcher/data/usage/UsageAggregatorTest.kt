package com.usos.launcher.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageAggregatorTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun t(d: String, h: Int, m: Int, s: Int = 0): Long =
        LocalDateTime.parse(d + "T00:00:00").withHour(h).withMinute(m).withSecond(s)
            .atZone(zone).toInstant().toEpochMilli()

    private fun ev(pkg: String, kind: UsageEventKind, time: Long, cls: String = "A") =
        UsageEventRecord(pkg, cls, kind, time)

    private val start = t("2026-11-07", 0, 0)
    private val end = t("2026-11-13", 18, 0)
    private fun agg(vararg e: UsageEventRecord) = UsageAggregator.aggregate(e.toList(), start, end, zone)
    private val d13 = LocalDate.of(2026, 11, 13)
    private val d12 = LocalDate.of(2026, 11, 12)

    @Test fun simpleSessionCountsMinutesAndOpen() {
        val r = agg(
            ev("p", UsageEventKind.RESUMED, t("2026-11-13", 9, 0)),
            ev("p", UsageEventKind.PAUSED, t("2026-11-13", 9, 12, 30)),
        )
        assertEquals(DayUsage(12 * 60_000 + 30_000L, 1), r["p"]!![d13])
    }

    @Test fun sessionCrossingMidnightIsSplit() {
        val r = agg(
            ev("p", UsageEventKind.RESUMED, t("2026-11-12", 23, 50)),
            ev("p", UsageEventKind.PAUSED, t("2026-11-13", 0, 20)),
        )
        assertEquals(DayUsage(10 * 60_000L, 1), r["p"]!![d12])
        assertEquals(DayUsage(20 * 60_000L, 0), r["p"]!![d13])
    }

    @Test fun twoActivitiesOfSameAppAreOneSession() {
        val r = agg(
            ev("p", UsageEventKind.RESUMED, t("2026-11-13", 10, 0), "A"),
            ev("p", UsageEventKind.RESUMED, t("2026-11-13", 10, 5), "B"),
            ev("p", UsageEventKind.PAUSED, t("2026-11-13", 10, 5), "A"),
            ev("p", UsageEventKind.PAUSED, t("2026-11-13", 10, 10), "B"),
        )
        assertEquals(DayUsage(10 * 60_000L, 1), r["p"]!![d13])
    }

    @Test fun openSessionAtWindowEndIsClosedAtEnd() {
        val r = agg(ev("p", UsageEventKind.RESUMED, t("2026-11-13", 17, 30)))
        assertEquals(30 * 60_000L, r["p"]!![d13]!!.foregroundMs)
    }

    @Test fun pauseWithoutResumeCountsFromWindowStart() {
        val r = agg(ev("p", UsageEventKind.PAUSED, t("2026-11-07", 0, 5)))
        assertEquals(5 * 60_000L, r["p"]!![LocalDate.of(2026, 11, 7)]!!.foregroundMs)
    }

    @Test fun shutdownClosesSessions() {
        val r = agg(
            ev("p", UsageEventKind.RESUMED, t("2026-11-13", 8, 0)),
            UsageEventRecord("", "", UsageEventKind.SHUTDOWN, t("2026-11-13", 8, 3)),
        )
        assertEquals(3 * 60_000L, r["p"]!![d13]!!.foregroundMs)
    }

    @Test fun summaryBuildsBarsWeekAndOpens() {
        val r = agg(
            ev("p", UsageEventKind.RESUMED, t("2026-11-12", 23, 50)),
            ev("p", UsageEventKind.PAUSED, t("2026-11-13", 0, 20)),
            ev("p", UsageEventKind.RESUMED, t("2026-11-13", 9, 0)),
            ev("p", UsageEventKind.PAUSED, t("2026-11-13", 9, 5)),
        )
        val s = UsageSummary.build(r["p"]!!, d13) // viernes
        assertEquals(7, s.barsMs.size)
        assertEquals(25, s.todayMin)
        assertEquals(35, s.weekMin)
        assertEquals(2, s.opens7)
        assertEquals(10 * 60_000L, s.barsMs[5])
    }

    @Test fun formatDuration() {
        assertEquals("12 MIN", UsageSummary.format(12))
        assertEquals("1 H 05", UsageSummary.format(65))
    }
}
