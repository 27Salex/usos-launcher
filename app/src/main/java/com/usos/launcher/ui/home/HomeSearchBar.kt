package com.usos.launcher.ui.home

// Barra BUSCAR, botón de IA y selector rápido del asistente.

import androidx.compose.runtime.snapshotFlow
import android.util.Log
import com.usos.launcher.BuildConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.usos.launcher.ui.theme.LocalOnAccent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import com.usos.launcher.data.leisure.LeisureStatus
import com.usos.launcher.data.sections.Highlight
import com.usos.launcher.data.usage.UsageSummary
import com.usos.launcher.ui.appdetail.AppDetailScreen
import com.usos.launcher.ui.appdetail.DetailActions
import com.usos.launcher.ui.settings.OnboardingScreen
import com.usos.launcher.ui.settings.SettingsActions
import com.usos.launcher.ui.settings.SettingsScreen
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.search.SearchOverlay
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.onColor
import androidx.compose.ui.graphics.Color
import com.usos.launcher.ui.theme.LocalOnAccent
import com.usos.launcher.ui.theme.LocalAnimationsEnabled
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.TileHead
import com.usos.launcher.ui.theme.LocalIconPrefs
import com.usos.launcher.ui.theme.LocalIcons
import androidx.compose.runtime.CompositionLocalProvider
import com.usos.launcher.ui.theme.UsosType
import com.usos.launcher.ui.theme.tap
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.key
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import com.usos.launcher.ui.theme.Bubble
import com.usos.launcher.ui.theme.BubbleKind
import com.usos.launcher.ui.theme.BubbleRow
import com.usos.launcher.ui.theme.ResizeHandles
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.withFrameNanos
import com.usos.launcher.data.sections.Cell
import com.usos.launcher.data.sections.MosaicEdit
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.ui.theme.dashedBorder
import com.usos.launcher.ui.theme.pressGestures
import com.usos.launcher.ui.theme.swipeVertical
import com.usos.launcher.ui.theme.tapOutside
import kotlin.math.roundToInt
import com.usos.launcher.ui.theme.usosTween


/** Barra fija de búsqueda. Al tocarla sube y se convierte en campo (SearchOverlay). */
@Composable
internal fun SearchBar(notice: String?, hidden: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(UsosDimens.RadiusSearch)
    Box(
        modifier
            .height(52.dp)
            .alpha(if (hidden) 0f else 1f)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .tap(enabled = !hidden) { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        UsosText(notice ?: stringResource(R.string.search_hint), UsosType.SearchHint, UsosColors.TextSecondary)
    }
}

/** Hueco entre la barra BUSCAR y el botón de IA. */
internal val AiGap = 8.dp
internal val AiButtonSize = 52.dp

/** Botón redondo de IA a la derecha de la barra: glifo de puntos propio o, con iconos activados, el icono de la app del asistente. */
@Composable
internal fun AiButton(assistant: HomeViewModel.AssistantUi, hidden: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier
            .size(AiButtonSize)
            .alpha(if (hidden) 0f else 1f)
            .background(UsosColors.Surface, CircleShape)
            .border(1.dp, UsosColors.Border, CircleShape)
            .tap(enabled = !hidden, onLongClick = onLongClick) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val entry = assistant.entry
        val mode = com.usos.launcher.ui.theme.LocalIconPrefs.current.mode
        if (entry != null && mode != com.usos.launcher.data.sections.IconMode.NONE) {
            com.usos.launcher.ui.theme.AppIconOrNull(entry, 28.dp, UsosColors.Text) { AiGlyph() }
        } else {
            AiGlyph()
        }
    }
}

/** Chispa de cuatro puntas hecha con puntos (rejilla 5x5), sin logos de marca. */
@Composable
fun AiGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val step = size.width / 5f
        val r = step * 0.28f
        val on = listOf(2 to 0, 2 to 1, 0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 2 to 3, 2 to 4)
        on.forEach { (cx, cy) ->
            drawCircle(UsosColors.Text, r * if (cx == 2 && cy == 2) 1.25f else 1f, androidx.compose.ui.geometry.Offset(step * (cx + 0.5f), step * (cy + 0.5f)))
        }
    }
}

/** Selector rápido del asistente (pulsación larga en el botón de IA). */
@Composable
internal fun AssistantPicker(assistant: HomeViewModel.AssistantUi, onPick: (String) -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier.fillMaxSize().background(UsosColors.Background.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = UsosDimens.ScreenMargin).padding(bottom = 12.dp + navBar)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UsosText(stringResource(R.string.search_assistant), UsosType.Label, UsosColors.TextSecondary)
            val current = assistant.resolved
            assistant.installed.forEach { a ->
                val sel = current != null && !current.web && current.assistant == a
                Box(
                    Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp)
                        .background(if (sel) LocalAccent.current else UsosColors.Surface, shape)
                        .border(1.dp, UsosColors.Border, shape)
                        .tap { onPick(com.usos.launcher.data.ai.Assistants.appChoice(a)) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { UsosText(a.label, UsosType.Button, if (sel) LocalOnAccent.current else UsosColors.Text) }
            }
            Box(
                Modifier.fillMaxWidth().height(UsosDimens.MinTouch + 4.dp)
                    .background(UsosColors.Surface, shape).border(1.dp, UsosColors.Border, shape)
                    .tap { onPick(com.usos.launcher.data.ai.Assistants.NONE) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) { UsosText(stringResource(R.string.none), UsosType.Button, UsosColors.TextSecondary) }
        }
    }
}
