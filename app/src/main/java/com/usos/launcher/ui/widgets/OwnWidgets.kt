package com.usos.launcher.ui.widgets

/** Tipo de widget colocado: ANDROID (AppWidgetHost) o pieza propia de Usos (Compose). */
enum class WidgetKind {
    ANDROID, USOS_MUSIC, USOS_CALENDAR, USOS_CLOCK;

    val isOwn get() = this != ANDROID

    companion object {
        fun fromName(name: String?): WidgetKind = entries.firstOrNull { it.name == name } ?: ANDROID
    }
}

/** Lógica pura de los widgets propios: ids, tamaños y ajustes. */
object OwnWidgets {
    /** Los propios usan ids negativos: no chocan con los de AppWidgetHost (>= 0) ni con los ya usados. */
    fun nextId(existing: Collection<Int>): Int = minOf(existing.minOrNull() ?: 0, 0) - 1

    /** Celdas mínimas (ancho, alto) de cada pieza. */
    fun minCells(kind: WidgetKind): Pair<Int, Int> = 2 to 1

    /** Tamaño inicial: ancho completo; [tall] es la variante grande del calendario (mes). */
    fun defaultCells(kind: WidgetKind, columns: Int, tall: Boolean = false): Pair<Int, Int> = when (kind) {
        WidgetKind.USOS_CALENDAR -> columns to (if (tall) 3 else 2)
        else -> columns to 1
    }

    /** Si la pieza tiene ajustes propios (el botón de configurar de la edición). */
    fun configurable(kind: WidgetKind) = kind != WidgetKind.ANDROID

    fun parse(config: String): Map<String, String> =
        config.split(';').mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()

    fun get(config: String, key: String): String? = parse(config)[key]

    fun with(config: String, key: String, value: String?): String {
        val m = LinkedHashMap(parse(config))
        if (value.isNullOrEmpty()) m.remove(key) else m[key] = value
        return m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    const val KEY_BARS = "bars"   // música: "accent" o vacío (blancas)
    const val KEY_TZ = "tz"       // reloj: id de zona o vacío (la del sistema)
    const val KEY_MODE = "mode"   // calendario: week | month | agenda; vacío = automático por alto
    const val KEY_BORDER = "border" // todas: "off" quita el borde blanco; vacío = con borde

    fun bordered(config: String): Boolean = get(config, KEY_BORDER) != "off"
}
