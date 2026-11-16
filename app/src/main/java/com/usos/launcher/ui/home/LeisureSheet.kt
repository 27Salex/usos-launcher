package com.usos.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.usos.launcher.data.leisure.LeisureStatus
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.tap

/**
 * Panel de Ocio a pantalla completa. Se usa cuando Ocio está oculta en inicio y se toca una app de ocio congelada
 * (desde la búsqueda o el dock): permite descongelar igual que la sección. Se cierra solo al quedar abierto.
 */
@Composable
fun LeisureSheet(status: LeisureStatus?, onThaw: (Int) -> Unit, onCancelThaw: () -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    LaunchedEffect(status) { if (status is LeisureStatus.Open) onClose() }
    Column(
        Modifier.fillMaxSize().background(UsosColors.Background).tap { }
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                start = UsosDimens.ScreenMargin, end = UsosDimens.ScreenMargin,
            ),
    ) {
        ScreenHeader(stringResource(Section.LEISURE.titleRes).uppercase(), onBack = onClose)
        Spacer(Modifier.height(16.dp))
        LeisurePanel(status, onThaw, onCancelThaw)
    }
}
