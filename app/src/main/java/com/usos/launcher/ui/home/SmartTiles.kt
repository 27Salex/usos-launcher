package com.usos.launcher.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.usos.launcher.R
import com.usos.launcher.data.tiles.ContactApp
import com.usos.launcher.data.tiles.CustomTile
import com.usos.launcher.data.tiles.CustomTiles
import com.usos.launcher.data.tiles.NavApps
import com.usos.launcher.data.tiles.TileContact
import com.usos.launcher.data.tiles.TileKind
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.LocalOnAccent
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.pressGestures
import com.usos.launcher.ui.theme.tap
import com.usos.launcher.ui.theme.themed
import com.usos.launcher.ui.widgets.MediaUi
import com.usos.launcher.ui.widgets.MusicBars
import com.usos.launcher.ui.widgets.rememberMediaUi

// ---------------------------------------------------------------------------------------------------------------
// Marco común y glifos
// ---------------------------------------------------------------------------------------------------------------

/** Aspecto de pieza: Surface, borde de 1 dp, radio de pieza; toque/pulsación larga solo si [interactive]. */
@Composable
private fun TileFrame(
    modifier: Modifier,
    interactive: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Box(
        modifier
            .clip(shape)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .then(if (interactive) Modifier.pressGestures(onLongPress = onLongPress, onClick = onClick) else Modifier),
    ) { content() }
}

private val PIN_ROWS = listOf(
    "..XXX..",
    ".XXXXX.",
    "XXX.XXX",
    "XX...XX",
    "XXX.XXX",
    ".XXXXX.",
    "..XXX..",
    "...X...",
    "...X...",
)

