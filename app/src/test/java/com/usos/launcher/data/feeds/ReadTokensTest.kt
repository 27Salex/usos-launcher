package com.usos.launcher.data.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReadTokensTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, 13, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val min = 60_000L

    @Test
    fun startsFullAndSpendingStartsRefill() {
        val s = ReadTokens.spend(null, at(18), 5)!!
        assertEquals(4, s.count)
        assertEquals(at(18), s.refilledAt)
        assertEquals(at(18) + 12 * min, ReadTokens.nextAt(s, 5)) // 60/5 = 12 min por lectura
    }

    @Test
    fun refillsContinuously() {
        var s: TokenState? = TokenState(5, at(18))
        repeat(5) { s = ReadTokens.spend(s, at(18), 5) }
        assertEquals(0, s!!.count)
        assertNull(ReadTokens.spend(s, at(18, 11), 5)) // aun no ha pasado un intervalo
        val after = ReadTokens.normalize(s, at(18, 25), 5)
        assertEquals(2, after.count) // 25 min = 2 intervalos de 12
        assertEquals(at(18) + 24 * min, after.refilledAt) // el resto cuenta para la siguiente
    }

    @Test
    fun cappedAtMax() {
        val s = ReadTokens.normalize(TokenState(0, at(10)), at(20), 5)
        assertEquals(5, s.count)
        assertNull(ReadTokens.nextAt(s, 5))
    }

    @Test
    fun lowerMaxClampsAndClockGoingBackDoesNotBlock() {
        assertEquals(3, ReadTokens.normalize(TokenState(5, at(18)), at(18), 3).count)
        val s = ReadTokens.normalize(TokenState(1, at(19)), at(18), 5) // refilledAt en el futuro
        assertEquals(1, s.count)
        assertEquals(at(18), s.refilledAt)
    }

    @Test
    fun spendKeepsPartialProgress() {
        val s = ReadTokens.spend(TokenState(2, at(18, 5)), at(18, 8), 5)!!
        assertEquals(1, s.count)
        assertEquals(at(18, 5), s.refilledAt) // no reinicia el relleno en curso
    }

    @Test
    fun labelAndPerHourRange() {
        val s = TokenState(1, at(18, 5))
        assertEquals("18:17", ReadTokens.nextTime(s, 5, zone))
        assertNull(ReadTokens.nextTime(TokenState(5, at(18)), 5, zone))
        assertEquals(3, ReadTokens.max(1))
        assertEquals(10, ReadTokens.max(99))
    }
}
