package com.usos.launcher.data.notify

/** Aviso de notificación en las piezas. Los nombres persistidos no cambian. */
enum class NotifMode {
    NONE, DOT, BORDER;

    companion object {
        fun fromName(n: String?) = entries.firstOrNull { it.name == n } ?: NONE
    }
}

/** Qué piezas llevan aviso. Función pura: sin números, solo sí/no por app+perfil (clave `paquete/serial`). */
object NotifBadge {
    fun show(mode: NotifMode, active: Set<String>, appKey: String, frozen: Boolean): Boolean =
        mode != NotifMode.NONE && !frozen && appKey in active
}