/** Chincheta de puntos (matriz 7x9). */
@Composable
private fun PinGlyph(height: Dp, color: Color) {
    val cols = PIN_ROWS[0].length
    val rows = PIN_ROWS.size
    Canvas(Modifier.size(height * cols / rows, height)) {
        val cell = size.height / rows
        for (r in 0 until rows) for (c in 0 until cols) {
            if (PIN_ROWS[r][c] == 'X') drawCircle(color, cell * 0.36f, Offset((c + 0.5f) * cell, (r + 0.5f) * cell))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Destino
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun DestinationTile(
    tile: CustomTile,
    colSpan: Int,
    rowSpan: Int,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val accent = LocalAccent.current
    val name = tile.name.ifBlank { tile.address }
    val app = if (tile.appPackage.isEmpty()) "" else NavApps.labelOf(tile.appPackage).uppercase()
    TileFrame(modifier, interactive, onOpen, onLongPress) {
        when {
            rowSpan >= 2 -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PinGlyph(if (colSpan >= 2) 34.dp else 28.dp, accent)
                    Spacer(Modifier.size(10.dp))
                    UsosText(stringResource(R.string.stile_go), UsosType.Label, UsosColors.TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                UsosText(name, UsosType.TileCode, UsosColors.Text, maxLines = 2)
                if (tile.address.isNotBlank() && tile.name.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    UsosText(tile.address, UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
                }
                if (app.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    UsosText(app, UsosType.SmallTag, accent)
                }
            }
            colSpan >= 2 -> Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PinGlyph(40.dp, accent)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    UsosText(name, UsosType.TileCode, UsosColors.Text)
                    if (tile.address.isNotBlank() && tile.name.isNotBlank()) UsosText(tile.address, UsosType.SmallName, UsosColors.TextSecondary)
                    if (app.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        UsosText(app, UsosType.SmallTag, accent)
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PinGlyph(34.dp, accent)
                Spacer(Modifier.height(8.dp))
                UsosText(name, UsosType.TileName, UsosColors.Text)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Contactos
// ---------------------------------------------------------------------------------------------------------------

internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = words.take(2).mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
    return letters.joinToString("").uppercase().ifEmpty { "?" }
}

/** Mini glifo de la app elegida, dibujado con trazos. */
@Composable
private fun AppGlyph(app: ContactApp, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = Stroke(width = (w * 0.12f).coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (app) {
            ContactApp.WHATSAPP -> {
                drawCircle(color, w * 0.42f, Offset(w / 2, h / 2), style = s)
                drawLine(color, Offset(w * 0.18f, h * 0.9f), Offset(w * 0.28f, h * 0.66f), s.width, StrokeCap.Round)
            }
            ContactApp.TELEGRAM -> drawPath(
                Path().apply {
                    moveTo(w * 0.08f, h * 0.46f); lineTo(w * 0.92f, h * 0.1f); lineTo(w * 0.68f, h * 0.9f)
                    lineTo(w * 0.46f, h * 0.58f); close()
                },
                color, style = s,
            )
            ContactApp.SMS -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.6f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = s)
                drawLine(color, Offset(w * 0.1f, h * 0.24f), Offset(w * 0.5f, h * 0.55f), s.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.9f, h * 0.24f), Offset(w * 0.5f, h * 0.55f), s.width, StrokeCap.Round)
            }
            ContactApp.CALL -> {
                drawLine(color, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.78f, h * 0.22f), s.width * 1.6f, StrokeCap.Round)
                drawCircle(color, w * 0.11f, Offset(w * 0.2f, h * 0.8f))
                drawCircle(color, w * 0.11f, Offset(w * 0.8f, h * 0.2f))
            }
        }
    }
}

@Composable
private fun Avatar(c: TileContact, size: Dp, interactive: Boolean, showName: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    Column(
        Modifier.then(if (interactive) Modifier.tap(onLongClick = onLongPress) { onClick() } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size)) {
            Box(Modifier.fillMaxSize().background(LocalAccent.current, CircleShape), contentAlignment = Alignment.Center) {
                UsosText(initialsOf(c.name), UsosType.SmallCode, LocalOnAccent.current)
            }
            AppGlyph(
                c.app, UsosColors.Text,
                Modifier.align(Alignment.BottomEnd).size(size * 0.4f)
                    .background(UsosColors.Background, CircleShape).border(1.dp, UsosColors.BorderStrong, CircleShape)
                    .padding(size * 0.07f),
            )
        }
        if (showName) {
            Spacer(Modifier.height(4.dp))
            UsosText(c.name.trim().substringBefore(' '), UsosType.SmallName, UsosColors.Text, Modifier.widthIn(max = size + 18.dp))
        }
    }
}

@Composable
fun ContactsTile(
    tile: CustomTile,
    colSpan: Int,
    rowSpan: Int,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onOpenContact: (Int) -> Unit,
    onLongPress: () -> Unit,
) {
    val (perRow, cap, size) = when {
        colSpan >= 2 && rowSpan >= 2 -> Triple(2, 4, 52.dp)
        colSpan >= 2 -> Triple(3, 3, 44.dp)
        rowSpan >= 2 -> Triple(1, 2, 46.dp)
        else -> Triple(1, 1, 44.dp)
    }
    val shown = tile.contacts.take(cap).withIndex().toList()
    TileFrame(modifier, interactive, onClick = { if (shown.isNotEmpty()) onOpenContact(0) }, onLongPress = onLongPress) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            shown.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { (i, c) -> Avatar(c, size, interactive, true, { onOpenContact(i) }, onLongPress) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Música
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun SmartBars(playing: Boolean, color: Color, modifier: Modifier) {
    val animate = playing && LocalAnimationsEnabled.current
    val timeMs = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate, lifecycle) {
        if (!animate) { timeMs.longValue = 0L; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { n ->
                    if (n - last >= 33_000_000L) { last = n; timeMs.longValue = n / 1_000_000L }
                }
            }
        }
    }
    Canvas(modifier) {
        val barW = 3.dp.toPx()
        val gap = 3.dp.toPx()
        val n = ((size.width + gap) / (barW + gap)).toInt().coerceIn(3, 40)
        val total = n * barW + (n - 1) * gap
        val x0 = (size.width - total) / 2f
        val t = timeMs.longValue / 1000f
        for (i in 0 until n) {
            val hh = (MusicBars.height(i, n, t, playing) * size.height).coerceAtLeast(2.dp.toPx())
            drawRect(color, Offset(x0 + i * (barW + gap), size.height - hh), androidx.compose.ui.geometry.Size(barW, hh))
        }
    }
}

private enum class Transport { PREV, PLAY, PAUSE, NEXT }

@Composable
private fun TransportButton(kind: Transport, big: Boolean, description: String, onClick: () -> Unit) {
    val d = if (big) 44.dp else 36.dp
    val filled = kind == Transport.PLAY || kind == Transport.PAUSE
    val bg = if (filled) LocalAccent.current else Color.Transparent
    val fg = if (filled) LocalOnAccent.current else UsosColors.Text
    Box(
        Modifier.size(d).background(bg, CircleShape)
            .then(if (filled) Modifier else Modifier.border(1.dp, UsosColors.BorderStrong, CircleShape))
            .semantics { contentDescription = description }.tap { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(d * 0.4f)) { drawTransport(kind, fg) }
    }
}

private fun DrawScope.drawTransport(kind: Transport, color: Color) {
    val w = size.width
    val h = size.height
    fun tri(x0: Float, x1: Float) = Path().apply {
        moveTo(x0, 0f); lineTo(x1, h / 2); lineTo(x0, h); close()
    }
    when (kind) {
        Transport.PLAY -> drawPath(tri(w * 0.1f, w * 0.95f), color)
        Transport.PAUSE -> {
            drawRect(color, Offset(w * 0.12f, 0f), androidx.compose.ui.geometry.Size(w * 0.28f, h))
            drawRect(color, Offset(w * 0.60f, 0f), androidx.compose.ui.geometry.Size(w * 0.28f, h))
        }
        Transport.NEXT -> {
            drawPath(tri(0f, w * 0.75f), color)
            drawRect(color, Offset(w * 0.82f, 0f), androidx.compose.ui.geometry.Size(w * 0.16f, h))
        }
        Transport.PREV -> {
            drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.25f, h / 2); lineTo(w, h); close() }, color)
            drawRect(color, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w * 0.16f, h))
        }
    }
}

