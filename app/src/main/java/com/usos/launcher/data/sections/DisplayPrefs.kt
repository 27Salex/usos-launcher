package com.usos.launcher.data.sections

import androidx.annotation.StringRes
import com.usos.launcher.R

enum class ClockType(@StringRes val labelRes: Int) {
    DOTS(R.string.clock_type_dots), DIGITAL(R.string.clock_type_digital), MONO(R.string.clock_type_mono), ANALOG(R.string.clock_type_analog);

    companion object {
        fun fromName(name: String?): ClockType = entries.firstOrNull { it.name == name } ?: DOTS
    }
}

data class ClockPrefs(
    val type: ClockType = ClockType.DOTS,
    val seconds: Boolean = false,
    val animated: Boolean = true,
)

data class WeatherPrefs(
    val on: Boolean = false,
    val fahrenheit: Boolean = false,
    val icon: Boolean = true,
    val text: Boolean = true,
    val minMax: Boolean = false,
    /** true = ubicacion aproximada del dispositivo; false = ciudad elegida. */
    val auto: Boolean = true,
    val cityName: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val belowClock: Boolean = false,
    val accentColor: Boolean = false,
) {
    val hasCity: Boolean get() = cityName.isNotEmpty()
}

enum class IconMode(@StringRes val labelRes: Int) {
    NONE(R.string.icon_mode_none), MONO(R.string.icon_mode_mono), COLOR(R.string.icon_mode_color);

    companion object {
        fun fromName(name: String?): IconMode = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Iconos de app en las piezas: modo global y si el código se muestra junto al icono. */
data class IconPrefs(val mode: IconMode = IconMode.NONE, val showCode: Boolean = true)

/** Mosaico: columnas globales, filas por sección y piezas de información (tamaño y ocultas). */
data class MosaicPrefs(
    val columns: Int = 3,
    val rows: Map<com.usos.launcher.domain.Section, Int> = emptyMap(),
    val infoSize: Map<com.usos.launcher.data.info.InfoKind, TileSize> = emptyMap(),
    val infoHidden: Set<com.usos.launcher.data.info.InfoKind> = emptySet(),
    /** Orden de las pestañas del inicio y secciones ocultas (Ajustes > Inicio > Secciones). */
    val sectionOrder: List<com.usos.launcher.domain.Section> = com.usos.launcher.domain.Section.home,
    val hiddenSections: Set<com.usos.launcher.domain.Section> = emptySet(),
    /** Inicio por pestañas (opcional, apagado: por defecto el acordeón de siempre). */
    val tabs: Boolean = false,
    /** Mini reproductor en piezas de apps de música de 2x1 o más (opcional, apagado). */
    val musicPlayer: Boolean = false,
    /** Pestañas por sección en el cajón de apps (búsqueda sin texto). */
    val drawerTabs: Boolean = false,
) {
    /** Pestañas visibles en orden; nunca vacía (si se ocultan todas se ve la primera). */
    val visibleSections: List<com.usos.launcher.domain.Section>
        get() = sectionOrder.filter { it !in hiddenSections }.ifEmpty { sectionOrder.take(1) }

    fun rowsFor(s: com.usos.launcher.domain.Section): Int = rows[s] ?: MosaicPacker.DEFAULT_ROWS
    fun infoSizeFor(k: com.usos.launcher.data.info.InfoKind): TileSize =
        infoSize[k] ?: com.usos.launcher.data.info.InfoPieces.defaultSize(k)
}

/**
 * Dock de apps fijas encima de BUSCAR. [apps] guarda la clave de cada hueco ("" = vacío); se conservan las que
 * quedan fuera al reducir [slots], así subir el número de huecos las recupera.
 */
data class DockPrefs(val on: Boolean = false, val slots: Int = DEFAULT_SLOTS, val apps: List<String> = emptyList()) {
    fun at(i: Int): String? = apps.getOrNull(i)?.takeIf { it.isNotEmpty() }

    /** Pone [key] en el hueco [i] (null = vaciarlo); la misma app no se repite en otro hueco. */
    fun with(i: Int, key: String?): List<String> {
        val l = MutableList(maxOf(apps.size, i + 1)) { apps.getOrNull(it).orEmpty() }
        if (key != null) l.indices.forEach { if (l[it] == key) l[it] = "" }
        l[i] = key.orEmpty()
        return l.dropLastWhile { it.isEmpty() }
    }

    companion object {
        const val MIN_SLOTS = 3
        const val MAX_SLOTS = 5
        const val DEFAULT_SLOTS = 4
        fun decode(raw: String?): List<String> = raw?.takeIf { it.isNotEmpty() }?.split('\n') ?: emptyList()
        fun encode(l: List<String>): String = l.joinToString("\n")
    }
}

/** Modo mesita (reloj horizontal a pantalla completa). Se abre solo a mano. */
enum class NightClock(@StringRes val labelRes: Int) {
    FLIP(R.string.night_clock_flip), DIGITAL(R.string.clock_type_digital), DOTS(R.string.clock_type_dots);

    companion object {
        fun fromName(name: String?): NightClock = entries.firstOrNull { it.name == name } ?: FLIP
    }
}

data class NightstandPrefs(
    val clock: NightClock = NightClock.FLIP,
    val calendar: Boolean = true,
    val seconds: Boolean = false,
    val dim: Boolean = true,
)

/** Codificación del orden y la ocultación de secciones (nombres del enum separados por comas). Pura. */
object SectionTabs {
    private val home = com.usos.launcher.domain.Section.home

    /** Orden guardado: nombres válidos sin repetir y, al final, las que falten en su orden por defecto. */
    fun decodeOrder(raw: String?): List<com.usos.launcher.domain.Section> {
        val saved = raw.orEmpty().split(',').mapNotNull { n -> home.firstOrNull { it.name == n.trim() } }.distinct()
        return saved + home.filter { it !in saved }
    }

    fun decodeHidden(raw: String?): Set<com.usos.launcher.domain.Section> =
        raw.orEmpty().split(',').mapNotNull { n -> home.firstOrNull { it.name == n.trim() } }.toSet()

    fun encode(list: Collection<com.usos.launcher.domain.Section>): String = list.joinToString(",") { it.name }

    /** Mueve [s] [delta] posiciones. */
    fun move(order: List<com.usos.launcher.domain.Section>, s: com.usos.launcher.domain.Section, delta: Int): List<com.usos.launcher.domain.Section> {
        val from = order.indexOf(s)
        if (from < 0) return order
        val to = (from + delta).coerceIn(0, order.lastIndex)
        return order.toMutableList().apply { removeAt(from); add(to, s) }
    }
}
