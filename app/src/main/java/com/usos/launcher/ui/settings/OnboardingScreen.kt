package com.usos.launcher.ui.settings

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.usos.launcher.R
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.usos.launcher.data.usage.hasUsageAccess
import com.usos.launcher.service.isBlockerEnabled
import com.usos.launcher.ui.theme.LocalAccent
import com.usos.launcher.ui.theme.UsosColors
import com.usos.launcher.ui.theme.ScreenHeader
import com.usos.launcher.ui.theme.UsosDimens
import com.usos.launcher.ui.theme.UsosPill
import com.usos.launcher.ui.theme.UsosText
import com.usos.launcher.ui.theme.UsosType

private fun isDefaultHome(context: Context): Boolean {
    val rm = context.getSystemService(RoleManager::class.java)
    return rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
}

private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

private fun appDetails(context: Context) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName),
)

/** Primer arranque: cuatro pasos para dejar el launcher y el bloqueo funcionando. Se puede saltar. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    BackHandler { onFinish() }
    val context = LocalContext.current
    // Se recalcula al volver de los ajustes del sistema.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val homeOk = remember(tick) { isDefaultHome(context) }
    val accessOk = remember(tick) { isBlockerEnabled(context) }
    val usageOk = remember(tick) { hasUsageAccess(context) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

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
        ScreenHeader(stringResource(R.string.onboarding_title), onBack = onFinish)
        Spacer(Modifier.height(8.dp))
        UsosText(
            stringResource(R.string.onboarding_intro),
            UsosType.Label, UsosColors.TextSecondary, maxLines = 3,
        )
        Spacer(Modifier.height(20.dp))

        Step(
            stringResource(R.string.onboarding_step1_title), ok = homeOk,
            text = stringResource(R.string.onboarding_step1_text),
        ) {
            UsosPill(stringResource(R.string.onboarding_pick_home), filled = !homeOk, modifier = Modifier.fillMaxWidth(), onClick = {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                } else {
                    start(context, Intent(Settings.ACTION_HOME_SETTINGS))
                }
            })
        }
        Step(
            stringResource(R.string.onboarding_step2_title), ok = accessOk,
            text = stringResource(R.string.onboarding_step2_text),
        ) {
            UsosPill(stringResource(R.string.onboarding_open_app_info), filled = false, modifier = Modifier.fillMaxWidth(),
                onClick = { start(context, appDetails(context)) })
            Spacer(Modifier.height(8.dp))
            UsosPill(stringResource(R.string.onboarding_open_accessibility), filled = !accessOk, modifier = Modifier.fillMaxWidth(),
                onClick = { start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        }
        Step(
            stringResource(R.string.onboarding_step3_title), ok = usageOk,
            text = stringResource(R.string.onboarding_step3_text),
        ) {
            UsosPill(stringResource(R.string.onboarding_open_usage), filled = !usageOk, modifier = Modifier.fillMaxWidth(), onClick = {
                start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName)))
            })
        }
        val notifOk = remember(tick) { com.usos.launcher.service.NotificationState.isGranted(context) }
        Step(
            stringResource(R.string.onboarding_notif_title), ok = notifOk,
            text = stringResource(R.string.onboarding_notif_text),
        ) {
            UsosPill(stringResource(R.string.settings_notif_grant), filled = false, modifier = Modifier.fillMaxWidth(),
                onClick = { start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        }
        Step(
            stringResource(R.string.onboarding_step4_title), ok = null,
            text = stringResource(R.string.onboarding_battery_hint, stringResource(batteryDetailRes(android.os.Build.MANUFACTURER))),
        ) {
            UsosPill(stringResource(R.string.onboarding_open_app_info), filled = false, modifier = Modifier.fillMaxWidth(),
                onClick = { start(context, appDetails(context)) })
        }

        Spacer(Modifier.height(12.dp))
        val allOk = homeOk && accessOk && usageOk
        UsosPill(stringResource(if (allOk) R.string.done else R.string.onboarding_skip), filled = allOk, onClick = onFinish, modifier = Modifier.fillMaxWidth())
    }
}

/** Tarjeta de un paso. `ok` null = no se puede comprobar. */
@Composable
private fun Step(title: String, ok: Boolean?, text: String, actions: @Composable () -> Unit) {
    val shape = RoundedCornerShape(UsosDimens.RadiusPiece)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(UsosColors.Surface, shape)
            .border(1.dp, UsosColors.Border, shape)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UsosText(title, UsosType.PanelState, UsosColors.Text, Modifier.weight(1f, fill = false))
            UsosText(
                stringResource(when (ok) { true -> R.string.onboarding_done; false -> R.string.onboarding_pending; null -> R.string.onboarding_manual }),
                UsosType.Label, if (ok == true) LocalAccent.current else UsosColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        UsosText(text, UsosType.Label, UsosColors.TextSecondary, maxLines = 12)
        Spacer(Modifier.height(12.dp))
        actions()
    }
}

/** Pasos de batería para que el sistema no detenga el servicio, adaptados al fabricante (recurso por fabricante). */
@StringRes
fun batteryDetailRes(manufacturer: String): Int {
    val m = manufacturer.lowercase()
    return when {
        "samsung" in m -> R.string.onboarding_battery_samsung
        "xiaomi" in m || "redmi" in m || "poco" in m -> R.string.onboarding_battery_xiaomi
        "oppo" in m || "realme" in m || "oneplus" in m -> R.string.onboarding_battery_oppo
        "huawei" in m || "honor" in m -> R.string.onboarding_battery_huawei
        else -> R.string.onboarding_battery_generic
    }
}