@Composable
fun MusicTile(
    appLabel: String,
    appPackage: String,
    colSpan: Int,
    rowSpan: Int,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onOpenApp: () -> Unit,
    onLongPress: () -> Unit,
    iconContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val media by rememberMediaUi()
    val session = (media as? MediaUi.Session)?.takeIf { it.packageName == appPackage }
    val noAccess = media is MediaUi.NoAccess
    val playing = session?.playing == true
    val accent = LocalAccent.current
    val title = session?.title?.takeIf { it.isNotBlank() } ?: appLabel
    val subtitle = when {
        session != null -> session.artist
        noAccess -> stringResource(R.string.stile_music_no_access)
        else -> stringResource(R.string.stile_music_hint)
    }
    val onBody: () -> Unit = {
        if (noAccess) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } else onOpenApp()
    }
    val onPlay: () -> Unit = {
        val ctl = session?.controller?.transportControls
        if (ctl == null) onOpenApp() else if (playing) ctl.pause() else ctl.play()
    }
    val dPlay = stringResource(R.string.stile_play)
    val dPrev = stringResource(R.string.stile_prev)
    val dNext = stringResource(R.string.stile_next)
    val playKind = if (playing) Transport.PAUSE else Transport.PLAY

    TileFrame(modifier, interactive, onBody, onLongPress) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 200.dp
            val tall = rowSpan >= 2
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                // Cabecera: icono, nombre de la app y barras.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { iconContent() }
                    Spacer(Modifier.size(8.dp))
                    UsosText(appLabel.uppercase(), UsosType.SmallTag, UsosColors.TextSecondary, Modifier.weight(1f))
                    if (!tall) SmartBars(playing, if (playing) accent else UsosColors.TextSecondary, Modifier.size(if (wide) 40.dp else 24.dp, 14.dp))
                }
                if (tall) {
                    Spacer(Modifier.weight(1f))
                    SmartBars(playing, if (playing) accent else UsosColors.BorderStrong, Modifier.fillMaxWidth().height(36.dp))
                    Spacer(Modifier.height(10.dp))
                    UsosText(title, UsosType.PieceData, UsosColors.Text, maxLines = 2)
                    if (subtitle.isNotBlank()) UsosText(subtitle, UsosType.Label, UsosColors.TextSecondary, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (wide && session != null) {
                            TransportButton(Transport.PREV, false, dPrev) { session.controller.transportControls.skipToPrevious() }
                            Spacer(Modifier.size(14.dp))
                        }
                        TransportButton(playKind, true, dPlay, onPlay)
                        if (wide && session != null) {
                            Spacer(Modifier.size(14.dp))
                            TransportButton(Transport.NEXT, false, dNext) { session.controller.transportControls.skipToNext() }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            UsosText(title, UsosType.PieceData, UsosColors.Text)
                            if (subtitle.isNotBlank()) UsosText(subtitle, UsosType.SmallName, UsosColors.TextSecondary)
                        }
                        Spacer(Modifier.size(8.dp))
                        TransportButton(playKind, false, dPlay, onPlay)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Editores
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun EditorScaffold(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler { onClose() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    Column(
        Modifier.fillMaxSize().background(UsosColors.Background).tap { }
            .padding(top = top, start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin),
    ) {
        ScreenHeader(title, onBack = onClose)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(bottom))
        }
    }
}

@Composable
private fun EditorField(label: String, value: String, hint: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    UsosText(label, UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 16.dp, bottom = 6.dp))
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Box(
        Modifier.fillMaxWidth().height(56.dp).background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) UsosText(hint, UsosType.SearchHint, UsosColors.TextSecondary)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = UsosType.SearchInput.themed().copy(color = UsosColors.Text),
            cursorBrush = SolidColor(LocalAccent.current),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun DestinationEditor(
    initial: CustomTile?,
    navApps: List<Pair<String, String>>,
    onSave: (CustomTile) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(initial?.address.orEmpty()) }
    var pkg by rememberSaveable { mutableStateOf(initial?.appPackage.orEmpty()) }
    val id = rememberSaveable { initial?.id ?: CustomTiles.newId() }
    val defaultName = stringResource(R.string.stile_default_dest)
    EditorScaffold(stringResource(R.string.stile_dest_title), onClose) {
        EditorField(stringResource(R.string.stile_name_label), name, stringResource(R.string.stile_name_hint_dest), { name = it })
        EditorField(stringResource(R.string.stile_address_label), address, stringResource(R.string.stile_address_hint), { address = it })
        UsosText(stringResource(R.string.stile_nav_label), UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill(stringResource(R.string.stile_nav_any), filled = pkg.isEmpty(), onClick = { pkg = "" })
            navApps.forEach { (p, label) -> UsosPill(label.uppercase(), filled = pkg == p, onClick = { pkg = p }) }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill(
                stringResource(R.string.stile_save), filled = true, enabled = address.isNotBlank(),
                onClick = {
                    onSave(CustomTile(id = id, kind = TileKind.DEST, name = name.trim().ifBlank { defaultName }, address = address.trim(), appPackage = pkg))
                },
            )
            if (onDelete != null) UsosPill(stringResource(R.string.stile_delete), filled = false, onClick = onDelete)
        }
    }
}

