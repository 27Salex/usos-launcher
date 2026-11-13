package com.usos.launcher.ui.widgets

import com.usos.launcher.ui.theme.themed
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import com.usos.launcher.data.sections.IconMode
import com.usos.launcher.ui.search.SearchFilter
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalIconPrefs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.border
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

/** Cache pequena de vistas previas en bitmap (imagen de preview o icono). */
private val previewCache = LruCache<String, ImageBitmap>(24)

private fun loadPreviewBitmap(context: Context, info: AppWidgetProviderInfo, key: String): ImageBitmap? {
    previewCache.get(key)?.let { return it }
    val dpi = context.resources.displayMetrics.densityDpi
    val drawable = runCatching { info.loadPreviewImage(context, dpi) }.getOrNull()
        ?: runCatching { info.loadIcon(context, dpi) }.getOrNull()
        ?: return null
    val maxPx = (320 * context.resources.displayMetrics.density).toInt()
    val w0 = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h0 = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val k = minOf(1f, maxPx.toFloat() / maxOf(w0, h0))
    val w = maxOf(1, (w0 * k).toInt())
    val h = maxOf(1, (h0 * k).toInt())
    val bmp = runCatching {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(it))
        }
    }.getOrNull() ?: return null
    return bmp.asImageBitmap().also { previewCache.put(key, it) }
}

private fun cellsOf(info: AppWidgetProviderInfo, fallback: String): String =
    if (info.targetCellWidth > 0 && info.targetCellHeight > 0) "${info.targetCellWidth}x${info.targetCellHeight}" else fallback

/** Vista previa: layout real (previewLayout) si existe; si no imagen de preview; si no icono. */
@Composable
private fun WidgetPreview(e: PickerEntry, grayscale: Boolean) {
    val context = LocalContext.current
    val hasLayout = e.info.previewLayout != 0
    val bitmap by produceState<ImageBitmap?>(null, e.key, hasLayout) {
        value = if (hasLayout) null else withContext(Dispatchers.IO) { loadPreviewBitmap(context, e.info, e.key) }
    }
    val filter = if (grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    var layoutFailed by remember(e.key) { mutableStateOf(false) }
    val boxMod = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 160.dp)
    if (hasLayout && !layoutFailed) {
        AndroidView(
            modifier = boxMod,
            factory = { ctx ->
                FrameLayout(ctx).also { frame ->
                    val ok = runCatching {
                        val host = UsosHostView(ctx)
                        host.setAppWidget(-1, e.info)
                        host.updateAppWidget(RemoteViews(e.info.provider.packageName, e.info.previewLayout))
                        host.setGrayscale(grayscale)
                        frame.addView(host, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
                    }.isSuccess
                    if (!ok) frame.post { layoutFailed = true }
                }
            },
            update = { frame -> (frame.getChildAt(0) as? UsosHostView)?.setGrayscale(grayscale) },
        )
    } else {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, null, boxMod, contentScale = ContentScale.Fit, colorFilter = filter)
        } else {
            Box(boxMod.height(48.dp))
        }
    }
}
private data class OwnEntry(val kind: WidgetKind, val tall: Boolean, val nameRes: Int)

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
                        it.loadLabel(pm), it, cellsOf(it, "${cells(it.minWidth)}x${cells(it.minHeight)}"),
                    )
                }.sortedBy { it.label.lowercase() },
            )
        }
        .sortedBy { it.appName.lowercase() }
}

