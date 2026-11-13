package com.usos.launcher.service

import android.content.Context
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.usos.launcher.domain.AppEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Paquetes+perfil con notificaciones activas no ongoing. Solo lee packageName y usuario, nunca el contenido. */
object NotificationState {
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys
    internal fun set(v: Set<String>) { _keys.value = v }

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
        NotificationState.set(list.filter { !it.isOngoing }.map { key(it) }.toSet())
    }

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()
    override fun onListenerDisconnected() { NotificationState.set(emptySet()) }
}
