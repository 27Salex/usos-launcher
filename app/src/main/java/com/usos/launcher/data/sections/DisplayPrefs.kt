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
