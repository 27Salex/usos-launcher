package com.usos.launcher.data.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.Serializable

/** Un valor de DataStore con su tipo: s, b, i, l, d, f o ss (conjunto de textos). */
@Serializable
data class PrefValue(val t: String, val s: String? = null, val b: Boolean? = null, val n: Double? = null, val l: Long? = null, val ss: List<String>? = null)

@Serializable
data class FeedEntry(val url: String, val title: String)

/** Archivo de copia de Usos. Versión 1: preferencias + lista de feeds (sin artículos ni widgets de otras apps). */
@Serializable
data class ConfigFile(val app: String = APP, val version: Int = 1, val prefs: Map<String, PrefValue> = emptyMap(), val feeds: List<FeedEntry> = emptyList()) {
    companion object { const val APP = "usos-launcher" }
}

/** Lógica pura de exportar/importar la configuración. */
object ConfigBackup {
    /** Claves propias de este dispositivo o cachés: ni se exportan ni se sobrescriben al importar. */
    fun isLocal(key: String): Boolean =
        key.startsWith("update_") || key == "weather_cache" || key.startsWith("read_tokens") || key == "calendar_asked"

    fun fromPrefs(map: Map<Preferences.Key<*>, Any>): Map<String, PrefValue> = map.mapNotNull { (k, v) ->
        if (isLocal(k.name)) return@mapNotNull null
        val pv = when (v) {
            is String -> PrefValue("s", s = v)
            is Boolean -> PrefValue("b", b = v)
            is Int -> PrefValue("i", n = v.toDouble())
            is Long -> PrefValue("l", l = v)
            is Double -> PrefValue("d", n = v)
            is Float -> PrefValue("f", n = v.toDouble())
            is Set<*> -> PrefValue("ss", ss = v.map { it.toString() })
            else -> null
        } ?: return@mapNotNull null
        k.name to pv
    }.toMap()

    /** Sustituye todo lo no local por lo importado. */
    fun apply(prefs: MutablePreferences, imported: Map<String, PrefValue>) {
        prefs.asMap().keys.filter { !isLocal(it.name) }.forEach { prefs.remove(it) }
        imported.forEach { (name, v) ->
            if (isLocal(name)) return@forEach
            when (v.t) {
                "s" -> v.s?.let { prefs[stringPreferencesKey(name)] = it }
                "b" -> v.b?.let { prefs[booleanPreferencesKey(name)] = it }
                "i" -> v.n?.let { prefs[intPreferencesKey(name)] = it.toInt() }
                "l" -> v.l?.let { prefs[longPreferencesKey(name)] = it }
                "d" -> v.n?.let { prefs[doublePreferencesKey(name)] = it }
                "f" -> v.n?.let { prefs[floatPreferencesKey(name)] = it.toFloat() }
                "ss" -> v.ss?.let { prefs[stringSetPreferencesKey(name)] = it.toSet() }
            }
        }
    }
}
