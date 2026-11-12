package com.usos.launcher.data.leisure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private class FakeClock(var now: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis(): Long = now

    fun advanceSec(s: Long) { now += s * 1000 }
    fun advanceMin(m: Long) { now += m * 60_000 }
}

class LeisureMachineTest {
    private val t0 = Instant.parse("2026-11-13T10:00:00Z").toEpochMilli()
    private val clock = FakeClock(t0)
    private val machine = LeisureMachine(clock)

    private fun openSession(min: Int = 30, wait: Int = 20) {
        assertTrue(machine.requestThaw(min, wait))
        clock.advanceSec(wait.toLong())
        assertTrue(machine.tick() is LeisureStatus.Open)
    }

    @Test fun startsFrozenWithFullBudget() {
        assertEquals(LeisureStatus.Frozen(60), machine.tick())
        assertEquals(listOf(15, 30, 60), (machine.tick() as LeisureStatus.Frozen).durations)
    }

    @Test fun onlyDurationsThatFitAreOffered() {
        val s = LeisureMachine(clock, LeisureState(spentTodayMin = 35, day = t0 / 86_400_000L)).tick()
        assertEquals(LeisureStatus.Frozen(25), s)
        assertEquals(listOf(15), (s as LeisureStatus.Frozen).durations)
        assertEquals(15, LeisureLimits.defaultDuration(s.durations))
        assertEquals(30, LeisureLimits.defaultDuration(listOf(15, 30, 60)))
    }

    @Test fun frozenToThawingAndBackOnCancel() {
        assertTrue(machine.requestThaw(30, 20))
        assertEquals(LeisureStatus.Thawing(20, 20, 30), machine.tick())
        clock.advanceSec(5)
        assertEquals(LeisureStatus.Thawing(15, 20, 30), machine.tick())
        machine.cancelThaw()
        assertEquals(LeisureStatus.Frozen(60), machine.tick())
        assertNull(machine.state.sessionEndsAt)
        assertEquals(0, machine.state.spentTodayMin)
    }

    @Test fun thawRefusedIfDurationDoesNotFit() {
        val m = LeisureMachine(clock, LeisureState(spentTodayMin = 40, day = t0 / 86_400_000L))
        assertFalse(m.requestThaw(30, 20))
        assertTrue(m.requestThaw(15, 20))
    }

    @Test fun thawRefusedWithoutBudget() {
        val m = LeisureMachine(clock, LeisureState(spentTodayMin = 50, day = t0 / 86_400_000L))
        assertFalse(m.requestThaw(30, 20))
        assertEquals(LeisureStatus.NoBudget, m.tick())
    }

    @Test fun thawRefusedWhenAlreadyOpen() {
        openSession()
        assertFalse(machine.requestThaw(15, 20))
    }

    @Test fun waitEndsInOpenWithTimes() {
        assertTrue(machine.requestThaw(30, 20))
        clock.advanceSec(19)
        assertTrue(machine.tick() is LeisureStatus.Thawing)
        clock.advanceSec(1)
        val s = machine.tick() as LeisureStatus.Open
        assertEquals(30, s.minutesLeft)
        assertEquals(clock.now, machine.state.sessionStartedAt)
        assertEquals(clock.now + 30 * 60_000, machine.state.sessionEndsAt)
    }

    @Test fun openMinutesLeftRoundsUp() {
        openSession(30)
        clock.advanceMin(10)
        clock.advanceSec(1)
        assertEquals(20, (machine.tick() as LeisureStatus.Open).minutesLeft)
    }

    @Test fun freezeChargesElapsedRoundedUp() {
        openSession(30)
        clock.advanceSec(61)
        assertEquals(LeisureStatus.Frozen(58), machine.freeze())
        assertEquals(2, machine.state.spentTodayMin)
        assertNull(machine.state.sessionStartedAt)
        assertNull(machine.state.sessionEndsAt)
    }

    @Test fun freezeRightAwayCostsNothing() {
        openSession(30)
        machine.freeze()
        assertEquals(0, machine.state.spentTodayMin)
    }

    @Test fun freezeExactMinutesNotRoundedFurther() {
        openSession(30)
        clock.advanceMin(10)
        machine.freeze()
        assertEquals(10, machine.state.spentTodayMin)
    }

    @Test fun reachingSessionEndFreezesAndChargesWhole() {
        openSession(15)
        clock.advanceMin(15)
        assertEquals(LeisureStatus.Frozen(45), machine.tick())
        assertEquals(15, machine.state.spentTodayMin)
    }

    @Test fun lateTickAfterEndChargesOnlyTheSession() {
        openSession(15)
        clock.advanceMin(200)
        machine.tick()
        assertEquals(15, machine.state.spentTodayMin)
    }

    @Test fun fullBudgetSpentGivesNoBudget() {
        openSession(60)
        clock.advanceMin(60)
        assertEquals(LeisureStatus.NoBudget, machine.tick())
        assertFalse(machine.requestThaw(15, 5))
    }

    @Test fun lessThanSmallestPillIsNoBudget() {
        val m = LeisureMachine(clock, LeisureState(spentTodayMin = 50, day = t0 / 86_400_000L))
        assertEquals(LeisureStatus.NoBudget, m.tick())
    }

