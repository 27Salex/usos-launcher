package com.usos.launcher.ui.settings

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
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosRow
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
    val onColumns: (Int) -> Unit,
    val onRows: (Section, Int) -> Unit,
    val onInfoSize: (InfoKind, TileSize) -> Unit,
    val onInfoHidden: (InfoKind, Boolean) -> Unit,
    val onEditMosaic: () -> Unit,
    val onSearchKeyboard: (Boolean) -> Unit,
    val onAssistant: (String?) -> Unit,
    val onFeedsKeep: (Int) -> Unit,
    val onClockTap: (String) -> Unit,
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
)

/** Categorías de la pantalla principal de Ajustes: clave guardada, título y orden. */
private enum class Cat(val title: String) {
    HOME("INICIO"), CLOCK("RELOJ"), WEATHER("TIEMPO"), SEARCH("BÚSQUEDA E IA"), LEISURE("OCIO"),
    WIDGETS("WIDGETS"), NEWS("NOTICIAS"), LOOK("APARIENCIA"), SYSTEM("SISTEMA"),
}

@Composable
fun SettingsScreen(ui: SettingsUi, actions: SettingsActions) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    // Atrás vuelve a la lista de categorías y, desde la lista, sale de Ajustes.
    BackHandler { if (page != null) page = null else actions.onBack() }
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
            UsosText("Ajustes", UsosType.PanelTitle, UsosColors.Text)
            Spacer(Modifier.height(12.dp))
            Cat.entries.forEach { c ->
                Column(Modifier.fillMaxWidth().tap { page = c.name }.padding(vertical = 10.dp)) {
                    UsosText(c.title, UsosType.SectionName, UsosColors.Text)
                    UsosText(summary(c, ui), UsosType.Label, UsosColors.TextSecondary, maxLines = 1)
                }
            }
        } else {
            UsosText(cat.title, UsosType.PanelTitle, UsosColors.Text)
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

private fun onOff(b: Boolean) = if (b) "SÍ" else "NO"

/** Resumen mono gris bajo el nombre de cada categoría. */
private fun summary(c: Cat, ui: SettingsUi): String = when (c) {
    Cat.HOME -> "${ui.mosaic.columns} COLUMNAS · ORDEN · PIEZAS"
    Cat.CLOCK -> ui.clock.type.label + if (ui.clock.seconds) " · SEGUNDOS" else ""
    Cat.WEATHER -> if (ui.weather.on) "ACTIVADO" else "APAGADO"
    Cat.SEARCH -> ui.searchEngine.label + " · IA " + (ui.assistant.resolved?.assistant?.label ?: "NINGUNA")
    Cat.LEISURE -> "${ui.budget} MIN · ESPERA ${ui.wait} S"
    Cat.WIDGETS -> "GRISES " + onOff(ui.grayscale)
    Cat.NEWS -> "${ui.feedsPerHour} LECTURAS/H · ${ui.feedsKeep} POR FEED"
    Cat.LOOK -> "ACENTO · ICONOS " + ui.icons.mode.label
    Cat.SYSTEM -> "PERMISOS · FONDO · v" + ui.versionName
}

@Composable
private fun HomeSettings(ui: SettingsUi, actions: SettingsActions) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    SubHeading("COLUMNAS DEL MOSAICO")
    Choice(listOf("3", "4"), if (ui.mosaic.columns == 4) 1 else 0, 2) { actions.onColumns(if (it == 1) 4 else 3) }
    SubHeading("MOSAICO POR SECCIÓN (FILAS)", top = 4.dp)
    Section.home.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            UsosText(s.title.uppercase(), UsosType.Label, UsosColors.Text, Modifier.width(88.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { n ->
                    UsosPill(n.toString(), filled = n == ui.mosaic.rowsFor(s), onClick = { actions.onRows(s, n) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    SubHeading("PIEZAS DE INFORMACIÓN", top = 4.dp)
    InfoKind.entries.forEach { k ->
        val spec = ui.infoSpecs.values.firstOrNull { it.kind == k } ?: return@forEach
        if (!spec.available && k == InfoKind.MAPS) return@forEach
        UsosRow(if (k == InfoKind.AGENDA) "AGENDA" else "IR A CASA") {
            UsosSwitch(!spec.hidden, { actions.onInfoHidden(k, !it) })
        }
        Choice(TileSize.entries.map { it.label }, TileSize.entries.indexOf(spec.size), 4) { actions.onInfoSize(k, TileSize.entries[it]) }
    }
    UsosPill("EDITAR INICIO", filled = false, onClick = actions.onEditMosaic, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    SubHeading("ORDEN DE APPS")
    UsosText("Las primeras entran al mosaico (●).", UsosType.Label, UsosColors.TextSecondary)
    Spacer(Modifier.height(8.dp))
    ui.orderLists.forEach { (section, placed) ->
        val open = expanded == section.name
        Row(
            Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp).tap { expanded = if (open) null else section.name },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            UsosText(section.title, UsosType.SectionName.copy(fontSize = 22.sp), UsosColors.Text)
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
    SubHeading("TIPO")
    Choice(ClockType.entries.map { it.label }, ClockType.entries.indexOf(ui.clock.type), 2) {
        actions.onClockType(ClockType.entries[it])
    }
    UsosRow("SEGUNDOS") { UsosSwitch(ui.clock.seconds, actions.onClockSeconds) }
    UsosRow("RELOJ ANIMADO") { UsosSwitch(ui.clock.animated, actions.onClockAnimated) }
    UsosText("Animación muy sutil; se desactiva con las animaciones reducidas del sistema.", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    SubHeading("AL TOCAR EL RELOJ")
    val tapApp = ui.clockTap.takeIf { it.startsWith("APP:") }?.removePrefix("APP:")
    val tapIdx = when { ui.clockTap == "NONE" -> 2; tapApp != null -> 1; else -> 0 }
    Choice(listOf("RELOJ DEL SISTEMA", "ELEGIR APP…", "NADA"), tapIdx, 1) {
        when (it) {
            0 -> actions.onClockTap("SYSTEM")
            1 -> pickingApp = true
            else -> actions.onClockTap("NONE")
        }
    }
    if (tapApp != null) {
        val label = ui.allApps.firstOrNull { a -> a.packageName == tapApp }?.label ?: tapApp
        UsosText("APP: ${label.uppercase()}", UsosType.Label, LocalAccent.current)
    }
    if (pickingApp) {
        Spacer(Modifier.height(8.dp))
        UsosText("ELIGE UNA APP", UsosType.Label, UsosColors.TextSecondary)
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
    UsosRow("MOSTRAR TIEMPO") { UsosSwitch(w.on, actions.onWeatherOn) }
    UsosText(
        "Open-Meteo, sin cuenta. Con ubicación automática se pide el permiso de ubicación aproximada.",
        UsosType.Label, UsosColors.TextSecondary, maxLines = 3,
    )
    if (w.on) {
        SubHeading("UBICACIÓN", top = 8.dp)
        Choice(listOf("AUTOMÁTICA", "CIUDAD"), if (w.auto) 0 else 1, 2) { actions.onWeatherAuto(it == 0) }
        if (!w.auto) {
            Spacer(Modifier.height(4.dp))
            UsosText(if (w.hasCity) "CIUDAD: ${w.cityName.uppercase()}" else "ELIGE UNA CIUDAD", UsosType.Label, accent)
            Spacer(Modifier.height(6.dp))
            CityField(ui.cityResults, actions)
        }
        UsosRow("UNIDADES") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsosPill("°C", filled = !w.fahrenheit, onClick = { actions.onWeatherFahrenheit(false) }, modifier = Modifier.width(64.dp))
                UsosPill("°F", filled = w.fahrenheit, onClick = { actions.onWeatherFahrenheit(true) }, modifier = Modifier.width(64.dp))
            }
        }
        UsosRow("ICONO") { UsosSwitch(w.icon, actions.onWeatherIcon) }
        UsosRow("TEXTO DE ESTADO") { UsosSwitch(w.text, actions.onWeatherText) }
        UsosRow("MÁX / MÍN DEL DÍA") { UsosSwitch(w.minMax, actions.onWeatherMinMax) }
        SubHeading("POSICIÓN", top = 8.dp)
        Choice(listOf("JUNTO A LA FECHA", "BAJO EL RELOJ"), if (w.belowClock) 1 else 0, 2) { actions.onWeatherBelow(it == 1) }
        SubHeading("COLOR")
        Choice(listOf("BLANCO", "ACENTO"), if (w.accentColor) 1 else 0, 2) { actions.onWeatherAccent(it == 1) }
    }
}

@Composable
private fun SearchSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow("SUGERENCIAS WEB") { UsosSwitch(ui.suggestionsOn, actions.onSuggestionsOn) }
    UsosText(
        "Cada pulsación se envía al proveedor elegido (tras 250 ms sin teclear).",
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
    SubHeading("BUSCADOR")
    Choice(SearchEngine.entries.map { it.label }, ui.searchEngine.ordinal, 2) { actions.onSearchEngine(SearchEngine.entries[it]) }
    UsosText("Intro abre URLs, apps con nombre exacto o busca aquí.", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    UsosRow("ABRIR TECLADO AL BUSCAR") { UsosSwitch(ui.searchKeyboard, actions.onSearchKeyboard) }
    UsosText("Apagado: la búsqueda muestra las apps y el teclado se abre al tocar el campo.", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)

    Spacer(Modifier.height(16.dp))
    SubHeading("ASISTENTE DE IA")
    val a = ui.assistant
    val labels = a.installed.map { it.label } + listOf("NINGUNO", "WEB")
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
        SubHeading("SERVICIO WEB", top = 4.dp)
        Choice(Assistant.entries.map { it.label }, Assistant.entries.indexOf(a.resolved?.assistant), 2) {
            actions.onAssistant(Assistants.webChoice(Assistant.entries[it]))
        }
    }
    UsosText(
        "Solo se listan los asistentes instalados. Ninguno oculta el botón de IA de inicio y la fila «PREGUNTA A…».",
        UsosType.Label, UsosColors.TextSecondary, maxLines = 3,
    )
}

@Composable
private fun LeisureSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow("PRESUPUESTO DIARIO") {
        Stepper("${ui.budget} MIN",
            onMinus = { actions.onBudget(ui.budget - LeisureLimits.BUDGET_STEP_MIN) },
            onPlus = { actions.onBudget(ui.budget + LeisureLimits.BUDGET_STEP_MIN) },
            minusEnabled = ui.budget > LeisureLimits.MIN_BUDGET_MIN,
            plusEnabled = ui.budget < LeisureLimits.MAX_BUDGET_MIN)
    }
    UsosRow("ESPERA AL DESCONGELAR") {
        Stepper("${ui.wait} S",
            onMinus = { actions.onWait(ui.wait - 5) },
            onPlus = { actions.onWait(ui.wait + 5) },
            minusEnabled = ui.wait > LeisureLimits.MIN_WAIT_SECONDS,
            plusEnabled = ui.wait < LeisureLimits.MAX_WAIT_SECONDS)
    }
}

@Composable
private fun WidgetsSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow("ESCALA DE GRISES") { UsosSwitch(ui.grayscale, actions.onGrayscale) }
    UsosText("Apágala para ver los colores originales de los widgets.", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(8.dp))
    UsosText("Las columnas de la rejilla son las del mosaico de inicio (INICIO).", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
}

@Composable
private fun NewsSettings(ui: SettingsUi, actions: SettingsActions) {
    UsosRow("LECTURAS POR HORA") {
        Stepper("${ui.feedsPerHour}",
            onMinus = { actions.onFeedsPerHour(ui.feedsPerHour - 1) },
            onPlus = { actions.onFeedsPerHour(ui.feedsPerHour + 1) },
            minusEnabled = ui.feedsPerHour > FeedLimits.MIN_PER_HOUR,
            plusEnabled = ui.feedsPerHour < FeedLimits.MAX_PER_HOUR)
    }
    UsosText("Abrir un artículo sin leer gasta 1 lectura; el cubo se rellena de forma continua.", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    SubHeading("ARTÍCULOS POR FEED")
    Choice(FeedLimits.KEEP_CHOICES.map { it.toString() }, FeedLimits.KEEP_CHOICES.indexOf(ui.feedsKeep).coerceAtLeast(0), 4) {
        actions.onFeedsKeep(FeedLimits.KEEP_CHOICES[it])
    }
    UsosText("Se conservan los más recientes de cada feed; los guardados (estrella) nunca se borran.", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
}

@Composable
private fun LookSettings(ui: SettingsUi, actions: SettingsActions) {
    SubHeading("ACENTO")
    val accent = LocalAccent.current
    val isPreset = UsosColors.AccentChoices.any { it.toArgb() == accent.toArgb() }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        UsosColors.AccentChoices.forEach { c ->
            Swatch(c, selected = c.toArgb() == accent.toArgb()) { customOpen = false; actions.onAccent(c.toArgb()) }
        }
        UsosPill("PERSONALIZADO", filled = !isPreset || customOpen, onClick = { customOpen = !customOpen }, modifier = Modifier.weight(1f))
    }
    if (customOpen) {
        Spacer(Modifier.height(12.dp))
        ColorPicker(accent.toArgb(), ui.recentColors, actions.onCustomAccent)
    }
    Spacer(Modifier.height(12.dp))
    SubHeading("ICONOS")
    Choice(IconMode.entries.map { it.label }, IconMode.entries.indexOf(ui.icons.mode), 3) { actions.onIconMode(IconMode.entries[it]) }
    if (ui.icons.mode != IconMode.NONE) {
        UsosRow("MOSTRAR CÓDIGO") { UsosSwitch(ui.icons.showCode, actions.onIconShowCode) }
        UsosText(
            "Con el código apagado el icono lo sustituye. Monocromo usa la capa monocroma de la app.",
            UsosType.Label, UsosColors.TextSecondary, maxLines = 2,
        )
    }
}

@Composable
private fun SystemSettings(ui: SettingsUi, actions: SettingsActions) {
    var confirmWallpaper by rememberSaveable { mutableStateOf(false) }
    SubHeading("PERMISOS")
    if (ui.calendarGranted) {
        UsosText("Calendario: concedido.", UsosType.Label, UsosColors.TextSecondary)
    } else {
        UsosText("Sin permiso de calendario la pieza de agenda no se dibuja.", UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        UsosPill("CONCEDER CALENDARIO", filled = false, onClick = actions.onCalendarPermission, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(16.dp))
    SubHeading("FONDO")
    if (!confirmWallpaper) {
        UsosPill("PONER FONDO NEGRO (INICIO Y BLOQUEO)", filled = false, onClick = { confirmWallpaper = true }, modifier = Modifier.fillMaxWidth())
    } else {
        UsosText("Sustituye el fondo de pantalla de inicio y de bloqueo por uno negro.", UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill("CONFIRMAR", filled = true, onClick = { confirmWallpaper = false; actions.onBlackWallpaper() }, modifier = Modifier.weight(1f))
            UsosPill("CANCELAR", filled = false, onClick = { confirmWallpaper = false }, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(16.dp))
    SubHeading("PRIMER ARRANQUE")
    UsosPill("VOLVER A EMPEZAR (PRIMER ARRANQUE)", filled = false, onClick = actions.onRestartOnboarding, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    SubHeading("ACERCA DE")
    UsosText("USOS · VERSIÓN ${ui.versionName}", UsosType.Label, UsosColors.Text)
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
        if (text.isEmpty()) UsosText("BUSCAR CIUDAD", UsosType.SearchHint, UsosColors.TextSecondary)
        BasicTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            textStyle = UsosType.Pill.copy(color = UsosColors.Text),
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
