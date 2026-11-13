package com.usos.launcher.data.sections

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.usos.launcher.data.feeds.FeedLimits
import com.usos.launcher.data.suggest.SuggestProvider
import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val Context.usosDataStore: DataStore<Preferences> by preferencesDataStore(name = "usos")

/** AppConfig de todas las apps, como un único JSON en DataStore. */
class AppConfigStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("app_configs")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(AppConfig.serializer())

    val configs: Flow<Map<String, AppConfig>> =
        dataStore.data.map { decode(it[key]) }.distinctUntilChanged()

    /** Reparte las apps nuevas o cambiadas y guarda el resultado si hay cambios. */
    suspend fun reconcile(apps: List<AppEntry>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key])
            val next = SectionAssigner.reconcile(apps, current)
            if (next != current) prefs[key] = json.encodeToString(serializer, next.values.toList())
        }
    }

    /** Modifica la configuracion guardada con [transform]; solo escribe si hay cambios. */
    suspend fun update(transform: (Map<String, AppConfig>) -> Map<String, AppConfig>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key])
            val next = transform(current)
            if (next != current) prefs[key] = json.encodeToString(serializer, next.values.toList())
        }
    }

    private fun decode(raw: String?): Map<String, AppConfig> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .associateBy { it.key }
    }
}

const val DEFAULT_ACCENT: Int = 0xFFFF6A3D.toInt()

/** Subconjunto de Settings necesario en la Fase 1. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    private val accentKey = intPreferencesKey("accent")
    private val lastOpenKey = stringPreferencesKey("last_open_section")

    val accent: Flow<Int> = dataStore.data.map { it[accentKey] ?: DEFAULT_ACCENT }.distinctUntilChanged()

    val lastOpenSection: Flow<Section?> = dataStore.data.map { prefs ->
        prefs[lastOpenKey]?.let { name -> Section.entries.firstOrNull { it.name == name } }
    }.distinctUntilChanged()

    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    /** false hasta que el usuario termina o salta el primer arranque. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[onboardingKey] ?: false }.distinctUntilChanged()

    private val suggestionsKey = booleanPreferencesKey("suggestions_on")
    private val providerKey = stringPreferencesKey("suggest_provider")
    private val grayscaleKey = booleanPreferencesKey("widgets_grayscale")

    /** Sugerencias web al teclear (envian cada pulsacion al proveedor). Activadas por defecto. */
    val suggestionsOn: Flow<Boolean> = dataStore.data.map { it[suggestionsKey] ?: true }.distinctUntilChanged()
    val suggestProvider: Flow<SuggestProvider> =
        dataStore.data.map { SuggestProvider.fromName(it[providerKey]) }.distinctUntilChanged()

    /** Escala de grises global de los widgets. Activada por defecto. */
    val widgetsGrayscale: Flow<Boolean> = dataStore.data.map { it[grayscaleKey] ?: true }.distinctUntilChanged()

    private val perHourKey = intPreferencesKey("feeds_per_hour")
    private val feedsHiddenKey = booleanPreferencesKey("feeds_hidden")
    private val calendarAskedKey = booleanPreferencesKey("calendar_asked")

    /** Artículos que el panel de noticias desvela por hora de reloj (3..10). */
    val feedsPerHour: Flow<Int> = dataStore.data
        .map { FeedLimits.clamp(it[perHourKey] ?: FeedLimits.DEFAULT_PER_HOUR) }.distinctUntilChanged()
    val feedsHidden: Flow<Boolean> = dataStore.data.map { it[feedsHiddenKey] ?: false }.distinctUntilChanged()
    val calendarAsked: Flow<Boolean> = dataStore.data.map { it[calendarAskedKey] ?: false }.distinctUntilChanged()

    suspend fun setFeedsPerHour(n: Int) { dataStore.edit { it[perHourKey] = FeedLimits.clamp(n) } }
    suspend fun setFeedsHidden(hidden: Boolean) { dataStore.edit { it[feedsHiddenKey] = hidden } }
    suspend fun setCalendarAsked(asked: Boolean) { dataStore.edit { it[calendarAskedKey] = asked } }

    suspend fun setSuggestionsOn(on: Boolean) { dataStore.edit { it[suggestionsKey] = on } }
    suspend fun setSuggestProvider(p: SuggestProvider) { dataStore.edit { it[providerKey] = p.name } }
    suspend fun setWidgetsGrayscale(on: Boolean) { dataStore.edit { it[grayscaleKey] = on } }

    suspend fun setOnboardingDone(done: Boolean) { dataStore.edit { it[onboardingKey] = done } }

    suspend fun setAccent(argb: Int) { dataStore.edit { it[accentKey] = argb } }

    suspend fun setLastOpenSection(section: Section?) {
        dataStore.edit { if (section == null) it.remove(lastOpenKey) else it[lastOpenKey] = section.name }
    }
}
