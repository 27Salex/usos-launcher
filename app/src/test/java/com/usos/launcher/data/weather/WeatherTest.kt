package com.usos.launcher.data.weather

import com.usos.launcher.R

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val body = """{"latitude":40.4,"current":{"time":"2026-11-13T02:00","temperature_2m":12.8,"weather_code":3,"is_day":0},
        "daily":{"time":["2026-11-13"],"temperature_2m_max":[16.0],"temperature_2m_min":[12.3]}}"""

    @Test fun parsesCurrentAndDaily() {
        val w = Weather.parse(body, 5L)!!
        assertEquals(12.8, w.tempC, 0.001)
        assertEquals(3, w.code)
        assertFalse(w.isDay)
        assertEquals(16.0, w.maxC!!, 0.001)
        assertEquals(12.3, w.minC!!, 0.001)
        assertEquals(5L, w.fetchedAt)
    }

    @Test fun parseWithoutDailyOrBrokenJson() {
        val w = Weather.parse("""{"current":{"temperature_2m":3,"weather_code":0}}""", 1)!!
        assertNull(w.maxC)
        assertTrue(w.isDay)
        assertNull(Weather.parse("{}", 1))
        assertNull(Weather.parse("no json", 1))
    }

    @Test fun codesToTextResources() {
        assertEquals(R.string.weather_clear, Weather.textRes(0))
        assertEquals(R.string.weather_clouds, Weather.textRes(3))
        assertEquals(R.string.weather_fog, Weather.textRes(45))
        assertEquals(R.string.weather_rain, Weather.textRes(63))
        assertEquals(R.string.weather_snow, Weather.textRes(73))
        assertEquals(R.string.weather_showers, Weather.textRes(81))
        assertEquals(R.string.weather_thunderstorm, Weather.textRes(95))
        assertEquals(R.string.weather_variable, Weather.textRes(42))
    }

    @Test fun iconsDependOnDayAndCode() {
        assertEquals(WeatherIconKind.CLEAR, Weather.icon(0, true))
        assertEquals(WeatherIconKind.CLEAR_NIGHT, Weather.icon(0, false))
        assertEquals(WeatherIconKind.PARTLY, Weather.icon(2, true))
        assertEquals(WeatherIconKind.RAIN, Weather.icon(61, true))
        assertEquals(WeatherIconKind.THUNDER, Weather.icon(96, false))
        WeatherIconKind.entries.forEach { k ->
            val p = WeatherIcons.pattern(k)
            assertEquals(9, p.size)
            p.forEach { row -> assertEquals("$k $row", 9, row.length) }
        }
    }

    @Test fun label() {
        val w = WeatherNow(13.6, 3, true, 18.2, 9.4)
        assertEquals("14° · NUBES", Weather.label(w, false, "NUBES", false))
        assertEquals("14°", Weather.label(w, false, null, false))
        assertEquals("14° · NUBES · 9°/18°", Weather.label(w, false, "NUBES", true))
        assertEquals("56° · NUBES", Weather.label(w, true, "NUBES", false))
        assertEquals("-2°", Weather.label(WeatherNow(-1.6, 0, true), false, null, false))
    }

    @Test fun cacheWindows() {
        assertTrue(Weather.isFresh(0, 29 * 60_000L))
        assertFalse(Weather.isFresh(0, 30 * 60_000L))
        assertTrue(Weather.isVisible(0, 44 * 60_000L))
        assertFalse(Weather.isVisible(0, 46 * 60_000L))
    }

    @Test fun geocoding() {
        val json = """{"results":[{"name":"Madrid","latitude":40.4165,"longitude":-3.70256,"admin1":"Madrid","country":"España"},
            {"name":"Madrid","latitude":37.0,"longitude":-5.0,"admin1":"Andalucía"},{"name":"sin coords"}]}"""
        val r = Weather.parseGeocoding(json)
        assertEquals(2, r.size)
        assertEquals("Madrid", r[0].label)
        assertEquals("Madrid, Andalucía", r[1].label)
        assertTrue(Weather.parseGeocoding("{}").isEmpty())
    }

    @Test fun urls() {
        assertTrue(Weather.forecastUrl(40.4165, -3.70256).contains("latitude=40.417&longitude=-3.703"))
        assertTrue(Weather.geocodeUrl("A Coruña").contains("name=A+Coru%C3%B1a"))
    }
}
