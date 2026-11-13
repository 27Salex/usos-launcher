package com.usos.launcher.service

import android.content.Context
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.usos.launcher.domain.AppEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Paquetes+perfil con notificaciones activas no ongoing y cuántas. Solo lee packageName, usuario y flags, nunca el contenido. */
object NotificationState {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts
    internal fun set(v: Map<String, Int>) { _counts.value = v }

    fun isGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
}

class NotificationWatcher : NotificationListenerService() {
    private fun key(sbn: StatusBarNotification): String {
        val serial = getSystemService(UserManager::class.java).getSerialNumberForUser(sbn.user)
        return AppEntry.configKey(sbn.packageName, serial)
    }

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        // Los resúmenes de grupo no cuentan, salvo que sean lo único que tiene la app.
        val counts = list.filter { !it.isOngoing }.groupBy { key(it) }.mapValues { (_, l) ->
            l.count { it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 }.coerceAtLeast(1)
        }
        NotificationState.set(counts)
    }

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()
    override fun onListenerDisconnected() { NotificationState.set(emptyMap()) }
}
