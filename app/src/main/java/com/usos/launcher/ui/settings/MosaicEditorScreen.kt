package com.usos.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usos.launcher.data.info.InfoKind
import com.usos.launcher.data.info.InfoPieces
import com.usos.launcher.data.sections.MosaicPrefs
import com.usos.launcher.data.sections.PlacedApp
import com.usos.launcher.data.sections.SectionMosaic
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.data.sections.entry
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.home.InfoSpec
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosSwitch
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap

class MosaicEditorUi(
    val orderLists: List<Pair<Section, List<PlacedApp>>>,
    val prefs: MosaicPrefs,
    val infoSpecs: Map<Section, InfoSpec>,
)

class MosaicEditorActions(
    val onRows: (Section, Int) -> Unit,
    val onMove: (Section, String, Int) -> Unit,
    val onInMosaic: (String, Boolean) -> Unit,
    val onSize: (String, TileSize) -> Unit,
    val onInfoSize: (InfoKind, TileSize) -> Unit,
    val onInfoHidden: (InfoKind, Boolean) -> Unit,
    val onBack: () -> Unit,
)

/** Edición del mosaico de una sección: previsualización y lista de apps (orden, en inicio, tamaño). Sin arrastrar. */
@Composable
fun MosaicEditorScreen(ui: MosaicEditorUi, actions: MosaicEditorActions) {
    BackHandler { actions.onBack() }
    var sectionName by rememberSaveable { mutableStateOf(Section.WORK.name) }
    val section = Section.valueOf(sectionName)
    val apps = ui.orderLists.firstOrNull { it.first == section }?.second.orEmpty()
    val spec = ui.infoSpecs[section]
    val infoSize = spec?.visibleSize
    val rows = ui.prefs.rowsFor(section)
    val mapsPiece = spec != null && spec.kind == InfoKind.MAPS && infoSize != null
    val shown = if (mapsPiece) apps.filter { it.app.packageName != InfoPieces.MAPS_PACKAGE } else apps

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
        UsosText("Mosaico", UsosType.PanelTitle, UsosColors.Text)
        Spacer(Modifier.height(16.dp))
        Section.home.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    UsosPill(s.title.uppercase(), filled = s == section, onClick = { sectionName = s.name }, modifier = Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            UsosText("FILAS", UsosType.Label, UsosColors.TextSecondary, Modifier.width(56.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { n ->
                    UsosPill(n.toString(), filled = n == rows, onClick = { actions.onRows(section, n) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Preview(ui.prefs.columns, rows, infoSize, shown, section)

        Spacer(Modifier.height(20.dp))
        if (spec != null && (spec.available || spec.kind == InfoKind.AGENDA)) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().height(UsosDimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
                    UsosText(
                        if (spec.kind == InfoKind.AGENDA) "PIEZA AGENDA" else "PIEZA IR A CASA",
                        UsosType.Label, LocalAccent.current, Modifier.weight(1f),
                    )
                    UsosText("EN INICIO", UsosType.Label, UsosColors.TextSecondary)
                    UsosSwitch(!spec.hidden, { actions.onInfoHidden(spec.kind, !it) })
                }
                SizePills(spec.size) { actions.onInfoSize(spec.kind, it) }
            }
        }
        apps.forEachIndexed { i, p ->
            val cfg = p.config
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().height(UsosDimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
                    UsosText(
                        p.app.label.uppercase(), UsosType.Label,
                        if (cfg.inMosaic) UsosColors.Text else UsosColors.TextSecondary, Modifier.weight(1f),
                    )
                    Arrow("↑", enabled = i > 0) { actions.onMove(section, cfg.key, -1) }
                    Arrow("↓", enabled = i < apps.lastIndex) { actions.onMove(section, cfg.key, 1) }
                    UsosSwitch(cfg.inMosaic, { actions.onInMosaic(cfg.key, it) })
                }
                SizePills(cfg.size) { actions.onSize(cfg.key, it) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(UsosColors.Border))
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        UsosPill("VOLVER", filled = false, onClick = actions.onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SizePills(selected: TileSize, onSelect: (TileSize) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TileSize.entries.forEach { s ->
            UsosPill(s.label, filled = s == selected, onClick = { onSelect(s) }, modifier = Modifier.weight(1f))
        }
    }
}

/** Mosaico en miniatura con el mismo empaquetado que el inicio. Las celdas libres se dibujan como huecos. */
@Composable
private fun Preview(columns: Int, rows: Int, infoSize: TileSize?, apps: List<PlacedApp>, section: Section) {
    val gap = 4.dp
    val packed = SectionMosaic.layout(columns, rows, infoSize, apps.map { it.entry() }, SectionMosaic.reservesFreeze(section))
    val byKey = apps.associateBy { it.config.key }
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(8.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - gap * (columns - 1)) / columns
        val h = cell * 0.8f
        Box(Modifier.fillMaxWidth().height(h * rows + gap * (rows - 1))) {
            for (r in 0 until rows) for (c in 0 until columns) {
                Box(Modifier.offset((cell + gap) * c, (h + gap) * r).size(cell, h).border(1.dp, UsosColors.Border, shape))
            }
            packed.placements.forEach { pl ->
                val m = Modifier
                    .offset((cell + gap) * pl.col, (h + gap) * pl.row)
                    .size(cell * pl.colSpan + gap * (pl.colSpan - 1), h * pl.rowSpan + gap * (pl.rowSpan - 1))
                if (pl.key == SectionMosaic.INFO_KEY) {
                    Box(m.background(accent, shape).padding(6.dp)) { UsosText("INFO", UsosType.Label, UsosColors.OnAccent) }
                } else {
                    val p = byKey[pl.key]
                    Box(m.background(UsosColors.Surface, shape).border(1.dp, UsosColors.BorderStrong, shape).padding(6.dp)) {
                        UsosText(p?.code.orEmpty(), UsosType.Label, UsosColors.Text)
                    }
                }
            }
            packed.reserved?.let { f ->
                Box(
                    Modifier.offset((cell + gap) * f.col, (h + gap) * f.row).size(cell, h)
                        .border(1.dp, accent, shape).padding(6.dp),
                ) { UsosText("CONGELAR", UsosType.SmallTag, accent) }
            }
        }
    }
    if (packed.skipped.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        UsosText(packed.skipped.size.toString() + " NO CABEN Y SE OCULTAN", UsosType.Label, accent)
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(UsosDimens.MinTouch).tap(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        UsosText(glyph, UsosType.PieceData, if (enabled) UsosColors.Text else UsosColors.BorderStrong)
    }
}
