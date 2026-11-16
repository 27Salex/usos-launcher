package com.usos.launcher.ui.settings

import com.usos.launcher.ui.theme.themed
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import com.usos.launcher.data.info.InfoKind
import com.usos.launcher.data.sections.IconMode
import com.usos.launcher.data.sections.IconPrefs
import com.usos.launcher.data.sections.MosaicPrefs
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.ui.home.InfoSpec
import com.usos.launcher.data.sections.ClockPrefs
import com.usos.launcher.data.sections.ClockType
import com.usos.launcher.data.sections.WeatherPrefs
import com.usos.launcher.data.weather.GeoPlace
import com.usos.launcher.data.feeds.FeedLimits
import com.usos.launcher.data.ai.Assistant
import com.usos.launcher.data.ai.Assistants
import com.usos.launcher.data.leisure.LeisureLimits
import com.usos.launcher.data.sections.PlacedApp
import com.usos.launcher.data.suggest.SearchEngine
import com.usos.launcher.data.suggest.SuggestProvider
import com.usos.launcher.ui.theme.ColorPicker
import com.usos.launcher.ui.theme.ColorMath
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosRow
import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import com.usos.launcher.ui.theme.UsosSwitch
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap

class SettingsActions(
    val onBudget: (Int) -> Unit,
    val onWait: (Int) -> Unit,
    val onAccent: (Int) -> Unit,
    val onCustomAccent: (Int) -> Unit,
    val onSearchEngine: (SearchEngine) -> Unit,
    val onSuggestionsOn: (Boolean) -> Unit,
    val onProvider: (SuggestProvider) -> Unit,
    val onGrayscale: (Boolean) -> Unit,
    val onFeedsPerHour: (Int) -> Unit,
    val onCalendarPermission: () -> Unit,
    val onMove: (Section, String, Int) -> Unit,
    val onRestartOnboarding: () -> Unit,
    val onClockType: (ClockType) -> Unit,
    val onClockSeconds: (Boolean) -> Unit,
    val onClockAnimated: (Boolean) -> Unit,
    val onWeatherOn: (Boolean) -> Unit,
    val onWeatherFahrenheit: (Boolean) -> Unit,
    val onWeatherIcon: (Boolean) -> Unit,
    val onWeatherText: (Boolean) -> Unit,
    val onWeatherMinMax: (Boolean) -> Unit,
    val onWeatherAuto: (Boolean) -> Unit,
    val onSearchCity: (String) -> Unit,
    val onPickCity: (GeoPlace) -> Unit,
    val onWeatherBelow: (Boolean) -> Unit,
    val onWeatherAccent: (Boolean) -> Unit,
    val onBlackWallpaper: () -> Unit,
    val onIconMode: (IconMode) -> Unit,
    val onIconShowCode: (Boolean) -> Unit,
    val onDockOn: (Boolean) -> Unit,
    val onDockSlots: (Int) -> Unit,
    val onColumns: (Int) -> Unit,
    val onRows: (Section, Int) -> Unit,
    val onSectionMove: (Section, Int) -> Unit = { _, _ -> },
    val onSectionHidden: (Section, Boolean) -> Unit = { _, _ -> },
    val onInfoSize: (InfoKind, TileSize) -> Unit,
    val onInfoHidden: (InfoKind, Boolean) -> Unit,
    val onEditMosaic: () -> Unit,
    val onSearchKeyboard: (Boolean) -> Unit,
    val onAssistant: (String?) -> Unit,
    val onFeedsKeep: (Int) -> Unit,
    val onClockTap: (String) -> Unit,
    val onThumbsGray: (Boolean) -> Unit,
    val onFontTitle: (com.usos.launcher.ui.theme.FontChoice) -> Unit,
    val onFontText: (com.usos.launcher.ui.theme.FontChoice) -> Unit,
    val onBackgroundMode: (com.usos.launcher.data.sections.BackgroundMode) -> Unit,
    val onBackgroundColor: (Int) -> Unit,
    val onBackgroundDim: (Int) -> Unit,
    val onBackgroundBlur: (Boolean) -> Unit,
    val onPickBackground: () -> Unit,
    val onBack: () -> Unit,
)

class SettingsUi(
    val budget: Int,
    val wait: Int,
    val orderLists: List<Pair<Section, List<PlacedApp>>>,
    val suggestionsOn: Boolean,
    val provider: SuggestProvider,
    val grayscale: Boolean,
    val feedsPerHour: Int,
    val calendarGranted: Boolean,
    val clock: ClockPrefs,
    val weather: WeatherPrefs,
    val cityResults: List<GeoPlace>,
    val icons: IconPrefs,
    val mosaic: MosaicPrefs,
    val infoSpecs: Map<Section, InfoSpec>,
    val searchEngine: SearchEngine,
    val recentColors: List<Int>,
    val searchKeyboard: Boolean,
    val assistant: com.usos.launcher.ui.home.HomeViewModel.AssistantUi,
    val feedsKeep: Int,
    val clockTap: String,
    val allApps: List<com.usos.launcher.domain.AppEntry>,
    val versionName: String,
    val thumbsGray: Boolean,
    val fontTitle: com.usos.launcher.ui.theme.FontChoice,
    val fontText: com.usos.launcher.ui.theme.FontChoice,
    val background: com.usos.launcher.data.sections.BackgroundPrefs,
    val dock: com.usos.launcher.data.sections.DockPrefs,
)

