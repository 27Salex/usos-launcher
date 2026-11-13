package com.usos.launcher.ui.search

import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.usos.launcher.ui.theme.SettingsGlyph
import com.usos.launcher.ui.theme.swipeVertical
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.remember
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.LocalOnAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.TileHead
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap

/** Alto de la barra en reposo (inicio) y como campo. */
val SearchBarRestHeight = 52.dp
val SearchFieldHeight = 56.dp
private val GridGap = 8.dp
private val SmallTileHeight = 84.dp

/**
 * Pantalla de busqueda. [progress] (0..1) lo anima el llamador en 250 ms: la barra viaja desde su sitio
 * de reposo (abajo) hasta arriba y se convierte en campo; el fondo y la lista aparecen a la vez.
 */
@Composable
fun SearchOverlay(
    open: Boolean,
    progress: Float,
    query: String,
    items: List<SearchItem>,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPick: (SearchItem) -> Unit,
    onLongPick: (SearchItem) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    autoKeyboard: Boolean = false,
    assistantName: String? = null,
    onAsk: (String) -> Unit = {},
    restEndInset: Dp = 0.dp,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(open) {
        if (open) {
            // Sin teclado al abrir: solo se abre al tocar el campo (salvo el ajuste «Abrir teclado al buscar»).
            if (autoKeyboard) {
                focus.requestFocus()
                keyboard?.show()
            }
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // El teclado intercepta el primer "atras": si se oculta con la busqueda abierta, se cierra la busqueda
    // en esa misma pulsacion (una sola en total).
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var imeWasShown by remember(open) { mutableStateOf(false) }
    LaunchedEffect(open, imeVisible) {
        if (!open) return@LaunchedEffect
        if (imeVisible) imeWasShown = true else if (imeWasShown) onClose()
    }
    if (!open && progress <= 0f) return

    val p = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(UsosColors.Background.copy(alpha = p))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val restTop = maxHeight - navBar - 12.dp - SearchBarRestHeight
        val topY = statusTop + 8.dp

        val groups = remember(items, query) { SearchFilter.groups(items, query) }
        val showSettings = SearchFilter.matchesSettings(query)
        val listState = rememberLazyListState()
        Column(
            Modifier
                .fillMaxSize()
                // Deslizar hacia abajo con la lista arriba del todo cierra la busqueda.
                .swipeVertical(accept = { !listState.canScrollBackward }, onDown = onClose)
                .alpha(p)
                .padding(top = topY + SearchFieldHeight + 8.dp)
                .imePadding(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin,
                    top = 8.dp, bottom = navBar + 16.dp,
                ),
            ) {
                if (query.isNotBlank()) {
                    item(key = "action") {
                        val intent = remember(items, query) { SearchClassifier.classify(query, items) { it.app.label } }
                        val label = when (intent) {
                            is SearchIntent.OpenUrl -> stringResource(R.string.search_open, intent.url.substringAfter("://").trimEnd('/').uppercase())
                            is SearchIntent.OpenApp<*> -> stringResource(R.string.search_open, (intent.app as SearchItem).app.label.uppercase())
                            is SearchIntent.WebSearch -> stringResource(R.string.search_web, intent.text)
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp).height(UsosDimens.MinTouch + 8.dp)
                                .background(LocalAccent.current, RoundedCornerShape(UsosDimens.RadiusPiece))
                                .tap { onSubmit() }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { UsosText(label, UsosType.Button, LocalOnAccent.current) }
                    }
                }
                if (query.isNotBlank() && assistantName != null) {
                    item(key = "ask") {
                        val askShape = RoundedCornerShape(UsosDimens.RadiusPiece)
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp).height(UsosDimens.MinTouch + 8.dp)
                                .background(UsosColors.Surface, askShape)
                                .border(1.dp, LocalAccent.current, askShape)
                                .tap { onAsk(query) }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { UsosText(stringResource(R.string.search_ask, assistantName, query.trim()), UsosType.Button, LocalAccent.current, maxLines = 1) }
                    }
                }
                if (showSettings) {
                    item(key = "settings") {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp).height(UsosDimens.MinTouch + 8.dp)
                                .background(UsosColors.Surface, RoundedCornerShape(UsosDimens.RadiusPiece))
                                .border(1.dp, LocalAccent.current, RoundedCornerShape(UsosDimens.RadiusPiece))
                                .tap { onOpenSettings() }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { UsosText(stringResource(R.string.search_settings), UsosType.Button, LocalAccent.current) }
                    }
                }
                if (groups.isEmpty() && !showSettings) {
                    item(key = "empty") {
                        UsosText(stringResource(R.string.search_nothing), UsosType.Empty, UsosColors.TextSecondary, Modifier.padding(vertical = 8.dp))
                    }
                }
                groups.forEach { group ->
                    item(key = "h-" + group.section.name) {
                        UsosText(
                            stringResource(group.section.titleRes).uppercase(), UsosType.Label, UsosColors.TextSecondary,
                            Modifier.padding(top = 14.dp, bottom = 8.dp),
                        )
                    }
                    items(group.items.chunked(4), key = { row -> group.section.name + row.first().app.key }) { row ->
                        GridRow(row, onPick, onLongPick)
                    }
                }
                if (suggestions.isNotEmpty()) {
                    item(key = "h-suggest") {
                        UsosText(stringResource(R.string.search_suggestions), UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 14.dp, bottom = 4.dp))
                    }
                    items(suggestions, key = { "s-" + it }) { text ->
                        Box(
                            Modifier.fillMaxWidth().height(UsosDimens.MinTouch).tap { onSuggestion(text) },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            UsosText(text, UsosType.Pill, UsosColors.Text, maxLines = 1)
                        }
                    }
                }
            }
        }

        // La barra, encima de todo.
        val barHeight = lerp(SearchBarRestHeight, SearchFieldHeight, p)
        val radius = lerp(UsosDimens.RadiusSearch, UsosDimens.RadiusPiece, p)
        val shape = RoundedCornerShape(radius)
        Box(
            Modifier
                .padding(horizontal = UsosDimens.ScreenMargin)
                .padding(end = lerp(restEndInset, 0.dp, p))
                .offset { IntOffset(0, lerp(restTop, topY, p).roundToPx()) }
                .fillMaxWidth()
                .height(barHeight)
                .background(UsosColors.Surface, shape)
                .border(1.dp, UsosColors.Border, shape)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) UsosText(stringResource(R.string.search_hint), UsosType.SearchHint, UsosColors.TextSecondary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = UsosType.SearchInput.copy(color = UsosColors.Text),
                cursorBrush = SolidColor(LocalAccent.current),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().padding(end = 36.dp).focusRequester(focus),
            )
            SettingsGlyph(onOpenSettings, Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).alpha(p))
        }
    }
}

