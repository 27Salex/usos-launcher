package com.usos.launcher.ui.settings

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
        ScreenHeader("Primer arranque", onBack = onFinish)
        Spacer(Modifier.height(8.dp))
        UsosText(
            "CUATRO PASOS. PUEDES SALTARLOS Y VOLVER DESDE AJUSTES (PULSACIÓN LARGA EN EL RELOJ).",
            UsosType.Label, UsosColors.TextSecondary, maxLines = 3,
        )
        Spacer(Modifier.height(20.dp))

        Step(
            "1 · APP DE INICIO", ok = homeOk,
            text = "Para que el botón de inicio abra Usos.",
        ) {
            UsosPill("ELEGIR USOS COMO INICIO", filled = !homeOk, modifier = Modifier.fillMaxWidth(), onClick = {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                } else {
                    start(context, Intent(Settings.ACTION_HOME_SETTINGS))
                }
            })
        }
        Step(
            "2 · ACCESIBILIDAD", ok = accessOk,
            text = "Permite congelar las apps de ocio aunque las abras desde una notificación. Solo detecta qué app se abre, no lee la pantalla. " +
                "Si Android la muestra atenuada (\"ajuste restringido\", por instalar fuera de la tienda): " +
                "Info de la app > ⋮ (arriba a la derecha) > Permitir ajustes restringidos, y vuelve aquí.",
        ) {
            UsosPill("ABRIR INFO DE LA APP", filled = false, modifier = Modifier.fillMaxWidth(),
                onClick = { start(context, appDetails(context)) })
            Spacer(Modifier.height(8.dp))
            UsosPill("ABRIR ACCESIBILIDAD", filled = !accessOk, modifier = Modifier.fillMaxWidth(),
                onClick = { start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        }
        Step(
            "3 · ACCESO A DATOS DE USO", ok = usageOk,
            text = "Para mostrar el tiempo de uso y el almacenamiento de cada app.",
        ) {
            UsosPill("ABRIR ACCESO A DATOS DE USO", filled = !usageOk, modifier = Modifier.fillMaxWidth(), onClick = {
                start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName)))
            })
        }
        Step(
            "4 · BATERÍA", ok = null,
            text = batteryHint(android.os.Build.MANUFACTURER),
        ) {
            UsosPill("ABRIR INFO DE LA APP", filled = false, modifier = Modifier.fillMaxWidth(),
                onClick = { start(context, appDetails(context)) })
        }

        Spacer(Modifier.height(12.dp))
        val allOk = homeOk && accessOk && usageOk
        UsosPill(if (allOk) "LISTO" else "SALTAR POR AHORA", filled = allOk, onClick = onFinish, modifier = Modifier.fillMaxWidth())
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
                when (ok) { true -> "HECHO"; false -> "PENDIENTE"; null -> "MANUAL" },
                UsosType.Label, if (ok == true) LocalAccent.current else UsosColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        UsosText(text, UsosType.Label, UsosColors.TextSecondary, maxLines = 12)
        Spacer(Modifier.height(12.dp))
        actions()
    }
}

/** Pasos de batería para que el sistema no detenga el servicio, adaptados al fabricante. */
fun batteryHint(manufacturer: String): String {
    val m = manufacturer.lowercase()
    val detail = when {
        "samsung" in m -> "Batería > Límites de uso en segundo plano > Apps que nunca se suspenden > añade Usos."
        "xiaomi" in m || "redmi" in m || "poco" in m ->
            "Info de la app > Ahorro de batería > Sin restricciones, y activa el inicio automático."
        "oppo" in m || "realme" in m || "oneplus" in m -> "Info de la app > Uso de batería > Permitir actividad en segundo plano."
        "huawei" in m || "honor" in m -> "Batería > Inicio de aplicaciones > Usos > gestionar manualmente (todo activado)."
        else -> "Ajustes > Apps > Usos > Batería > Sin restricciones."
    }
    return "Para que el sistema no detenga el servicio: $detail O bien Info de la app > Batería > Sin restricciones."
}