/** Categorías de la pantalla principal de Ajustes: clave guardada, título y orden. */
private enum class Cat(@StringRes val titleRes: Int) {
    HOME(R.string.settings_cat_home), CLOCK(R.string.settings_cat_clock), WEATHER(R.string.settings_cat_weather),
    SEARCH(R.string.settings_cat_search), LEISURE(R.string.settings_cat_leisure),
    WIDGETS(R.string.settings_cat_widgets), NEWS(R.string.settings_cat_news), LOOK(R.string.settings_cat_look),
    SYSTEM(R.string.settings_cat_system),
}

@Composable
fun SettingsScreen(initialCategory: String?, ui: SettingsUi, actions: SettingsActions) {
    var page by rememberSaveable(initialCategory) { mutableStateOf<String?>(initialCategory) }
    // Atrás vuelve a la lista de categorías y, desde la lista, sale de Ajustes.
    val goBack: () -> Unit = { if (page != null) page = null else actions.onBack() }
    BackHandler { goBack() }
    val cat = Cat.entries.firstOrNull { it.name == page }
    Column(
        Modifier
            .fillMaxSize()
            .background(UsosColors.Background)
            .verticalScroll(rememberScrollState(), enabled = true)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin,
            ),
    ) {
        if (cat == null) {
            ScreenHeader(stringResource(R.string.settings_title), onBack = goBack)
            Spacer(Modifier.height(12.dp))
            Cat.entries.forEach { c ->
                Column(Modifier.fillMaxWidth().tap { page = c.name }.padding(vertical = 10.dp)) {
                    UsosText(stringResource(c.titleRes), UsosType.SectionName, UsosColors.Text)
                    UsosText(summary(c, ui), UsosType.Label, UsosColors.TextSecondary, maxLines = 1)
                }
            }
        } else {
            ScreenHeader(stringResource(cat.titleRes), onBack = goBack)
            Spacer(Modifier.height(4.dp))
            when (cat) {
                Cat.HOME -> HomeSettings(ui, actions)
                Cat.CLOCK -> ClockSettings(ui, actions)
                Cat.WEATHER -> WeatherSettings(ui, actions)
                Cat.SEARCH -> SearchSettings(ui, actions)
                Cat.LEISURE -> LeisureSettings(ui, actions)
                Cat.WIDGETS -> WidgetsSettings(ui, actions)
                Cat.NEWS -> NewsSettings(ui, actions)
                Cat.LOOK -> LookSettings(ui, actions)
                Cat.SYSTEM -> SystemSettings(ui, actions)
            }
        }
    }
}

@Composable
private fun onOff(b: Boolean) = stringResource(if (b) R.string.yes else R.string.no)

/** Resumen mono gris bajo el nombre de cada categoría. */
@Composable
private fun summary(c: Cat, ui: SettingsUi): String = when (c) {
    Cat.HOME -> stringResource(R.string.settings_sum_home, ui.mosaic.columns)
    Cat.CLOCK -> stringResource(ui.clock.type.labelRes) +
        if (ui.clock.seconds) stringResource(R.string.settings_sum_seconds_suffix) else ""
    Cat.WEATHER -> stringResource(if (ui.weather.on) R.string.settings_sum_on else R.string.settings_sum_off)
    Cat.SEARCH -> stringResource(
        R.string.settings_sum_search, ui.searchEngine.label,
        ui.assistant.resolved?.assistant?.label ?: stringResource(R.string.settings_sum_ai_none),
    )
    Cat.LEISURE -> stringResource(R.string.settings_sum_leisure, ui.budget, ui.wait)
    Cat.WIDGETS -> stringResource(R.string.settings_sum_widgets, onOff(ui.grayscale))
    Cat.NEWS -> stringResource(R.string.settings_sum_news, ui.feedsPerHour, ui.feedsKeep)
    Cat.LOOK -> stringResource(R.string.settings_sum_look, stringResource(ui.icons.mode.labelRes))
    Cat.SYSTEM -> stringResource(R.string.settings_sum_system, ui.versionName)
}