@Composable
fun ContactsEditor(
    initial: CustomTile?,
    onSave: (CustomTile) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    val contacts = remember { mutableStateListOf<TileContact>().also { it.addAll(initial?.contacts.orEmpty()) } }
    val id = rememberSaveable { initial?.id ?: CustomTiles.newId() }
    val defaultName = stringResource(R.string.stile_default_contacts)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst() && contacts.size < CustomTiles.MAX_CONTACTS) {
                    contacts.add(TileContact(name = c.getString(0).orEmpty(), phone = c.getString(1).orEmpty(), app = ContactApp.WHATSAPP))
                }
            }
        }
    }

    EditorScaffold(stringResource(R.string.stile_contacts_title), onClose) {
        EditorField(stringResource(R.string.stile_name_label), name, stringResource(R.string.stile_name_hint_contacts), { name = it })
        Spacer(Modifier.height(16.dp))
        contacts.forEachIndexed { i, c ->
            val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
            Column(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).background(UsosColors.Surface, shape)
                    .border(1.dp, UsosColors.Border, shape).padding(14.dp),
            ) {
                UsosText(c.name, UsosType.Pill, UsosColors.Text)
                UsosText(c.phone, UsosType.SmallName, UsosColors.TextSecondary)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContactApp.entries.forEach { app ->
                        UsosPill(contactAppLabel(app), filled = c.app == app, onClick = { contacts[i] = c.copy(app = app) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                UsosPill(stringResource(R.string.stile_remove), filled = false, onClick = { contacts.removeAt(i) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (contacts.size < CustomTiles.MAX_CONTACTS) {
            UsosPill(stringResource(R.string.stile_add_contact), filled = false, onClick = {
                picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            })
        } else {
            UsosText(stringResource(R.string.stile_contacts_full), UsosType.Label, UsosColors.TextSecondary)
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsosPill(
                stringResource(R.string.stile_save), filled = true, enabled = contacts.isNotEmpty(),
                onClick = {
                    onSave(CustomTile(id = id, kind = TileKind.CONTACTS, name = name.trim().ifBlank { defaultName }, contacts = contacts.toList()))
                },
            )
            if (onDelete != null) UsosPill(stringResource(R.string.stile_delete), filled = false, onClick = onDelete)
        }
    }
}

@Composable
private fun contactAppLabel(app: ContactApp): String = stringResource(
    when (app) {
        ContactApp.WHATSAPP -> R.string.stile_app_whatsapp
        ContactApp.TELEGRAM -> R.string.stile_app_telegram
        ContactApp.SMS -> R.string.stile_app_sms
        ContactApp.CALL -> R.string.stile_app_call
    },
)
