package com.usos.launcher.ui.widgets

import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PickerEntry(val key: String, val label: String, val info: AppWidgetProviderInfo, val cells: String)
class PickerGroup(val appName: String, val packageName: String, val entries: List<PickerEntry>)

private fun loadGroups(context: Context, manager: AppWidgetManager): List<PickerGroup> {
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    // Celdas aproximadas segun la formula clasica de Android: (dp + 30) / 70.
    fun cells(px: Int) = maxOf(1, ((px / density + 30f) / 70f).toInt())
    return manager.installedProviders
        .groupBy { it.provider.packageName }
        .map { (pkg, list) ->
            val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg)
            PickerGroup(
                appName, pkg,
                list.map {
                    PickerEntry(
                        it.provider.flattenToString() + "#" + it.profile.hashCode(),
                        it.loadLabel(pm), it, "${cells(it.minWidth)}x${cells(it.minHeight)}",
                    )
                }.sortedBy { it.label.lowercase() },
            )
        }
        .sortedBy { it.appName.lowercase() }
}

/** Selector propio: widgets instalados agrupados por app, solo texto (sin vistas previas ni iconos). */
@Composable
fun WidgetPicker(manager: AppWidgetManager, onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<PickerGroup>?>(null) }
    LaunchedEffect(Unit) { groups = withContext(Dispatchers.Default) { loadGroups(context, manager) } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    Column(
        Modifier.fillMaxSize().background(UsosColors.Background)
            .padding(top = top, start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            ScreenHeader(stringResource(R.string.widgets_add_title), onBack = onClose, modifier = Modifier.weight(1f))
            UsosPill(stringResource(R.string.close), filled = false, onClick = onClose)
        }
        val list = groups
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.padding(top = 16.dp)) {
                UsosText(stringResource(R.string.widgets_none_installed), UsosType.Empty, UsosColors.TextSecondary)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
                list.forEach { g ->
                    item(key = "g-" + g.packageName) {
                        UsosText(g.appName.uppercase(), UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 18.dp, bottom = 4.dp))
                    }
                    items(g.entries, key = { it.key }) { e ->
                        Row(
                            Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp).tap { onPick(e.info) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            UsosText(e.label, UsosType.Pill, UsosColors.Text, Modifier.weight(1f))
                            UsosText(e.cells, UsosType.Label, UsosColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