@Composable
private fun HomeSettings(ui: SettingsUi, actions: SettingsActions) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    UsosRow(stringResource(R.string.settings_dock_on)) { UsosSwitch(ui.dock.on, actions.onDockOn) }
    if (ui.dock.on) {
        SubHeading(stringResource(R.string.settings_dock_slots), top = 4.dp)
        val opts = (com.usos.launcher.data.sections.DockPrefs.MIN_SLOTS..com.usos.launcher.data.sections.DockPrefs.MAX_SLOTS).toList()
        Choice(opts.map { it.toString() }, opts.indexOf(ui.dock.slots), opts.size) { actions.onDockSlots(opts[it]) }
        UsosText(stringResource(R.string.settings_dock_hint), UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
    }
    SubHeading(stringResource(R.string.settings_sections))
    UsosText(stringResource(R.string.settings_sections_hint), UsosType.Label, UsosColors.TextSecondary)
    Spacer(Modifier.height(4.dp))
    ui.mosaic.sectionOrder.forEachIndexed { i, s ->
        val hidden = s in ui.mosaic.hiddenSections
        Row(Modifier.fillMaxWidth().height(UsosDimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
            UsosText(stringResource(s.titleRes).uppercase(), UsosType.Label, if (hidden) UsosColors.TextSecondary else UsosColors.Text, Modifier.weight(1f))
            UsosPill(stringResource(if (hidden) R.string.settings_section_hidden else R.string.settings_section_shown), filled = !hidden,
                onClick = { actions.onSectionHidden(s, !hidden) })
            Arrow("↑", enabled = i > 0) { actions.onSectionMove(s, -1) }
            Arrow("↓", enabled = i < ui.mosaic.sectionOrder.lastIndex) { actions.onSectionMove(s, 1) }
        }
    }
    Spacer(Modifier.height(8.dp))
    SubHeading(stringResource(R.string.settings_home_columns))
    Choice(listOf("3", "4"), if (ui.mosaic.columns == 4) 1 else 0, 2) { actions.onColumns(if (it == 1) 4 else 3) }
    SubHeading(stringResource(R.string.settings_home_rows), top = 4.dp)
    Section.home.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            UsosText(stringResource(s.titleRes).uppercase(), UsosType.Label, UsosColors.Text, Modifier.width(88.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { n ->
                    UsosPill(n.toString(), filled = n == ui.mosaic.rowsFor(s), onClick = { actions.onRows(s, n) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    NotifSettings()
    SubHeading(stringResource(R.string.settings_home_info), top = 4.dp)
    InfoKind.entries.forEach { k ->
        val spec = ui.infoSpecs.values.firstOrNull { it.kind == k } ?: return@forEach
        if (!spec.available && k == InfoKind.MAPS) return@forEach
        UsosRow(stringResource(if (k == InfoKind.AGENDA) R.string.settings_info_agenda else R.string.settings_info_go_home)) {
            UsosSwitch(!spec.hidden, { actions.onInfoHidden(k, !it) })
        }
        Choice(TileSize.entries.map { it.label }, TileSize.entries.indexOf(spec.size), 4) { actions.onInfoSize(k, TileSize.entries[it]) }
    }
    UsosPill(stringResource(R.string.settings_edit_home), filled = false, onClick = actions.onEditMosaic, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    SubHeading(stringResource(R.string.settings_app_order))
    UsosText(stringResource(R.string.settings_app_order_hint), UsosType.Label, UsosColors.TextSecondary)
    Spacer(Modifier.height(8.dp))
    ui.orderLists.forEach { (section, placed) ->
        val open = expanded == section.name
        Row(
            Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp).tap { expanded = if (open) null else section.name },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            UsosText(stringResource(section.titleRes), UsosType.SectionName.copy(fontSize = 22.sp), UsosColors.Text)
            UsosText(if (open) "—" else placed.size.toString(), UsosType.SectionData, UsosColors.TextSecondary)
        }
        if (open) {
            placed.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().height(UsosDimens.MinTouch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UsosText(
                        (if (p.config.inMosaic) "● " else "  ") + p.app.label.uppercase(),
                        UsosType.Label, UsosColors.TextSecondary, Modifier.weight(1f),
                    )
                    Arrow("↑", enabled = i > 0) { actions.onMove(section, p.config.key, -1) }
                    Arrow("↓", enabled = i < placed.lastIndex) { actions.onMove(section, p.config.key, 1) }
                }
            }
        }
    }
}

@Composable
private fun ClockSettings(ui: SettingsUi, actions: SettingsActions) {
    var pickingApp by rememberSaveable { mutableStateOf(false) }
    NightstandSettings()
    SubHeading(stringResource(R.string.settings_clock_type))
    Choice(ClockType.entries.map { stringResource(it.labelRes) }, ClockType.entries.indexOf(ui.clock.type), 2) {
        actions.onClockType(ClockType.entries[it])
    }
    UsosRow(stringResource(R.string.settings_clock_seconds)) { UsosSwitch(ui.clock.seconds, actions.onClockSeconds) }
    UsosRow(stringResource(R.string.settings_clock_animated)) { UsosSwitch(ui.clock.animated, actions.onClockAnimated) }
    UsosText(stringResource(R.string.settings_clock_animated_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    SubHeading(stringResource(R.string.settings_clock_tap))
    val tapApp = ui.clockTap.takeIf { it.startsWith("APP:") }?.removePrefix("APP:")
    val tapIdx = when { ui.clockTap == "NONE" -> 2; tapApp != null -> 1; else -> 0 }
    Choice(listOf(stringResource(R.string.settings_clock_tap_system), stringResource(R.string.settings_clock_tap_pick), stringResource(R.string.settings_clock_tap_nothing)), tapIdx, 1) {
        when (it) {
            0 -> actions.onClockTap("SYSTEM")
            1 -> pickingApp = true
            else -> actions.onClockTap("NONE")
        }
    }
    if (tapApp != null) {
        val label = ui.allApps.firstOrNull { a -> a.packageName == tapApp }?.label ?: tapApp
        UsosText(stringResource(R.string.settings_clock_tap_app, label.uppercase()), UsosType.Label, LocalAccent.current)
    }
    if (pickingApp) {
        Spacer(Modifier.height(8.dp))
        UsosText(stringResource(R.string.settings_pick_app), UsosType.Label, UsosColors.TextSecondary)
        ui.allApps.forEach { a ->
            Box(
                Modifier.fillMaxWidth().height(UsosDimens.MinTouch).tap { pickingApp = false; actions.onClockTap("APP:" + a.packageName) },
                contentAlignment = Alignment.CenterStart,
            ) { UsosText(a.label.uppercase(), UsosType.Pill, UsosColors.Text, maxLines = 1) }
        }
    }
}

@Composable
private fun WeatherSettings(ui: SettingsUi, actions: SettingsActions) {
    val w = ui.weather
    val accent = LocalAccent.current
    UsosRow(stringResource(R.string.settings_weather_show)) { UsosSwitch(w.on, actions.onWeatherOn) }
    UsosText(
        stringResource(R.string.settings_weather_hint),
        UsosType.Label, UsosColors.TextSecondary, maxLines = 3,
    )
    if (w.on) {
        SubHeading(stringResource(R.string.settings_weather_location), top = 8.dp)
        Choice(listOf(stringResource(R.string.settings_weather_auto), stringResource(R.string.settings_weather_city)), if (w.auto) 0 else 1, 2) { actions.onWeatherAuto(it == 0) }
        if (!w.auto) {
            Spacer(Modifier.height(4.dp))
            UsosText(if (w.hasCity) stringResource(R.string.settings_weather_city_is, w.cityName.uppercase()) else stringResource(R.string.settings_weather_pick_city), UsosType.Label, accent)
            Spacer(Modifier.height(6.dp))
            CityField(ui.cityResults, actions)
        }
        UsosRow(stringResource(R.string.settings_weather_units)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsosPill("°C", filled = !w.fahrenheit, onClick = { actions.onWeatherFahrenheit(false) }, modifier = Modifier.width(64.dp))
                UsosPill("°F", filled = w.fahrenheit, onClick = { actions.onWeatherFahrenheit(true) }, modifier = Modifier.width(64.dp))
            }
        }
        UsosRow(stringResource(R.string.settings_weather_icon)) { UsosSwitch(w.icon, actions.onWeatherIcon) }
        UsosRow(stringResource(R.string.settings_weather_text)) { UsosSwitch(w.text, actions.onWeatherText) }
        UsosRow(stringResource(R.string.settings_weather_minmax)) { UsosSwitch(w.minMax, actions.onWeatherMinMax) }
        SubHeading(stringResource(R.string.settings_weather_position), top = 8.dp)
        Choice(listOf(stringResource(R.string.settings_weather_pos_date), stringResource(R.string.settings_weather_pos_clock)), if (w.belowClock) 1 else 0, 2) { actions.onWeatherBelow(it == 1) }
        SubHeading(stringResource(R.string.settings_weather_color))
        Choice(listOf(stringResource(R.string.settings_color_white), stringResource(R.string.settings_color_accent)), if (w.accentColor) 1 else 0, 2) { actions.onWeatherAccent(it == 1) }
    }
}

@Composable
private fun SearchSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow(stringResource(R.string.settings_search_suggestions)) { UsosSwitch(ui.suggestionsOn, actions.onSuggestionsOn) }
    UsosText(
        stringResource(R.string.settings_search_suggestions_hint),
        UsosType.Label, UsosColors.TextSecondary, maxLines = 2,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SuggestProvider.entries.forEach { p ->
            UsosPill(
                p.label, filled = p == ui.provider, enabled = ui.suggestionsOn,
                onClick = { actions.onProvider(p) }, modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SubHeading(stringResource(R.string.settings_search_engine))
    Choice(SearchEngine.entries.map { it.label }, ui.searchEngine.ordinal, 2) { actions.onSearchEngine(SearchEngine.entries[it]) }
    UsosText(stringResource(R.string.settings_search_engine_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    UsosRow(stringResource(R.string.settings_search_keyboard)) { UsosSwitch(ui.searchKeyboard, actions.onSearchKeyboard) }
    UsosText(stringResource(R.string.settings_search_keyboard_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)

    Spacer(Modifier.height(16.dp))
    SubHeading(stringResource(R.string.search_assistant))
    val a = ui.assistant
    val labels = a.installed.map { it.label } + listOf(stringResource(R.string.none), stringResource(R.string.settings_ai_web))
    val sel = when {
        a.raw == Assistants.NONE -> a.installed.size
        a.raw?.startsWith("WEB:") == true -> a.installed.size + 1
        a.resolved != null -> a.installed.indexOf(a.resolved.assistant)
        else -> a.installed.size
    }
    Choice(labels, sel, 2) { i ->
        when {
            i < a.installed.size -> actions.onAssistant(Assistants.appChoice(a.installed[i]))
            i == a.installed.size -> actions.onAssistant(Assistants.NONE)
            else -> actions.onAssistant(Assistants.webChoice(a.resolved?.assistant ?: Assistant.CLAUDE))
        }
    }
    if (a.raw?.startsWith("WEB:") == true) {
        SubHeading(stringResource(R.string.settings_ai_web_service), top = 4.dp)
        Choice(Assistant.entries.map { it.label }, Assistant.entries.indexOf(a.resolved?.assistant), 2) {
            actions.onAssistant(Assistants.webChoice(Assistant.entries[it]))
        }
    }
    UsosText(
        stringResource(R.string.settings_ai_hint),
        UsosType.Label, UsosColors.TextSecondary, maxLines = 3,
    )
}

@Composable
private fun LeisureSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow(stringResource(R.string.settings_leisure_budget)) {
        Stepper(stringResource(R.string.home_minutes, ui.budget),
            onMinus = { actions.onBudget(ui.budget - LeisureLimits.BUDGET_STEP_MIN) },
            onPlus = { actions.onBudget(ui.budget + LeisureLimits.BUDGET_STEP_MIN) },
            minusEnabled = ui.budget > LeisureLimits.MIN_BUDGET_MIN,
            plusEnabled = ui.budget < LeisureLimits.MAX_BUDGET_MIN)
    }
    UsosRow(stringResource(R.string.settings_leisure_wait)) {
        Stepper(stringResource(R.string.home_seconds, ui.wait),
            onMinus = { actions.onWait(ui.wait - 5) },
            onPlus = { actions.onWait(ui.wait + 5) },
            minusEnabled = ui.wait > LeisureLimits.MIN_WAIT_SECONDS,
            plusEnabled = ui.wait < LeisureLimits.MAX_WAIT_SECONDS)
    }
}

@Composable
private fun WidgetsSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow(stringResource(R.string.settings_widgets_gray)) { UsosSwitch(ui.grayscale, actions.onGrayscale) }
    UsosText(stringResource(R.string.settings_widgets_gray_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(8.dp))
    UsosText(stringResource(R.string.settings_widgets_grid_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
}

@Composable
private fun NewsSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow(stringResource(R.string.settings_news_reads)) {
        Stepper("${ui.feedsPerHour}",
            onMinus = { actions.onFeedsPerHour(ui.feedsPerHour - 1) },
            onPlus = { actions.onFeedsPerHour(ui.feedsPerHour + 1) },
            minusEnabled = ui.feedsPerHour > FeedLimits.MIN_PER_HOUR,
            plusEnabled = ui.feedsPerHour < FeedLimits.MAX_PER_HOUR)
    }
    UsosText(stringResource(R.string.settings_news_reads_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    SubHeading(stringResource(R.string.settings_news_keep))
    Choice(FeedLimits.KEEP_CHOICES.map { it.toString() }, FeedLimits.KEEP_CHOICES.indexOf(ui.feedsKeep).coerceAtLeast(0), 4) {
        actions.onFeedsKeep(FeedLimits.KEEP_CHOICES[it])
    }
    UsosText(stringResource(R.string.settings_news_keep_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    UsosRow(stringResource(R.string.thumbs_gray).uppercase()) { UsosSwitch(ui.thumbsGray, actions.onThumbsGray) }
    Spacer(Modifier.height(12.dp))
    NewsRefreshSettings()
}

/** Cuándo se descargan las noticias: al abrir Noticias si tienen más de X o solo a mano. */
@Composable
private fun NewsRefreshSettings() {
    val context = LocalContext.current
    val store = (context.applicationContext as com.usos.launcher.UsosApp).container.settingsStore
    val min by store.feedsRefreshMin.collectAsStateWithLifecycle(FeedLimits.DEFAULT_REFRESH_MIN)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val opts = FeedLimits.REFRESH_CHOICES
    SubHeading(stringResource(R.string.settings_news_refresh))
    Choice(
        listOf("10 MIN", "1 H", "6 H", stringResource(R.string.settings_news_refresh_manual)),
        opts.indexOf(min).coerceAtLeast(0), 4,
    ) { scope.launch { store.setFeedsRefreshMin(opts[it]) } }
    UsosText(
        stringResource(if (min == 0) R.string.settings_news_refresh_hint_manual else R.string.settings_news_refresh_hint),
        UsosType.Label, UsosColors.TextSecondary, maxLines = 3,
    )
}

@Composable
private fun LookSettings(ui: SettingsUi, actions: SettingsActions) {
    SubHeading(stringResource(R.string.settings_look_accent))
    val accent = LocalAccent.current
    val isPreset = UsosColors.AccentChoices.any { it.toArgb() == accent.toArgb() }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        UsosColors.AccentChoices.forEach { c ->
            Swatch(c, selected = c.toArgb() == accent.toArgb()) { customOpen = false; actions.onAccent(c.toArgb()) }
        }
        UsosPill(stringResource(R.string.settings_look_custom), filled = !isPreset || customOpen, onClick = { customOpen = !customOpen }, modifier = Modifier.weight(1f))
    }
    if (customOpen) {
        Spacer(Modifier.height(12.dp))
        ColorPicker(accent.toArgb(), ui.recentColors, actions.onCustomAccent)
    }
    Spacer(Modifier.height(12.dp))
    SubHeading(stringResource(R.string.settings_look_icons))
    Choice(IconMode.entries.map { stringResource(it.labelRes) }, IconMode.entries.indexOf(ui.icons.mode), 3) { actions.onIconMode(IconMode.entries[it]) }
    if (ui.icons.mode != IconMode.NONE) {
        UsosRow(stringResource(R.string.settings_look_show_code)) { UsosSwitch(ui.icons.showCode, actions.onIconShowCode) }
        UsosText(
            stringResource(R.string.settings_look_icons_hint),
            UsosType.Label, UsosColors.TextSecondary, maxLines = 2,
        )
    }
    Spacer(Modifier.height(16.dp))
    SubHeading(stringResource(R.string.settings_look_font_title))
    FontOptions(ui.fontTitle, title = true, onSelect = actions.onFontTitle)
    Spacer(Modifier.height(12.dp))
    SubHeading(stringResource(R.string.settings_look_font_text))
    FontOptions(ui.fontText, title = false, onSelect = actions.onFontText)
    Spacer(Modifier.height(16.dp))
    BackgroundSettings(ui, actions)
}

/** Lista de fuentes; cada opción se ve en su propia fuente. */
@Composable
private fun FontOptions(current: com.usos.launcher.ui.theme.FontChoice, title: Boolean, onSelect: (com.usos.launcher.ui.theme.FontChoice) -> Unit) {
    val accent = LocalAccent.current
    com.usos.launcher.ui.theme.FontChoice.entries.forEach { f ->
        val selected = f == current
        val name = f.labelRes?.let { stringResource(it) } ?: f.label
        Row(
            Modifier.fillMaxWidth().height(UsosDimens.MinTouch).tap { onSelect(f) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                if (title) name else name.uppercase(),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = f.family(title), fontSize = if (title) 20.sp else 13.sp,
                    fontWeight = if (title) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    color = if (selected) accent else UsosColors.Text,
                ),
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            androidx.compose.foundation.text.BasicText(
                "Aa 0123",
                style = androidx.compose.ui.text.TextStyle(fontFamily = f.family(title), fontSize = 14.sp, color = UsosColors.TextSecondary),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BackgroundSettings(ui: SettingsUi, actions: SettingsActions) {
    val bg = ui.background
    val modes = com.usos.launcher.data.sections.BackgroundMode.entries
    val context = LocalContext.current
    SubHeading(stringResource(R.string.settings_look_background))
    Choice(
        listOf(R.string.bg_black, R.string.bg_color, R.string.bg_image, R.string.bg_system).map { stringResource(it) },
        modes.indexOf(bg.mode), 2,
    ) {
        val m = modes[it]
        if (m == com.usos.launcher.data.sections.BackgroundMode.IMAGE &&
            !com.usos.launcher.data.background.BackgroundImage.file(context).exists()
        ) actions.onPickBackground() else actions.onBackgroundMode(m)
    }
    when (bg.mode) {
        com.usos.launcher.data.sections.BackgroundMode.COLOR ->
            ColorPicker(bg.color, ui.recentColors, actions.onBackgroundColor)
        com.usos.launcher.data.sections.BackgroundMode.IMAGE -> {
            UsosPill(stringResource(R.string.bg_pick), filled = false, onClick = actions.onPickBackground, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            UsosRow(stringResource(R.string.bg_dim, bg.dim)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UsosPill("-", filled = false, onClick = { actions.onBackgroundDim((bg.dim - 10).coerceAtLeast(0)) })
                    UsosPill("+", filled = false, onClick = { actions.onBackgroundDim((bg.dim + 10).coerceAtMost(com.usos.launcher.data.sections.BackgroundPrefs.MAX_DIM)) })
                }
            }
            UsosRow(stringResource(R.string.bg_blur)) { UsosSwitch(bg.blur, actions.onBackgroundBlur) }
        }
        com.usos.launcher.data.sections.BackgroundMode.SYSTEM ->
            UsosText(stringResource(R.string.bg_system_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 3)
        else -> Unit
    }
}

@Composable
private fun SystemSettings(ui: SettingsUi, actions: SettingsActions) {
    var confirmWallpaper by rememberSaveable { mutableStateOf(false) }
    LanguageSetting()
    Spacer(Modifier.height(16.dp))
    BackupSettings()
    SubHeading(stringResource(R.string.settings_system_permissions))
    if (ui.calendarGranted) {
        UsosText(stringResource(R.string.settings_system_calendar_granted), UsosType.Label, UsosColors.TextSecondary)
    } else {
        UsosText(stringResource(R.string.settings_system_calendar_missing), UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        UsosPill(stringResource(R.string.settings_system_grant_calendar), filled = false, onClick = actions.onCalendarPermission, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(16.dp))
    SubHeading(stringResource(R.string.settings_system_wallpaper))
    if (!confirmWallpaper) {
        UsosPill(stringResource(R.string.settings_system_black_wallpaper), filled = false, onClick = { confirmWallpaper = true }, modifier = Modifier.fillMaxWidth())
    } else {
        UsosText(stringResource(R.string.settings_system_black_wallpaper_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill(stringResource(R.string.confirm), filled = true, onClick = { confirmWallpaper = false; actions.onBlackWallpaper() }, modifier = Modifier.weight(1f))
            UsosPill(stringResource(R.string.cancel), filled = false, onClick = { confirmWallpaper = false }, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(16.dp))
    UpdateSettings()
    Spacer(Modifier.height(16.dp))
    SubHeading(stringResource(R.string.settings_system_onboarding))
    UsosPill(stringResource(R.string.settings_system_restart_onboarding), filled = false, onClick = actions.onRestartOnboarding, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    SubHeading(stringResource(R.string.settings_system_about))
    UsosText(stringResource(R.string.settings_system_about_version, ui.versionName), UsosType.Label, UsosColors.Text)
}

/** «Idioma: Sistema / Español / English»; la lista sale de res/xml/locales_config.xml (ver AppLocales). */
@Composable
private fun LanguageSetting() {
    val context = LocalContext.current
    val tags = remember { AppLocales.supportedTags(context) }
    var current by remember { mutableStateOf(AppLocales.currentTag(context)) }
    SubHeading(stringResource(R.string.settings_language))
    val options = listOf(stringResource(R.string.settings_language_system)) + tags.map { AppLocales.displayName(it).uppercase() }
    val selected = if (current == null) 0 else tags.indexOf(current).let { if (it < 0) 0 else it + 1 }
    Choice(options, selected, 3) { i ->
        val tag = if (i == 0) null else tags[i - 1]
        current = tag
        AppLocales.set(context, tag)
    }
}

/** Cabecera de grupo: texto fuerte y una linea fina debajo. */
@Composable
private fun Group(text: String) {
    Spacer(Modifier.height(28.dp))
    UsosText(text, UsosType.PanelState, UsosColors.Text)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(UsosColors.Border))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SubHeading(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    UsosText(text, UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = top, bottom = 4.dp))
}

/** Opciones excluyentes en pildoras, en filas de [columns]. */
@Composable
private fun Choice(options: List<String>, selected: Int, columns: Int, onSelect: (Int) -> Unit) {
    options.chunked(columns).forEachIndexed { r, row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { c, label ->
                val i = r * columns + c
                UsosPill(label, filled = i == selected, onClick = { onSelect(i) }, modifier = Modifier.weight(1f))
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CityField(results: List<GeoPlace>, actions: SettingsActions) {
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(text) {
        delay(400)
        actions.onSearchCity(text)
    }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Box(
        Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp)
            .background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) UsosText(stringResource(R.string.settings_weather_search_city), UsosType.SearchHint, UsosColors.TextSecondary)
        BasicTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            textStyle = UsosType.Pill.themed().copy(color = UsosColors.Text),
            cursorBrush = SolidColor(LocalAccent.current),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    results.forEach { p ->
        Row(
            Modifier.fillMaxWidth().height(UsosDimens.MinTouch).tap { text = ""; actions.onPickCity(p) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            UsosText(p.label, UsosType.Pill, UsosColors.Text, Modifier.weight(1f))
            UsosText(p.country.orEmpty().uppercase(), UsosType.Label, UsosColors.TextSecondary)
        }
    }
}

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow("−", minusEnabled, onMinus)
        UsosText(value, UsosType.Pill, UsosColors.Text, Modifier.padding(horizontal = 6.dp))
        Arrow("+", plusEnabled, onPlus)
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(UsosDimens.MinTouch).tap(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        UsosText(glyph, UsosType.PieceData, if (enabled) UsosColors.Text else UsosColors.BorderStrong)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(UsosDimens.MinTouch)
            .tap { onClick() }
            .border(2.dp, if (selected) UsosColors.Text else UsosColors.Border, CircleShape)
            .padding(6.dp)
            .background(color, CircleShape),
    )
}

/** Actualizaciones desde GitHub: ajuste automático, comprobación manual y tarjeta de la nueva versión. */
@Composable
private fun UpdateSettings() {
    val manager = (LocalContext.current.applicationContext as com.usos.launcher.UsosApp).container.updateManager
    val st by manager.state.collectAsStateWithLifecycle()
    val auto by manager.store.auto.collectAsStateWithLifecycle(true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { manager.load() }
    val accent = LocalAccent.current
    val r = st.release
    val phase = st.phase
    SubHeading(stringResource(R.string.settings_update_title))
    UsosRow(stringResource(R.string.settings_update_auto)) {
        UsosSwitch(auto, { v -> scope.launch { manager.store.setAuto(v) } })
    }
    val checking = phase == com.usos.launcher.data.update.UpdatePhase.CHECKING
    UsosPill(
        stringResource(if (checking) R.string.settings_update_checking else R.string.settings_update_check),
        filled = false, onClick = manager::checkNow, enabled = !checking, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    when (phase) {
        com.usos.launcher.data.update.UpdatePhase.UP_TO_DATE ->
            UsosText(stringResource(R.string.settings_update_up_to_date), UsosType.Label, UsosColors.TextSecondary)
        else -> {}
    }
    st.error?.let { UsosText(stringResource(it), UsosType.Label, UsosColors.TextSecondary, maxLines = 3) }
    if (r != null && phase != com.usos.launcher.data.update.UpdatePhase.UP_TO_DATE) {
        Spacer(Modifier.height(8.dp))
        val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
        Column(Modifier.fillMaxWidth().background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape).padding(16.dp)) {
            UsosText(stringResource(R.string.settings_update_version, r.version), UsosType.PanelState, accent)
            val notes = remember(r.notes) { com.usos.launcher.data.update.ReleaseNotes.plain(r.notes) }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                UsosText(notes, UsosType.Label, UsosColors.TextSecondary, maxLines = 14)
            }
            Spacer(Modifier.height(12.dp))
            when (phase) {
                com.usos.launcher.data.update.UpdatePhase.DOWNLOADING -> {
                    val pct = if (st.total > 0) (st.done * 100 / st.total).toInt().coerceIn(0, 100) else 0
                    UsosText(stringResource(R.string.settings_update_downloading, pct), UsosType.Label, UsosColors.Text)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(UsosColors.Border)) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(3.dp).background(accent))
                    }
                }
                com.usos.launcher.data.update.UpdatePhase.INSTALLING ->
                    UsosText(stringResource(R.string.settings_update_installing), UsosType.Label, UsosColors.Text)
                else -> {
                    if (phase == com.usos.launcher.data.update.UpdatePhase.NEEDS_PERMISSION) {
                        UsosText(stringResource(R.string.settings_update_permission), UsosType.Label, UsosColors.TextSecondary, maxLines = 3)
                        Spacer(Modifier.height(8.dp))
                    }
                    UsosPill(stringResource(R.string.settings_update_install), filled = true, onClick = manager::downloadAndInstall, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Avisos de notificación (Ninguno / Punto / Borde); lleva a conceder el acceso si falta. */
@Composable
private fun NotifSettings() {
    val context = LocalContext.current
    val store = (context.applicationContext as com.usos.launcher.UsosApp).container.settingsStore
    val mode by store.notifMode.collectAsStateWithLifecycle(com.usos.launcher.data.notify.NotifMode.DOT)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val granted = remember(tick) { com.usos.launcher.service.NotificationState.isGranted(context) }
    val grant = {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Unit
    }
    SubHeading(stringResource(R.string.settings_notif_title), top = 4.dp)
    val modes = com.usos.launcher.data.notify.NotifMode.entries
    Choice(
        listOf(R.string.settings_notif_none, R.string.settings_notif_dot, R.string.settings_notif_border).map { stringResource(it) },
        modes.indexOf(mode), 3,
    ) { i ->
        scope.launch { store.setNotifMode(modes[i]) }
        if (i != 0 && !granted) grant()
    }
    if (!granted) {
        UsosText(stringResource(R.string.settings_notif_missing), UsosType.Label, UsosColors.TextSecondary, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        UsosPill(stringResource(R.string.settings_notif_grant), filled = false, onClick = grant, modifier = Modifier.fillMaxWidth())
    } else {
        UsosText(stringResource(R.string.settings_notif_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    }
    Spacer(Modifier.height(16.dp))
}

/** Modo mesita: estilo del reloj, calendario, segundos, atenuar y botón para abrirlo (nunca se abre solo). */
@Composable
private fun NightstandSettings() {
    val context = LocalContext.current
    val store = (context.applicationContext as com.usos.launcher.UsosApp).container.settingsStore
    val p by store.nightstand.collectAsStateWithLifecycle(com.usos.launcher.data.sections.NightstandPrefs())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SubHeading(stringResource(R.string.night_title))
    UsosPill(stringResource(R.string.night_open), filled = true, onClick = { com.usos.launcher.ui.home.NightstandActivity.open(context) }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
    UsosText(stringResource(R.string.night_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 3)
    Spacer(Modifier.height(8.dp))
    val kinds = com.usos.launcher.data.sections.NightClock.entries
    Choice(kinds.map { stringResource(it.labelRes) }, kinds.indexOf(p.clock), 3) { scope.launch { store.setNightClock(kinds[it]) } }
    UsosRow(stringResource(R.string.night_calendar)) { UsosSwitch(p.calendar, onChange = { v -> scope.launch { store.setNightCalendar(v) } }) }
    UsosRow(stringResource(R.string.settings_clock_seconds)) { UsosSwitch(p.seconds, onChange = { v -> scope.launch { store.setNightSeconds(v) } }) }
    UsosRow(stringResource(R.string.night_dim)) { UsosSwitch(p.dim, onChange = { v -> scope.launch { store.setNightDim(v) } }) }
    Spacer(Modifier.height(16.dp))
}

/** Copia de seguridad: exportar/importar la configuración a un archivo (la copia de Google va aparte, automática). */
@Composable
private fun BackupSettings() {
    val context = LocalContext.current
    val backup = (context.applicationContext as com.usos.launcher.UsosApp).container.backupManager
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<com.usos.launcher.data.backup.ConfigFile?>(null) }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) scope.launch { status = if (backup.export(uri)) R.string.backup_exported else R.string.backup_failed } }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            val f = backup.read(uri)
            if (f == null) status = R.string.backup_invalid else { pending = f; status = null }
        }
    }
    SubHeading(stringResource(R.string.backup_title))
    UsosText(stringResource(R.string.backup_hint), UsosType.Label, UsosColors.TextSecondary, maxLines = 4)
    Spacer(Modifier.height(8.dp))
    val f = pending
    if (f == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill(stringResource(R.string.backup_export), filled = false, modifier = Modifier.weight(1f), onClick = {
                exporter.launch("usos-config-" + java.time.LocalDate.now() + ".json")
            })
            UsosPill(stringResource(R.string.backup_import), filled = false, modifier = Modifier.weight(1f), onClick = {
                importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            })
        }
    } else {
        UsosText(stringResource(R.string.backup_confirm, f.feeds.size), UsosType.Label, LocalAccent.current, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill(stringResource(R.string.backup_replace), filled = true, modifier = Modifier.weight(1f), onClick = {
                scope.launch { backup.import(f); pending = null; status = R.string.backup_imported }
            })
            UsosPill(stringResource(R.string.cancel_upper), filled = false, modifier = Modifier.weight(1f), onClick = { pending = null })
        }
    }
    status?.let {
        Spacer(Modifier.height(6.dp))
        UsosText(stringResource(it), UsosType.Label, LocalAccent.current)
    }
    Spacer(Modifier.height(16.dp))
}
