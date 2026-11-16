package com.usos.launcher.data.sections

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.usos.launcher.data.feeds.FeedLimits
import com.usos.launcher.data.feeds.ReadTokens
import com.usos.launcher.data.feeds.TokenState
import com.usos.launcher.data.suggest.SearchEngine
import com.usos.launcher.data.suggest.SuggestProvider
import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val Context.usosDataStore: DataStore<Preferences> by preferencesDataStore(name = "usos")

/** AppConfig de todas las apps, como un único JSON en DataStore. */
class AppConfigStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("app_configs")
    private val json = com.usos.launcher.data.PersistJson
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

    private val notifModeKey = stringPreferencesKey("notif_indicator")

    /** Aviso de notificaciones en las piezas: NONE (defecto) / DOT / BORDER. */
    val notifMode: Flow<com.usos.launcher.data.notify.NotifMode> =
        dataStore.data.map { p -> p[notifModeKey]?.let { com.usos.launcher.data.notify.NotifMode.fromName(it) } ?: com.usos.launcher.data.notify.NotifMode.DOT }.distinctUntilChanged()
    suspend fun setNotifMode(m: com.usos.launcher.data.notify.NotifMode) { dataStore.edit { it[notifModeKey] = m.name } }

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

    private val thumbsGrayKey = booleanPreferencesKey("feeds_thumbs_gray")

    /** Miniaturas de noticias en gris (solo al dibujar; la caché guarda el color). Apagado por defecto. */
    val thumbsGray: Flow<Boolean> = dataStore.data.map { it[thumbsGrayKey] ?: false }.distinctUntilChanged()
    suspend fun setThumbsGray(on: Boolean) { dataStore.edit { it[thumbsGrayKey] = on } }

    private val perHourKey = intPreferencesKey("feeds_per_hour")
    private val feedsHiddenKey = booleanPreferencesKey("feeds_hidden")
    private val feedsKeepKey = intPreferencesKey("feeds_keep")
    private val searchKeyboardKey = booleanPreferencesKey("search_keyboard")
    private val clockTapKey = stringPreferencesKey("clock_tap")
    private val assistantKey = stringPreferencesKey("ai_assistant")
    private val calendarAskedKey = booleanPreferencesKey("calendar_asked")

    /** Lecturas por hora (3..10): tamaño del cubo de lecturas y ritmo de relleno. */
    val feedsPerHour: Flow<Int> = dataStore.data
        .map { FeedLimits.clamp(it[perHourKey] ?: FeedLimits.DEFAULT_PER_HOUR) }.distinctUntilChanged()
    val feedsHidden: Flow<Boolean> = dataStore.data.map { it[feedsHiddenKey] ?: false }.distinctUntilChanged()
    val calendarAsked: Flow<Boolean> = dataStore.data.map { it[calendarAskedKey] ?: false }.distinctUntilChanged()

    /** Artículos que se conservan por feed (10/20/30/50, defecto 20); los guardados nunca se podan. */
    val feedsKeep: Flow<Int> = dataStore.data
        .map { FeedLimits.clampKeep(it[feedsKeepKey] ?: FeedLimits.DEFAULT_KEEP) }.distinctUntilChanged()
    suspend fun setFeedsKeep(n: Int) { dataStore.edit { it[feedsKeepKey] = FeedLimits.clampKeep(n) } }

    private val feedsRefreshKey = intPreferencesKey("feeds_refresh_min")
    /** Minutos de antigüedad para descargar al abrir Noticias; 0 = solo manual. */
    val feedsRefreshMin: Flow<Int> = dataStore.data
        .map { FeedLimits.clampRefresh(it[feedsRefreshKey] ?: FeedLimits.DEFAULT_REFRESH_MIN) }.distinctUntilChanged()
    suspend fun setFeedsRefreshMin(n: Int) { dataStore.edit { it[feedsRefreshKey] = FeedLimits.clampRefresh(n) } }

    /** Al tocar el reloj: "SYSTEM" (defecto), "NONE" o "APP:<paquete>". */
    val clockTap: Flow<String> = dataStore.data.map { it[clockTapKey] ?: "SYSTEM" }.distinctUntilChanged()
    suspend fun setClockTap(v: String) { dataStore.edit { it[clockTapKey] = v } }

    /** Abrir el teclado al abrir la búsqueda. Apagado por defecto. */
    val searchKeyboard: Flow<Boolean> = dataStore.data.map { it[searchKeyboardKey] ?: false }.distinctUntilChanged()
    suspend fun setSearchKeyboard(on: Boolean) { dataStore.edit { it[searchKeyboardKey] = on } }

    /**
     * Palabras para filtrar solo web apps en la búsqueda (`search_pwa_keywords`, separadas por salto de línea).
     * Sin interfaz: se guardan en el dispositivo (p. ej. importando una copia), nunca en el código.
     */
    val pwaKeywords: Flow<Set<String>> = dataStore.data.map { p ->
        p[stringPreferencesKey("search_pwa_keywords")].orEmpty().split('\n')
            .map { com.usos.launcher.ui.search.SearchFilter.normalize(it.trim()) }.filter { it.isNotEmpty() }.toSet()
    }.distinctUntilChanged()

    /** Asistente de IA elegido (ver AssistantChoice); null = automático (el primero instalado). */
    val assistantChoice: Flow<String?> = dataStore.data.map { it[assistantKey] }.distinctUntilChanged()
    suspend fun setAssistantChoice(v: String?) { dataStore.edit { if (v == null) it.remove(assistantKey) else it[assistantKey] = v } }

    suspend fun setFeedsPerHour(n: Int) { dataStore.edit { it[perHourKey] = FeedLimits.clamp(n) } }
    suspend fun setFeedsHidden(hidden: Boolean) { dataStore.edit { it[feedsHiddenKey] = hidden } }
    suspend fun setCalendarAsked(asked: Boolean) { dataStore.edit { it[calendarAskedKey] = asked } }

    suspend fun setSuggestionsOn(on: Boolean) { dataStore.edit { it[suggestionsKey] = on } }
    suspend fun setSuggestProvider(p: SuggestProvider) { dataStore.edit { it[providerKey] = p.name } }
    suspend fun setWidgetsGrayscale(on: Boolean) { dataStore.edit { it[grayscaleKey] = on } }

    suspend fun setOnboardingDone(done: Boolean) { dataStore.edit { it[onboardingKey] = done } }

    suspend fun setAccent(argb: Int) { dataStore.edit { it[accentKey] = argb } }

    // ---- Reloj, tiempo y primer reparto de feeds ----
    private val nsClockKey = stringPreferencesKey("nightstand_clock")
    private val nsCalendarKey = booleanPreferencesKey("nightstand_calendar")
    private val nsSecondsKey = booleanPreferencesKey("nightstand_seconds")
    private val nsDimKey = booleanPreferencesKey("nightstand_dim")

    val nightstand: Flow<NightstandPrefs> = dataStore.data.map {
        NightstandPrefs(NightClock.fromName(it[nsClockKey]), it[nsCalendarKey] ?: true, it[nsSecondsKey] ?: false, it[nsDimKey] ?: true)
    }.distinctUntilChanged()
    suspend fun setNightClock(c: NightClock) { dataStore.edit { it[nsClockKey] = c.name } }
    suspend fun setNightCalendar(on: Boolean) { dataStore.edit { it[nsCalendarKey] = on } }
    suspend fun setNightSeconds(on: Boolean) { dataStore.edit { it[nsSecondsKey] = on } }
    suspend fun setNightDim(on: Boolean) { dataStore.edit { it[nsDimKey] = on } }

    private val clockTypeKey = stringPreferencesKey("clock_type")
    private val clockSecondsKey = booleanPreferencesKey("clock_seconds")
    private val clockAnimatedKey = booleanPreferencesKey("clock_animated")

    val clockPrefs: Flow<ClockPrefs> = dataStore.data.map {
        ClockPrefs(ClockType.fromName(it[clockTypeKey]), it[clockSecondsKey] ?: false, it[clockAnimatedKey] ?: true)
    }.distinctUntilChanged()

    suspend fun setClockType(t: ClockType) { dataStore.edit { it[clockTypeKey] = t.name } }
    suspend fun setClockSeconds(on: Boolean) { dataStore.edit { it[clockSecondsKey] = on } }
    suspend fun setClockAnimated(on: Boolean) { dataStore.edit { it[clockAnimatedKey] = on } }

    private val wOnKey = booleanPreferencesKey("weather_on")
    private val wFahrenheitKey = booleanPreferencesKey("weather_fahrenheit")
    private val wIconKey = booleanPreferencesKey("weather_icon")
    private val wTextKey = booleanPreferencesKey("weather_text")
    private val wMinMaxKey = booleanPreferencesKey("weather_minmax")
    private val wAutoKey = booleanPreferencesKey("weather_auto")
    private val wCityKey = stringPreferencesKey("weather_city")
    private val wLatKey = doublePreferencesKey("weather_lat")
    private val wLonKey = doublePreferencesKey("weather_lon")
    private val wBelowKey = booleanPreferencesKey("weather_below_clock")
    private val wAccentKey = booleanPreferencesKey("weather_accent")
    private val wCacheKey = stringPreferencesKey("weather_cache")
    private val feedsSeededKey = booleanPreferencesKey("feeds_seeded")

    val weatherPrefs: Flow<WeatherPrefs> = dataStore.data.map {
        WeatherPrefs(
            on = it[wOnKey] ?: false,
            fahrenheit = it[wFahrenheitKey] ?: false,
            icon = it[wIconKey] ?: true,
            text = it[wTextKey] ?: true,
            minMax = it[wMinMaxKey] ?: false,
            auto = it[wAutoKey] ?: true,
            cityName = it[wCityKey] ?: "",
            lat = it[wLatKey] ?: 0.0,
            lon = it[wLonKey] ?: 0.0,
            belowClock = it[wBelowKey] ?: false,
            accentColor = it[wAccentKey] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun setWeatherOn(on: Boolean) { dataStore.edit { it[wOnKey] = on } }
    suspend fun setWeatherFahrenheit(on: Boolean) { dataStore.edit { it[wFahrenheitKey] = on } }
    suspend fun setWeatherIcon(on: Boolean) { dataStore.edit { it[wIconKey] = on } }
    suspend fun setWeatherText(on: Boolean) { dataStore.edit { it[wTextKey] = on } }
    suspend fun setWeatherMinMax(on: Boolean) { dataStore.edit { it[wMinMaxKey] = on } }
    suspend fun setWeatherAuto(on: Boolean) { dataStore.edit { it[wAutoKey] = on } }
    suspend fun setWeatherBelowClock(on: Boolean) { dataStore.edit { it[wBelowKey] = on } }
    suspend fun setWeatherAccent(on: Boolean) { dataStore.edit { it[wAccentKey] = on } }
    suspend fun setWeatherCity(name: String, lat: Double, lon: Double) {
        dataStore.edit { it[wCityKey] = name; it[wLatKey] = lat; it[wLonKey] = lon; it[wAutoKey] = false; it.remove(wCacheKey) }
    }

    /** Ultimo tiempo descargado (JSON de WeatherNow) y la ultima ubicacion automatica usada. */
    val weatherCache: Flow<String?> = dataStore.data.map { it[wCacheKey] }.distinctUntilChanged()
    suspend fun setWeatherCache(json: String?) {
        dataStore.edit { if (json == null) it.remove(wCacheKey) else it[wCacheKey] = json }
    }

    /** Los feeds por defecto se crean una sola vez, si al primer arranque no hay ninguno. */
    suspend fun feedsSeeded(): Boolean = dataStore.data.first()[feedsSeededKey] ?: false
    suspend fun setFeedsSeeded() { dataStore.edit { it[feedsSeededKey] = true } }


    // ---- Buscador, lecturas de noticias y colores recientes ----
    private val engineKey = stringPreferencesKey("search_engine")
    private val tokenCountKey = intPreferencesKey("read_tokens")
    private val tokenAtKey = androidx.datastore.preferences.core.longPreferencesKey("read_tokens_at")
    private val recentColorsKey = stringPreferencesKey("recent_colors")

    val searchEngine: Flow<SearchEngine> = dataStore.data.map { SearchEngine.fromName(it[engineKey]) }.distinctUntilChanged()
    suspend fun setSearchEngine(e: SearchEngine) { dataStore.edit { it[engineKey] = e.name } }

    /** Cubo de lecturas; null = nunca usado (lleno). */
    val readTokens: Flow<TokenState?> = dataStore.data.map { p ->
        val c = p[tokenCountKey]
        val at = p[tokenAtKey]
        if (c == null || at == null) null else TokenState(c, at)
    }.distinctUntilChanged()

    /** Gasta una lectura de forma atómica; false si no quedaba ninguna. */
    suspend fun spendReadToken(now: Long, perHour: Int): Boolean {
        var ok = false
        dataStore.edit { p ->
            val cur = p[tokenCountKey]?.let { c -> p[tokenAtKey]?.let { TokenState(c, it) } }
            val next = ReadTokens.spend(cur, now, perHour)
            if (next != null) {
                p[tokenCountKey] = next.count
                p[tokenAtKey] = next.refilledAt
                ok = true
            }
        }
        return ok
    }

    val recentColors: Flow<List<Int>> = dataStore.data
        .map { p -> p[recentColorsKey].orEmpty().split(',').mapNotNull { it.toIntOrNull() } }.distinctUntilChanged()

    suspend fun addRecentColor(argb: Int) {
        dataStore.edit { p ->
            val cur = p[recentColorsKey].orEmpty().split(',').mapNotNull { it.toIntOrNull() }
            p[recentColorsKey] = (listOf(argb) + cur.filter { it != argb }).take(8).joinToString(",")
        }
    }

    // ---- Fuentes y fondo (1.0.6) ----
    private val fontTitleKey = stringPreferencesKey("font_title")
    private val fontTextKey = stringPreferencesKey("font_text")
    private val bgModeKey = stringPreferencesKey("bg_mode")
    private val bgColorKey = intPreferencesKey("bg_color")
    private val bgDimKey = intPreferencesKey("bg_dim")
    private val bgBlurKey = booleanPreferencesKey("bg_blur")
    private val bgVersionKey = intPreferencesKey("bg_image_version")

    /** Nombres de [com.usos.launcher.ui.theme.FontChoice]; DEFAULT = Space Grotesk / JetBrains Mono. */
    val fontTitle: Flow<String> = dataStore.data.map { it[fontTitleKey] ?: "DEFAULT" }.distinctUntilChanged()
    val fontText: Flow<String> = dataStore.data.map { it[fontTextKey] ?: "DEFAULT" }.distinctUntilChanged()
    suspend fun setFontTitle(name: String) { dataStore.edit { it[fontTitleKey] = name } }
    suspend fun setFontText(name: String) { dataStore.edit { it[fontTextKey] = name } }

    val background: Flow<BackgroundPrefs> = dataStore.data.map {
        BackgroundPrefs(
            BackgroundMode.fromName(it[bgModeKey]), it[bgColorKey] ?: 0xFF1A1A1A.toInt(),
            (it[bgDimKey] ?: BackgroundPrefs.DEFAULT_DIM).coerceIn(0, BackgroundPrefs.MAX_DIM),
            it[bgBlurKey] ?: false, it[bgVersionKey] ?: 0,
        )
    }.distinctUntilChanged()
    suspend fun setBackgroundMode(m: BackgroundMode) { dataStore.edit { it[bgModeKey] = m.name } }
    suspend fun setBackgroundColor(argb: Int) { dataStore.edit { it[bgColorKey] = argb; it[bgModeKey] = BackgroundMode.COLOR.name } }
    suspend fun setBackgroundDim(pct: Int) { dataStore.edit { it[bgDimKey] = pct.coerceIn(0, BackgroundPrefs.MAX_DIM) } }
    suspend fun setBackgroundBlur(on: Boolean) { dataStore.edit { it[bgBlurKey] = on } }
    /** Nueva imagen guardada: sube la versión (recarga) y activa el modo imagen. */
    suspend fun bumpBackgroundImage() {
        dataStore.edit { it[bgVersionKey] = (it[bgVersionKey] ?: 0) + 1; it[bgModeKey] = BackgroundMode.IMAGE.name }
    }

    suspend fun setLastOpenSection(section: Section?) {
        dataStore.edit { if (section == null) it.remove(lastOpenKey) else it[lastOpenKey] = section.name }
    }
}

/** Ajustes de iconos y mosaico. */
class DisplayStore(private val dataStore: DataStore<Preferences>) {
    private val iconModeKey = stringPreferencesKey("icon_mode")
    private val iconCodeKey = booleanPreferencesKey("icon_show_code")
    private val columnsKey = intPreferencesKey("mosaic_columns")
    private fun rowsKey(s: Section) = intPreferencesKey("mosaic_rows_" + s.name)
    private fun infoSizeKey(k: com.usos.launcher.data.info.InfoKind) = stringPreferencesKey("info_size_" + k.name)
    private fun infoHiddenKey(k: com.usos.launcher.data.info.InfoKind) = booleanPreferencesKey("info_hidden_" + k.name)

    val iconPrefs: Flow<IconPrefs> = dataStore.data.map {
        IconPrefs(IconMode.fromName(it[iconModeKey]), it[iconCodeKey] ?: true)
    }.distinctUntilChanged()

    val mosaicPrefs: Flow<MosaicPrefs> = dataStore.data.map { p ->
        val kinds = com.usos.launcher.data.info.InfoKind.entries
        MosaicPrefs(
            columns = (p[columnsKey] ?: 3).let { if (it in MosaicPacker.COLUMN_CHOICES) it else 3 },
            rows = Section.home.associateWith {
                (p[rowsKey(it)] ?: MosaicPacker.DEFAULT_ROWS).coerceIn(MosaicPacker.MIN_ROWS, MosaicPacker.MAX_ROWS)
            },
            infoSize = kinds.associateWith {
                TileSize.fromName(p[infoSizeKey(it)] ?: com.usos.launcher.data.info.InfoPieces.defaultSize(it).name)
            },
            infoHidden = kinds.filter { p[infoHiddenKey(it)] ?: false }.toSet(),
            sectionOrder = SectionTabs.decodeOrder(p[sectionOrderKey]),
            hiddenSections = SectionTabs.decodeHidden(p[sectionsHiddenKey]),
            tabs = p[homeTabsKey] ?: false,
            musicPlayer = p[musicPlayerKey] ?: false,
            drawerTabs = p[drawerTabsKey] ?: false,
        )
    }.distinctUntilChanged()

    private val homeTabsKey = booleanPreferencesKey("home_tabs")
    private val musicPlayerKey = booleanPreferencesKey("music_player_tiles")
    suspend fun setMusicPlayer(on: Boolean) { dataStore.edit { it[musicPlayerKey] = on } }
    suspend fun setHomeTabs(on: Boolean) { dataStore.edit { it[homeTabsKey] = on } }
    private val drawerTabsKey = booleanPreferencesKey("drawer_tabs")
    suspend fun setDrawerTabs(on: Boolean) { dataStore.edit { it[drawerTabsKey] = on } }
    private val sectionOrderKey = stringPreferencesKey("section_order")
    private val sectionsHiddenKey = stringPreferencesKey("sections_hidden")
    suspend fun setSectionOrder(order: List<Section>) { dataStore.edit { it[sectionOrderKey] = SectionTabs.encode(order) } }
    suspend fun setSectionHidden(s: Section, hidden: Boolean) {
        dataStore.edit {
            val cur = SectionTabs.decodeHidden(it[sectionsHiddenKey])
            it[sectionsHiddenKey] = SectionTabs.encode(if (hidden) cur + s else cur - s)
        }
    }

    private val dockOnKey = booleanPreferencesKey("dock_on")
    private val dockSlotsKey = intPreferencesKey("dock_slots")
    private val dockAppsKey = stringPreferencesKey("dock_apps")

    val dockPrefs: Flow<DockPrefs> = dataStore.data.map { p ->
        DockPrefs(
            on = p[dockOnKey] ?: false,
            slots = (p[dockSlotsKey] ?: DockPrefs.DEFAULT_SLOTS).coerceIn(DockPrefs.MIN_SLOTS, DockPrefs.MAX_SLOTS),
            apps = DockPrefs.decode(p[dockAppsKey]),
        )
    }.distinctUntilChanged()

    suspend fun setDockOn(on: Boolean) { dataStore.edit { it[dockOnKey] = on } }
    suspend fun setDockSlots(n: Int) { dataStore.edit { it[dockSlotsKey] = n.coerceIn(DockPrefs.MIN_SLOTS, DockPrefs.MAX_SLOTS) } }
    suspend fun setDockApp(i: Int, key: String?) {
        dataStore.edit {
            val cur = DockPrefs(apps = DockPrefs.decode(it[dockAppsKey]))
            it[dockAppsKey] = DockPrefs.encode(cur.with(i, key))
        }
    }

    suspend fun setIconMode(m: IconMode) { dataStore.edit { it[iconModeKey] = m.name } }
    suspend fun setIconShowCode(on: Boolean) { dataStore.edit { it[iconCodeKey] = on } }
    suspend fun setColumns(n: Int) { dataStore.edit { it[columnsKey] = n } }
    suspend fun setRows(s: Section, n: Int) {
        dataStore.edit { it[rowsKey(s)] = n.coerceIn(MosaicPacker.MIN_ROWS, MosaicPacker.MAX_ROWS) }
    }
    suspend fun setInfoSize(k: com.usos.launcher.data.info.InfoKind, s: TileSize) { dataStore.edit { it[infoSizeKey(k)] = s.name } }
    suspend fun setInfoHidden(k: com.usos.launcher.data.info.InfoKind, hidden: Boolean) { dataStore.edit { it[infoHiddenKey(k)] = hidden } }
}

/** Fondo de inicio, Widgets y Noticias. BLACK (defecto) = negro puro. */
enum class BackgroundMode {
    BLACK, COLOR, IMAGE, SYSTEM;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: BLACK
    }
}

data class BackgroundPrefs(
    val mode: BackgroundMode = BackgroundMode.BLACK,
    val color: Int = 0xFF1A1A1A.toInt(),
    val dim: Int = DEFAULT_DIM,
    val blur: Boolean = false,
    val imageVersion: Int = 0,
) {
    companion object {
        const val DEFAULT_DIM = 50
        const val MAX_DIM = 80
    }
}