    @Test fun noBudgetResetsAtMidnight() {
        openSession(60)
        clock.advanceMin(60)
        assertEquals(LeisureStatus.NoBudget, machine.tick())
        clock.now = Instant.parse("2026-11-14T00:00:00Z").toEpochMilli()
        assertEquals(LeisureStatus.Frozen(60), machine.tick())
        assertEquals(0, machine.state.spentTodayMin)
    }

    @Test fun dayChangeResetsWhileFrozen() {
        openSession(30)
        clock.advanceMin(30)
        machine.tick()
        assertEquals(30, machine.state.spentTodayMin)
        clock.now = Instant.parse("2026-11-14T08:00:00Z").toEpochMilli()
        assertEquals(LeisureStatus.Frozen(60), machine.tick())
    }

    @Test fun dayChangeDuringThawingKeepsThawing() {
        clock.now = Instant.parse("2026-11-13T23:59:50Z").toEpochMilli()
        assertTrue(machine.requestThaw(15, 20))
        clock.advanceSec(15)
        assertTrue(machine.tick() is LeisureStatus.Thawing)
        clock.advanceSec(5)
        assertTrue(machine.tick() is LeisureStatus.Open)
    }

    @Test fun sessionCrossingMidnightCountsAgainstStartDay() {
        clock.now = Instant.parse("2026-11-13T23:00:00Z").toEpochMilli()
        // gasta 15 min antes, el mismo dia
        openSession(15)
        clock.advanceMin(15)
        machine.tick()
        assertEquals(15, machine.state.spentTodayMin)
        // sesion de 30 min que empieza a las 23:55
        clock.now = Instant.parse("2026-11-13T23:55:00Z").toEpochMilli()
        openSession(30, wait = 5)
        val startDay = machine.state.day
        clock.now = Instant.parse("2026-11-14T00:10:00Z").toEpochMilli()
        // sigue abierta tras la medianoche y no repone mientras dure
        assertTrue(machine.tick() is LeisureStatus.Open)
        assertEquals(startDay, machine.state.day)
        assertEquals(15, machine.state.spentTodayMin)
        // se congela a mano: los minutos cuentan contra el dia 13, hoy queda entero
        val s = machine.freeze()
        assertEquals(LeisureStatus.Frozen(60), s)
        assertEquals(0, machine.state.spentTodayMin)
        assertEquals(Instant.parse("2026-11-14T00:00:00Z").toEpochMilli() / 86_400_000L, machine.state.day)
    }

    @Test fun sessionCrossingMidnightEndsAndResets() {
        clock.now = Instant.parse("2026-11-13T23:50:00Z").toEpochMilli()
        openSession(30, wait = 5)
        clock.now = Instant.parse("2026-11-14T00:30:00Z").toEpochMilli()
        assertEquals(LeisureStatus.Frozen(60), machine.tick())
        assertEquals(0, machine.state.spentTodayMin)
    }

    @Test fun restartMidSessionKeepsItOpen() {
        openSession(30)
        clock.advanceMin(10)
        val persisted = machine.state
        // el movil se reinicia y el reloj sigue
        clock.advanceMin(5)
        val restarted = LeisureMachine(clock, persisted)
        val s = restarted.tick() as LeisureStatus.Open
        assertEquals(15, s.minutesLeft)
        restarted.freeze()
        assertEquals(15, restarted.state.spentTodayMin)
    }

    @Test fun restartAfterSessionExpiredClosesAndChargesWhole() {
        openSession(30)
        val persisted = machine.state
        clock.advanceMin(180)
        val restarted = LeisureMachine(clock, persisted)
        assertEquals(LeisureStatus.Frozen(30), restarted.tick())
        assertEquals(30, restarted.state.spentTodayMin)
        assertNull(restarted.state.sessionStartedAt)
    }

    @Test fun restartExpiredNextDayChargesStartDayAndResets() {
        clock.now = Instant.parse("2026-11-13T23:40:00Z").toEpochMilli()
        openSession(30, wait = 5)
        val persisted = machine.state
        clock.now = Instant.parse("2026-11-14T09:00:00Z").toEpochMilli()
        val restarted = LeisureMachine(clock, persisted)
        assertEquals(LeisureStatus.Frozen(60), restarted.tick())
    }

    @Test fun cancelThawDoesNotTouchBudget() {
        machine.requestThaw(60, 10)
        machine.cancelThaw()
        clock.advanceSec(60)
        assertEquals(LeisureStatus.Frozen(60), machine.tick())
    }

    @Test fun clockGoingBackwardsNeverChargesNegative() {
        openSession(30)
        clock.now -= 5 * 60_000
        machine.freeze()
        assertEquals(0, machine.state.spentTodayMin)
    }

    @Test fun budgetClampedToStepsAndRange() {
        assertEquals(30, LeisureLimits.clampBudget(10))
        assertEquals(120, LeisureLimits.clampBudget(500))
        assertEquals(75, LeisureLimits.clampBudget(74))
        machine.setBudget(90)
        assertEquals(LeisureStatus.Frozen(90), machine.tick())
        assertEquals(5, LeisureLimits.clampWait(1))
        assertEquals(60, LeisureLimits.clampWait(100))
    }
}
