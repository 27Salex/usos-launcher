package com.usos.launcher.data.weather

import androidx.annotation.StringRes
import com.usos.launcher.R

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Tiempo actual (siempre en Celsius; la conversion a Fahrenheit se hace al mostrarlo). */
@Serializable
data class WeatherNow(
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val maxC: Double? = null,
    val minC: Double? = null,
    val fetchedAt: Long = 0,
)

enum class WeatherIconKind { CLEAR, CLEAR_NIGHT, PARTLY, CLOUDS, FOG, RAIN, THUNDER, SNOW }

/** Ciudad devuelta por la API de geocodificacion. */
data class GeoPlace(val name: String, val region: String?, val country: String?, val lat: Double, val lon: Double) {
    val label: String get() = listOfNotNull(name, region?.takeIf { it != name }).joinToString(", ")
}

/** Todo el codigo de la meteorologia que no depende de Android. */
object Weather {
    const val CACHE_MS = 30 * 60 * 1000L

    /** Pasado este tiempo sin poder refrescar, el dato deja de mostrarse. */
    const val VISIBLE_MS = 45 * 60 * 1000L

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(java.util.Locale.US, lat)}" +
            "&longitude=${"%.3f".format(java.util.Locale.US, lon)}" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"

    fun geocodeUrl(name: String, language: String = "es"): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=" +
            java.net.URLEncoder.encode(name.trim(), "UTF-8") + "&count=6&language=$language&format=json"

    fun isFresh(fetchedAt: Long, now: Long): Boolean = now - fetchedAt in 0 until CACHE_MS
    fun isVisible(fetchedAt: Long, now: Long): Boolean = now - fetchedAt in 0 until VISIBLE_MS

    /** Analiza la respuesta de /v1/forecast; null si no trae temperatura y codigo. */
    fun parse(json: String, fetchedAt: Long): WeatherNow? = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        val cur = root["current"]?.jsonObject ?: return null
        val temp = cur["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return null
        val code = cur["weather_code"]?.jsonPrimitive?.intOrNull ?: return null
        val isDay = (cur["is_day"]?.jsonPrimitive?.intOrNull ?: 1) != 0
        val daily = root["daily"] as? JsonObject
        fun first(key: String): Double? =
            (daily?.get(key) as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.doubleOrNull }
        WeatherNow(temp, code, isDay, first("temperature_2m_max"), first("temperature_2m_min"), fetchedAt)
    }.getOrNull()

    /** Analiza la respuesta del buscador de ciudades. */
    fun parseGeocoding(json: String): List<GeoPlace> = runCatching {
        val results = Json.parseToJsonElement(json).jsonObject["results"]?.jsonArray ?: return emptyList()
        results.mapNotNull { e ->
            val o = e.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val lat = o["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = o["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            GeoPlace(name, o["admin1"]?.jsonPrimitive?.contentOrNull, o["country"]?.jsonPrimitive?.contentOrNull, lat, lon)
        }
    }.getOrDefault(emptyList())

    /** Recurso del texto de estado (WMO weather_code); cada idioma lo traduce en strings.xml. */
    @StringRes
    fun textRes(code: Int): Int = when (code) {
        0 -> R.string.weather_clear
        1 -> R.string.weather_mostly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_clouds
        45, 48 -> R.string.weather_fog
        in 51..57 -> R.string.weather_drizzle
        in 61..67 -> R.string.weather_rain
        in 71..77 -> R.string.weather_snow
        in 80..82 -> R.string.weather_showers
        85, 86 -> R.string.weather_snow
        95, 96, 99 -> R.string.weather_thunderstorm
        else -> R.string.weather_variable
    }

    fun icon(code: Int, isDay: Boolean): WeatherIconKind = when (code) {
        0, 1 -> if (isDay) WeatherIconKind.CLEAR else WeatherIconKind.CLEAR_NIGHT
        2 -> if (isDay) WeatherIconKind.PARTLY else WeatherIconKind.CLOUDS
        3 -> WeatherIconKind.CLOUDS
        45, 48 -> WeatherIconKind.FOG
        in 51..67, in 80..82 -> WeatherIconKind.RAIN
        in 71..77, 85, 86 -> WeatherIconKind.SNOW
        95, 96, 99 -> WeatherIconKind.THUNDER
        else -> WeatherIconKind.CLOUDS
    }

    fun degrees(celsius: Double, fahrenheit: Boolean): Int {
        val v = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius
        return Math.round(v).toInt()
    }

    /** «14° · NUBES», o «14°» / «14° · NUBES · 9°/18°» segun las opciones; [statusText] ya traducido (null = sin texto). */
    fun label(w: WeatherNow, fahrenheit: Boolean, statusText: String?, showMinMax: Boolean): String {
        val parts = mutableListOf("${degrees(w.tempC, fahrenheit)}°")
        if (statusText != null) parts += statusText
        if (showMinMax && w.minC != null && w.maxC != null) {
            parts += "${degrees(w.minC, fahrenheit)}°/${degrees(w.maxC, fahrenheit)}°"
        }
        return parts.joinToString(" · ")
    }
}

/** Iconos de 9x9 puntos (mismo lenguaje visual que el reloj de puntos). '#' = punto encendido. */
object WeatherIcons {
    const val SIZE = 9

    private val CLOUD_TOP = listOf("...###...", "..#####..", ".#######.", "#########", ".#######.")

    private fun cloud(vararg below: String) = CLOUD_TOP + below.toList()

    fun pattern(kind: WeatherIconKind): List<String> = when (kind) {
        WeatherIconKind.CLEAR -> listOf(
            "....#....", ".#.....#.", "...###...", "..#####..", "#.#####.#",
            "..#####..", "...###...", ".#.....#.", "....#....",
        )
        WeatherIconKind.CLEAR_NIGHT -> listOf(
            "...####..", "..###....", ".###.....", ".###.....", ".###.....",
            ".###.....", "..###..##", "...######", ".....###.",
        )
        WeatherIconKind.PARTLY -> listOf(
            "..#......", ".###..##.", "#####.###", ".###.####", "..#.#####",
            "...######", "...######", "....####.", ".........",
        )
        WeatherIconKind.CLOUDS -> listOf(
            ".........", "...###...", "..#####..", ".#######.", "#########",
            "#########", ".#######.", ".........", ".........",
        )
        WeatherIconKind.FOG -> listOf(
            ".........", ".#######.", ".........", "#######..", ".........",
            "..#######", ".........", ".#######.", ".........",
        )
        WeatherIconKind.RAIN -> cloud(".........", "#..#..#..", ".#..#..#.", "#..#..#..")
        WeatherIconKind.THUNDER -> cloud("....##...", "...##....", "..####...", "....#....")
        WeatherIconKind.SNOW -> cloud(".........", "#...#...#", "..#...#..", "#...#...#")
    }
}
