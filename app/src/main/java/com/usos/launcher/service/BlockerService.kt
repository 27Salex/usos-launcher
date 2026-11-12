package com.usos.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.usos.launcher.UsosApp
import com.usos.launcher.data.leisure.LeisureStatus
import com.usos.launcher.domain.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Aviso del servicio al launcher (mismo proceso). El launcher lo consume y lo muestra en la barra. */
data class LauncherAlert(val text: String, val revealLeisure: Boolean, val atMs: Long = SystemClock.elapsedRealtime()) {
    companion object {
        const val FROZEN = "CONGELADA"
        const val FROZEN_AGAIN = "OCIO CONGELADO DE NUEVO"
    }
}

/** Decide si un paquete en primer plano debe ignorarse como "ventana de otra app". Pura. */
object BlockerRules {
    const val SYSTEM_UI = "com.android.systemui"

    fun ignored(pkg: String?, ownPackage: String, imes: Set<String>): Boolean =
        pkg == null || pkg == ownPackage || pkg == SYSTEM_UI || pkg in imes
}

/** El servicio de bloqueo esta activado en los ajustes de accesibilidad. */
fun isBlockerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val me = ComponentName(context, BlockerService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}

/**
 * Hace cumplir el bloqueo del ocio (PLAN: "Servicio de accesibilidad"). Solo escucha
 * TYPE_WINDOW_STATE_CHANGED y no lee contenido de ventanas.
 */
class BlockerService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as UsosApp).container
    private val repo get() = container.leisureRepository

    private var foreground: String? = null
    private var leisurePackages: Set<String> = emptySet()
    private var imes: Set<String> = emptySet()
    private var imesAt = 0L
    private var timer: Job? = null
    private var wasOpen = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            // Paso 5: lee el estado guardado; load() cierra y contabiliza una sesion ya vencida.
            repo.load()
            repo.tick()
            repo.status.collect { onStatus(it) }
        }
        scope.launch {
            container.appConfigStore.configs.collect { configs ->
                leisurePackages = configs.values.filter { it.section == Section.LEISURE }
                    .map { it.packageName }.toSet()
            }
        }
    }

    /** Pasos 4 y 5: reprograma el temporizador y expulsa al cerrarse una sesion con ocio delante. */
    private fun onStatus(status: LeisureStatus?) {
        val open = status is LeisureStatus.Open
        if (open) {
            val endsAt = (status as LeisureStatus.Open).endsAt
            // Se reprograma solo si cambia el fin (el estado se republica cada minuto).
            if (timer == null || timer?.isActive != true || scheduledEnd != endsAt) schedule(endsAt)
        } else {
            timer?.cancel()
            timer = null
            scheduledEnd = -1
        }
        if (wasOpen && !open && foregroundIsLeisure()) kick(LauncherAlert(LauncherAlert.FROZEN_AGAIN, revealLeisure = false))
        wasOpen = open
    }

    private var scheduledEnd = -1L

    private fun schedule(endsAt: Long) {
        timer?.cancel()
        scheduledEnd = endsAt
        timer = scope.launch {
            while (System.currentTimeMillis() < endsAt) delay(endsAt - System.currentTimeMillis())
            repo.tick() // cierra la sesion; el colector de estado hace el resto
        }
    }

    private fun foregroundIsLeisure() = foreground?.let { it in leisurePackages } == true

    private fun kick(alert: LauncherAlert) {
        container.pendingAlert.value = alert
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (BlockerRules.ignored(pkg, packageName, imeSet())) {
            // El launcher si cuenta como "primer plano" (ya no hay ocio delante); sistema y teclados no.
            if (pkg == packageName) foreground = pkg
            return
        }
        foreground = pkg
        if (pkg in leisurePackages && !repo.isOpen()) {
            kick(LauncherAlert(LauncherAlert.FROZEN, revealLeisure = true))
        }
    }

    private fun imeSet(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - imesAt > 30_000 || imes.isEmpty()) {
            imes = runCatching {
                getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
            }.getOrDefault(imes)
            imesAt = now
        }
        return imes
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
