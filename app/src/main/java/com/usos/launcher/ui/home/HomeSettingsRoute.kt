package com.usos.launcher.ui.home

// Pantalla de Ajustes conectada al ViewModel: lee el estado y le pasa cada acción.

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


@Composable
internal fun SettingsRoute(
    viewModel: HomeViewModel,
    category: String?,
    grayscale: Boolean,
    iconPrefs: com.usos.launcher.data.sections.IconPrefs,
    assistantUi: HomeViewModel.AssistantUi,
    bgPrefs: com.usos.launcher.data.sections.BackgroundPrefs,
    dockPrefs: com.usos.launcher.data.sections.DockPrefs,
) {
        val budget by viewModel.budget.collectAsStateWithLifecycle()
        val wait by viewModel.wait.collectAsStateWithLifecycle()
        val orderLists by viewModel.orderLists.collectAsStateWithLifecycle()
        val suggestionsOn by viewModel.suggestionsOn.collectAsStateWithLifecycle()
        val perHour by viewModel.feedsPerHour.collectAsStateWithLifecycle()
        val calendarGranted by viewModel.calendarGranted.collectAsStateWithLifecycle()
        val provider by viewModel.suggestProvider.collectAsStateWithLifecycle()
        val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
        val recentColors by viewModel.recentColors.collectAsStateWithLifecycle()
        val clock by viewModel.clockPrefs.collectAsStateWithLifecycle()
        val weatherPrefs by viewModel.weatherPrefs.collectAsStateWithLifecycle()
        val cities by viewModel.cityResults.collectAsStateWithLifecycle()
        val mosaicPrefs by viewModel.mosaicPrefs.collectAsStateWithLifecycle()
        val infoSpecs by viewModel.infoSpecs.collectAsStateWithLifecycle()
        val searchKeyboard by viewModel.searchKeyboard.collectAsStateWithLifecycle()
        val thumbsGray by viewModel.thumbsGray.collectAsStateWithLifecycle()
        val feedsKeep by viewModel.feedsKeep.collectAsStateWithLifecycle()
        val clockTap by viewModel.clockTap.collectAsStateWithLifecycle()
        val fontChoices by viewModel.fontChoices.collectAsStateWithLifecycle()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val versionName = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
        val allApps = remember(orderLists) { orderLists.flatMap { (_, l) -> l.map { it.app } }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
        SettingsScreen(
            category,
            com.usos.launcher.ui.settings.SettingsUi(
                budget, wait, orderLists, suggestionsOn, provider, grayscale, perHour, calendarGranted,
                clock, weatherPrefs, cities, iconPrefs, mosaicPrefs, infoSpecs, searchEngine, recentColors,
                searchKeyboard, assistantUi, feedsKeep, clockTap, allApps, versionName, thumbsGray,
                fontChoices.first, fontChoices.second, bgPrefs, dockPrefs,
            ),
            SettingsActions(
                onBudget = viewModel::setBudget,
                onWait = viewModel::setWait,
                onAccent = viewModel::setAccent,
                onCustomAccent = viewModel::setCustomAccent,
                onSearchEngine = viewModel::setSearchEngine,
                onSuggestionsOn = viewModel::setSuggestionsOn,
                onProvider = viewModel::setSuggestProvider,
                onGrayscale = viewModel::setWidgetsGrayscale,
                onFeedsPerHour = viewModel::setFeedsPerHour,
                onCalendarPermission = viewModel::requestCalendar,
                onMove = viewModel::moveInSection,
                onRestartOnboarding = viewModel::restartOnboarding,
                onClockType = viewModel::setClockType,
                onClockSeconds = viewModel::setClockSeconds,
                onClockAnimated = viewModel::setClockAnimated,
                onWeatherOn = viewModel::setWeatherOn,
                onWeatherFahrenheit = viewModel::setWeatherFahrenheit,
                onWeatherIcon = viewModel::setWeatherIcon,
                onWeatherText = viewModel::setWeatherText,
                onWeatherMinMax = viewModel::setWeatherMinMax,
                onWeatherAuto = { if (it) viewModel.setWeatherAuto() else viewModel.setWeatherManual() },
                onSearchCity = viewModel::searchCities,
                onPickCity = viewModel::pickCity,
                onWeatherBelow = viewModel::setWeatherBelowClock,
                onWeatherAccent = viewModel::setWeatherAccent,
                onBlackWallpaper = viewModel::applyBlackWallpaper,
                onIconMode = viewModel::setIconMode,
                onIconShowCode = viewModel::setIconShowCode,
                onDockOn = viewModel::setDockOn,
                onDockSlots = viewModel::setDockSlots,
                onColumns = viewModel::setColumns,
                onRows = viewModel::setRows,
                onSectionMove = viewModel::moveSectionTab,
                onSectionHidden = viewModel::setSectionHidden,
                onHomeTabs = viewModel::setHomeTabs,
                onMusicPlayer = viewModel::setMusicPlayer,
                onDrawerTabs = viewModel::setDrawerTabs,
                onInfoSize = viewModel::setInfoSize,
                onInfoHidden = viewModel::setInfoHidden,
                onEditMosaic = viewModel::startHomeEdit,
                onSearchKeyboard = viewModel::setSearchKeyboard,
                onAssistant = viewModel::setAssistantChoice,
                onFeedsKeep = viewModel::setFeedsKeep,
                onClockTap = viewModel::setClockTap,
                onThumbsGray = viewModel::setThumbsGray,
                onFontTitle = viewModel::setFontTitle,
                onFontText = viewModel::setFontText,
                onBackgroundMode = viewModel::setBackgroundMode,
                onBackgroundColor = viewModel::setBackgroundColor,
                onBackgroundDim = viewModel::setBackgroundDim,
                onBackgroundBlur = viewModel::setBackgroundBlur,
                onPickBackground = viewModel::pickBackgroundImage,
                onBack = viewModel::closeOverlay,
            ),
        )
}
