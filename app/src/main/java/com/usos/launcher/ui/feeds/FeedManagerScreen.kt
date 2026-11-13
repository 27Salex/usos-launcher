package com.usos.launcher.ui.feeds

import com.usos.launcher.ui.theme.themed
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.usos.launcher.R
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usos.launcher.data.feeds.Feed
import com.usos.launcher.data.feeds.SuggestedFeeds
import com.usos.launcher.ui.theme.tap
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType

/** Gestión de feeds: buscar por URL de feed, de web o dominio; sugeridos de un toque; renombrar y borrar. */
@Composable
fun FeedManagerScreen(vm: FeedsViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val feeds by vm.feeds.collectAsStateWithLifecycle()
    val discover by vm.discover.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    val busy = discover is DiscoverUi.Loading || adding
    val have = feeds.map { it.url }.toSet()
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
        ScreenHeader(stringResource(R.string.feeds_manager_title), onBack = onBack)
        Spacer(Modifier.height(16.dp))
        UsosText(stringResource(R.string.feeds_add_label), UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        TextBox(url, { url = it }, stringResource(R.string.feeds_url_hint), ImeAction.Go, onGo = { if (url.isNotBlank() && !busy) vm.search(url) })
        Spacer(Modifier.height(8.dp))
        UsosPill(
            stringResource(if (discover is DiscoverUi.Loading) R.string.feeds_searching else R.string.feeds_search), filled = true, enabled = !busy && url.isNotBlank(),
            onClick = { vm.search(url) }, modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
            message?.let { UsosText(it, UsosType.Label, LocalAccent.current) }
        }
        (discover as? DiscoverUi.Results)?.let { r ->
            UsosText(stringResource(R.string.feeds_found), UsosType.Label, UsosColors.TextSecondary)
            r.feeds.forEach { f ->
                val added = f.url in have
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        UsosText(f.title, UsosType.PieceData, UsosColors.Text)
                        UsosText("${pluralStringResource(R.plurals.feeds_item_count, f.itemCount, f.itemCount)} · ${f.url.substringAfter("://")}", UsosType.Label, UsosColors.TextSecondary)
                    }
                    UsosPill(
                        stringResource(if (added) R.string.feeds_already_have else R.string.feeds_add), filled = !added, enabled = !added,
                        onClick = { vm.addFound(f, onBack); url = "" },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        UsosText(stringResource(R.string.feeds_suggested), UsosType.Label, UsosColors.TextSecondary)
        val pending = SuggestedFeeds.forLanguage(stringResource(R.string.feeds_language)).filter { it.second !in have }
        if (pending.isEmpty()) UsosText(stringResource(R.string.feeds_have_all), UsosType.Label, UsosColors.TextSecondary, Modifier.padding(vertical = 12.dp))
        pending.forEach { (name, feedUrl) ->
            Row(
                Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp).tap(enabled = !busy) { vm.addSuggested(name, feedUrl, onBack) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                UsosText(name, UsosType.Pill, UsosColors.Text, Modifier.weight(1f))
                UsosText("+", UsosType.PieceData, LocalAccent.current)
            }
        }

        Spacer(Modifier.height(20.dp))
        UsosText(stringResource(R.string.feeds_yours), UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        if (feeds.isEmpty()) {
            UsosText(stringResource(R.string.feeds_none_yet), UsosType.Label, UsosColors.TextSecondary, Modifier.padding(top = 12.dp))
        }
        feeds.forEach { FeedRow(it, vm) }
    }
}

@Composable
private fun FeedRow(feed: Feed, vm: FeedsViewModel) {
    var editing by rememberSaveable(feed.id) { mutableStateOf(false) }
    var name by rememberSaveable(feed.id, editing) { mutableStateOf(feed.title) }
    var confirmDelete by rememberSaveable(feed.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        if (editing) {
            TextBox(name, { name = it }, stringResource(R.string.feeds_name_hint), ImeAction.Done, onGo = { vm.rename(feed.id, name); editing = false })
        } else {
            UsosText(feed.title, UsosType.PieceData, UsosColors.Text)
        }
        UsosText(feed.url, UsosType.Label, UsosColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                UsosPill(stringResource(R.string.save), filled = true, onClick = { vm.rename(feed.id, name); editing = false })
                UsosPill(stringResource(R.string.cancel), filled = false, onClick = { editing = false })
            } else {
                UsosPill(stringResource(R.string.feeds_rename), filled = false, onClick = { editing = true })
                UsosPill(
                    stringResource(if (confirmDelete) R.string.feeds_delete_confirm else R.string.feeds_delete), filled = confirmDelete,
                    onClick = { if (confirmDelete) vm.delete(feed.id) else confirmDelete = true },
                )
            }
        }
    }
}

@Composable
private fun TextBox(value: String, onChange: (String) -> Unit, hint: String, ime: ImeAction, onGo: () -> Unit) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPill)
    Box(
        Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 8.dp)
            .background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) UsosText(hint, UsosType.Empty, UsosColors.TextSecondary)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = UsosType.Empty.themed().copy(color = UsosColors.Text),
            cursorBrush = SolidColor(UsosColors.Text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ime),
            keyboardActions = KeyboardActions(onAny = { onGo() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