/** Selector propio: widgets instalados agrupados por app, con vista previa. */
@Composable
fun WidgetPicker(
    manager: AppWidgetManager,
    grayscale: Boolean,
    apps: List<com.usos.launcher.domain.AppEntry>,
    columns: Int,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onPickOwn: (WidgetKind, Int, Int) -> Unit,
    onClose: () -> Unit,
) {
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
        var query by rememberSaveable { mutableStateOf("") }
        var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
        val fieldShape = RoundedCornerShape(UsosDimens.RadiusPiece)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).height(56.dp)
                .background(UsosColors.Surface, fieldShape).border(1.dp, UsosColors.Border, fieldShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) UsosText(stringResource(R.string.widgets_search_hint), UsosType.SearchHint, UsosColors.TextSecondary)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = UsosType.SearchInput.themed().copy(color = UsosColors.Text),
                cursorBrush = SolidColor(LocalAccent.current),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val q = SearchFilter.normalize(query.trim())
        val searching = q.isNotEmpty()
        val list = groups
        val shown = remember(list, q) {
            if (list == null || !searching) list
            else list.mapNotNull { g ->
                if (SearchFilter.normalize(g.appName).contains(q)) g
                else g.entries.filter { SearchFilter.normalize(it.label).contains(q) }
                    .takeIf { it.isNotEmpty() }?.let { PickerGroup(g.appName, g.packageName, it) }
            }
        }
        val iconsOn = LocalIconPrefs.current.mode != IconMode.NONE
        val appByPkg = remember(apps) { apps.filter { it.isMainProfile }.associateBy { it.packageName } }
        // Grupo propio «USOS»: siempre el primero.
        val ownAll = listOf(
            OwnEntry(WidgetKind.USOS_MUSIC, false, R.string.widget_music_name),
            OwnEntry(WidgetKind.USOS_CALENDAR, false, R.string.widget_cal_agenda_name),
            OwnEntry(WidgetKind.USOS_CALENDAR, true, R.string.widget_cal_month_name),
            OwnEntry(WidgetKind.USOS_CLOCK, false, R.string.widget_clock_name),
        )
        val ownNames = ownAll.map { stringResource(it.nameRes) }
        val usosMatch = !searching || "usos".contains(q)
        val ownShown = ownAll.filterIndexed { i, _ -> usosMatch || SearchFilter.normalize(ownNames[i]).contains(q) }
        val ownNameOf = ownAll.zip(ownNames).toMap()
        when {
            shown != null && list.isNullOrEmpty() && ownShown.isEmpty() -> Box(Modifier.padding(top = 16.dp)) {
                UsosText(stringResource(R.string.widgets_none_installed), UsosType.Empty, UsosColors.TextSecondary)
            }
            shown != null && shown.isEmpty() && ownShown.isEmpty() -> Box(Modifier.padding(top = 16.dp)) {
                UsosText(stringResource(R.string.search_nothing), UsosType.Empty, UsosColors.TextSecondary)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
                if (ownShown.isNotEmpty()) {
                    item(key = "g-usos") {
                        Row(Modifier.fillMaxWidth().heightIn(min = UsosDimens.MinTouch + 8.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            UsosText(stringResource(R.string.widgets_usos_group), UsosType.Pill, LocalAccent.current)
                        }
                    }
                    items(ownShown, key = { "own-" + it.kind.name + it.tall }) { e ->
                        val (c, r) = OwnWidgets.defaultCells(e.kind, columns, e.tall)
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(UsosDimens.RadiusPiece))
                                .background(UsosColors.Surface)
                                .border(BorderStroke(1.dp, UsosColors.Border), RoundedCornerShape(UsosDimens.RadiusPiece))
                                .tap { onPickOwn(e.kind, c, r) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OwnWidgetPreview(e.kind, e.tall)
                            UsosText(ownNameOf.getValue(e), UsosType.Pill, UsosColors.Text)
                            val min = OwnWidgets.minCells(e.kind)
                            UsosText("${c}x$r  (min ${min.first}x${min.second})", UsosType.Label, UsosColors.TextSecondary)
                        }
                    }
                }
                shown.orEmpty().forEach { g ->
                    val open = searching || g.packageName in expanded
                    item(key = "g-" + g.packageName) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = UsosDimens.MinTouch + 8.dp)
                                .tap { expanded = if (g.packageName in expanded) expanded - g.packageName else expanded + g.packageName }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val entry = appByPkg[g.packageName]
                            if (iconsOn && entry != null) {
                                com.usos.launcher.ui.theme.AppIconOrNull(entry, 28.dp, UsosColors.Text) { Spacer(Modifier.size(28.dp)) }
                            }
                            Column(Modifier.weight(1f)) {
                                UsosText(g.appName, UsosType.Pill, UsosColors.Text, maxLines = 1)
                                UsosText(
                                    pluralStringResource(R.plurals.widgets_count, g.entries.size, g.entries.size),
                                    UsosType.Label, UsosColors.TextSecondary,
                                )
                            }
                            UsosText(if (open) "-" else "+", UsosType.Pill, UsosColors.TextSecondary)
                        }
                    }
                    if (open) items(g.entries, key = { it.key }) { e ->
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(UsosDimens.RadiusPiece))
                                .background(UsosColors.Surface)
                                .border(BorderStroke(1.dp, UsosColors.Border), RoundedCornerShape(UsosDimens.RadiusPiece))
                                .tap { onPick(e.info) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WidgetPreview(e, grayscale)
                            UsosText(e.label, UsosType.Pill, UsosColors.Text)
                            UsosText(e.cells, UsosType.Label, UsosColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