@Composable
private fun GridRow(row: List<SearchItem>, onPick: (SearchItem) -> Unit, onLongPick: (SearchItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = GridGap),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        row.forEach { item -> SmallTile(item, Modifier.weight(1f), onPick, onLongPick) }
        repeat(4 - row.size) { Box(Modifier.weight(1f)) }
    }
}

@Composable
private fun SmallTile(
    item: SearchItem,
    modifier: Modifier,
    onPick: (SearchItem) -> Unit,
    onLongPick: (SearchItem) -> Unit,
) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    val dim = item.app.isQuiet
    val nameColor = if (item.frozen) UsosColors.LeisureFrozen else UsosColors.TextSecondary
    Column(
        modifier
            .height(SmallTileHeight)
            .alpha(if (dim) 0.35f else 1f)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .tap(onLongClick = { onLongPick(item) }) { onPick(item) }
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TileHead(item.app, item.code, UsosType.SmallCode, if (item.frozen) UsosColors.LeisureFrozen else UsosColors.Text, 22.dp, invert = false)
        Column {
            UsosText(item.app.label.uppercase(), UsosType.SmallName, nameColor)
            if (item.frozen) {
                UsosText(stringResource(R.string.search_leisure_frozen), UsosType.SmallTag, LocalAccent.current, maxLines = 2)
            }
        }
    }
}
