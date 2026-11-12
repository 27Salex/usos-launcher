package com.usos.launcher.domain

/** App instalada, tal como la devuelve LauncherApps. Solo en memoria. */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
    val label: String,
    val category: Int,
    val versionName: String?,
    val isMainProfile: Boolean = true,
    val isQuiet: Boolean = false,
) {
    /** Clave de configuración: paquete + perfil. */
    val key: String get() = configKey(packageName, userSerial)

    /** Código de dos letras por defecto: las dos primeras letras o dígitos del nombre. */
    val defaultCode: String get() = defaultCodeFor(label)

    companion object {
        fun configKey(packageName: String, userSerial: Long): String = "$packageName/$userSerial"

        fun defaultCodeFor(label: String): String {
            val chars = label.filter { it.isLetterOrDigit() }.take(2)
            return chars.ifEmpty { label.take(2) }.uppercase()
        }
    }
}
