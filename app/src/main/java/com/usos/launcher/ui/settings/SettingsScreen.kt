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
import com.usos.launcher.data.feeds.FeedLimits
import com.usos.launcher.data.leisure.LeisureLimits
import com.usos.launcher.data.sections.PlacedApp
import com.usos.launcher.data.suggest.SuggestProvider
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
    val onSuggestionsOn: (Boolean) -> Unit,
    val onProvider: (SuggestProvider) -> Unit,
    val onGrayscale: (Boolean) -> Unit,
    val onFeedsPerHour: (Int) -> Unit,
    val onCalendarPermission: () -> Unit,
    val onMove: (Section, String, Int) -> Unit,
    val onRestartOnboarding: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun SettingsScreen(
    budget: Int,
    wait: Int,
    orderLists: List<Pair<Section, List<PlacedApp>>>,
    suggestionsOn: Boolean,
    provider: SuggestProvider,
    grayscale: Boolean,
    feedsPerHour: Int,
    calendarGranted: Boolean,
    actions: SettingsActions,
) {
    BackHandler { actions.onBack() }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(UsosColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin,
            ),
    ) {
        UsosText("Ajustes", UsosType.PanelTitle, UsosColors.Text)
        Spacer(Modifier.height(20.dp))

        Heading("OCIO")
        UsosRow("PRESUPUESTO DIARIO") {
            Stepper("$budget MIN",
                onMinus = { actions.onBudget(budget - LeisureLimits.BUDGET_STEP_MIN) },
                onPlus = { actions.onBudget(budget + LeisureLimits.BUDGET_STEP_MIN) },
                minusEnabled = budget > LeisureLimits.MIN_BUDGET_MIN,
                plusEnabled = budget < LeisureLimits.MAX_BUDGET_MIN)
        }
        UsosRow("ESPERA AL DESCONGELAR") {
            Stepper("$wait S",
                onMinus = { actions.onWait(wait - 5) },
                onPlus = { actions.onWait(wait + 5) },
                minusEnabled = wait > LeisureLimits.MIN_WAIT_SECONDS,
                plusEnabled = wait < LeisureLimits.MAX_WAIT_SECONDS)
        }

        Spacer(Modifier.height(20.dp))
        Heading("BÚSQUEDA")
        UsosRow("SUGERENCIAS WEB") { UsosSwitch(suggestionsOn, actions.onSuggestionsOn) }
        UsosText(
            "Cada pulsación se envía al proveedor elegido (tras 250 ms sin teclear).",
            UsosType.Label, UsosColors.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestProvider.entries.forEach { p ->
                UsosPill(
                    p.label, filled = p == provider, enabled = suggestionsOn,
                    onClick = { actions.onProvider(p) }, modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Heading("WIDGETS")
        UsosRow("ESCALA DE GRISES") { UsosSwitch(grayscale, actions.onGrayscale) }

        Spacer(Modifier.height(20.dp))
        Heading("NOTICIAS")
        UsosRow("ARTÍCULOS POR HORA") {
            Stepper("$feedsPerHour",
                onMinus = { actions.onFeedsPerHour(feedsPerHour - 1) },
                onPlus = { actions.onFeedsPerHour(feedsPerHour + 1) },
                minusEnabled = feedsPerHour > FeedLimits.MIN_PER_HOUR,
                plusEnabled = feedsPerHour < FeedLimits.MAX_PER_HOUR)
        }

        Spacer(Modifier.height(20.dp))
        Heading("AGENDA")
        if (calendarGranted) {
            UsosText("Permiso de calendario concedido.", UsosType.Label, UsosColors.TextSecondary)
        } else {
            UsosText("Sin permiso la pieza de agenda no se dibuja.", UsosType.Label, UsosColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            UsosPill("CONCEDER CALENDARIO", filled = false, onClick = actions.onCalendarPermission, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(20.dp))
        Heading("ACENTO")
        val accent = LocalAccent.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UsosColors.AccentChoices.forEach { c ->
                Swatch(c, selected = c.toArgb() == accent.toArgb()) { actions.onAccent(c.toArgb()) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Heading("ORDEN DE APPS")
        UsosText("Las primeras entran al mosaico (●).", UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        orderLists.forEach { (section, placed) ->
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

        Spacer(Modifier.height(24.dp))
        Heading("PRIMER ARRANQUE")
        UsosPill("VOLVER A EMPEZAR", filled = false, onClick = actions.onRestartOnboarding, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Heading(text: String) {
    UsosText(text, UsosType.Label, UsosColors.TextSecondary, Modifier.padding(bottom = 4.dp))
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
