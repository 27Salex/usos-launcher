package com.usos.launcher.ui.appdetail

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.usos.launcher.data.sections.AppConfig
import com.usos.launcher.data.sections.Highlight
import com.usos.launcher.data.usage.UsageSummary
import com.usos.launcher.domain.AppEntry
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

/** Datos de la ficha. `usage` null con permiso = aun cargando. */
data class DetailUi(
    val app: AppEntry,
    val config: AppConfig,
    val code: String,
    val usage: UsageSummary?,
    val hasPermission: Boolean,
    val storageBytes: Long?,
    val mosaicFull: Boolean,
)

class DetailActions(
    val onShowUsage: (Boolean) -> Unit,
    val onSection: (Section) -> Unit,
    val onInMosaic: (Boolean) -> Unit,
    val onCode: (String) -> Unit,
    val onHighlight: (Highlight) -> Unit,
    val onBack: () -> Unit,
)

private val sectionChoices = listOf(
    Section.WORK, Section.TALK, Section.MOVE, Section.PERSONAL, Section.LEISURE, Section.NONE,
)

@Composable
fun AppDetailScreen(ui: DetailUi, actions: DetailActions) {
    BackHandler { actions.onBack() }
    val context = LocalContext.current
    val app = ui.app
    val cfg = ui.config
    Column(
        Modifier
            .fillMaxSize()
            .background(UsosColors.Background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin,
            ),
    ) {
        UsosText(app.label, UsosType.PanelTitle, UsosColors.Text, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        UsosText(
            (app.versionName?.let { "VERSIÓN $it" } ?: app.packageName).uppercase(),
            UsosType.Label, UsosColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))

        UsageBlock(ui, context)

        Spacer(Modifier.height(16.dp))
        UsosRow("MOSTRAR USO EN LA PIEZA") { UsosSwitch(cfg.showUsage, actions.onShowUsage) }

        Spacer(Modifier.height(12.dp))
        Label("SECCIÓN")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sectionChoices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        UsosPill(
                            s.title.uppercase(), filled = s == cfg.section,
                            onClick = { actions.onSection(s) }, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        UsosRow("MOSTRAR EN INICIO") {
            UsosSwitch(cfg.inMosaic, actions.onInMosaic, enabled = cfg.section != Section.NONE)
        }
        if (ui.mosaicFull && !cfg.inMosaic && cfg.section != Section.NONE) {
            UsosText("MOSAICO LLENO · QUITA OTRA APP ANTES", UsosType.Label, LocalAccent.current, maxLines = 2)
        }

        Spacer(Modifier.height(16.dp))
        CodeField(ui, actions.onCode)

        Spacer(Modifier.height(16.dp))
        Label("DESTACADO")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Highlight.NORMAL to "NORMAL", Highlight.ACCENT to "ACENTO",
                Highlight.WHITE to "BLANCA", Highlight.OUTLINE to "BORDE",
            ).forEach { (h, text) ->
                UsosPill(
                    text, filled = h == cfg.highlight, onClick = { actions.onHighlight(h) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill("INFO", filled = false, onClick = { openInfo(context, app) }, modifier = Modifier.weight(1f))
            UsosPill("DESINSTALAR", filled = false, onClick = { uninstall(context, app) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Label(text: String) = UsosText(text, UsosType.Label, UsosColors.TextSecondary)

@Composable
private fun UsageBlock(ui: DetailUi, context: Context) {
    if (!ui.hasPermission) {
        Label("USO")
        Spacer(Modifier.height(8.dp))
        UsosText(
            "Para ver cuánto usas esta app hace falta el acceso a datos de uso.",
            UsosType.Empty, UsosColors.TextSecondary, maxLines = 3,
        )
        Spacer(Modifier.height(12.dp))
        UsosPill("CONCEDER ACCESO A DATOS DE USO", filled = true, onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName)),
                )
            }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
        }, modifier = Modifier.fillMaxWidth())
        return
    }
    val usage = ui.usage
    Label("HOY")
    UsosText(if (usage == null) "…" else UsageSummary.format(usage.todayMin), UsosType.BigNumber, UsosColors.Text)
    Spacer(Modifier.height(4.dp))
    if (usage != null) {
        UsosText(
            "ESTA SEMANA ${UsageSummary.format(usage.weekMin)} · ${usage.opens7} APERTURAS EN 7 DÍAS",
            UsosType.Label, UsosColors.TextSecondary, maxLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        Chart(usage.barsMs)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Label("ÚLTIMOS 7 DÍAS")
        val storage = ui.storageBytes
        if (storage != null) Label("ALMACENAMIENTO " + Formatter.formatShortFileSize(context, storage).uppercase())
    }
}

@Composable
private fun Chart(barsMs: List<Long>) {
    val accent = LocalAccent.current
    val max = (barsMs.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        barsMs.forEachIndexed { i, ms ->
            val h = (4 + 68 * ms.toFloat() / max).dp
            Box(
                Modifier.weight(1f).height(h)
                    .background(if (i == barsMs.lastIndex) accent else UsosColors.BorderStrong, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun CodeField(ui: DetailUi, onCode: (String) -> Unit) {
    var text by remember(ui.app.key) { mutableStateOf(ui.code) }
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Label("CÓDIGO (2 LETRAS)")
        Box(
            Modifier.size(64.dp, UsosDimens.MinTouch)
                .background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val v = raw.filter { it.isLetterOrDigit() }.take(2).uppercase()
                    text = v
                    onCode(v)
                },
                singleLine = true,
                textStyle = UsosType.TileCode.copy(color = UsosColors.Text, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                cursorBrush = SolidColor(LocalAccent.current),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
}

private fun openInfo(context: Context, app: AppEntry) {
    runCatching {
        val user = context.getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
        context.getSystemService(LauncherApps::class.java)
            .startAppDetailsActivity(ComponentName(app.packageName, app.activityName), user, null, null)
    }
}

private fun uninstall(context: Context, app: AppEntry) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)))
    }
}
