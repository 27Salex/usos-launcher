package com.usos.launcher.data.sections

enum class ClockType(val label: String) {
    DOTS("PUNTOS"), DIGITAL("DIGITAL"), MONO("TEXTO"), ANALOG("ANALÓGICO");

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

enum class IconMode(val label: String) {
    NONE("NINGUNO"), MONO("MONOCROMO"), COLOR("COLOR");

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
) {
    fun rowsFor(s: com.usos.launcher.domain.Section): Int = rows[s] ?: MosaicPacker.DEFAULT_ROWS
    fun infoSizeFor(k: com.usos.launcher.data.info.InfoKind): TileSize =
        infoSize[k] ?: com.usos.launcher.data.info.InfoPieces.defaultSize(k)
}
