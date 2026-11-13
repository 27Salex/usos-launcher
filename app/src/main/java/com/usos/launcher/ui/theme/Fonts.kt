package com.usos.launcher.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.usos.launcher.R

/** Familias activas: títulos (secciones y paneles) y texto (todo lo mono). */
data class UsosFonts(val title: FontFamily, val text: FontFamily)

val LocalFonts = compositionLocalOf { UsosFonts(SpaceGrotesk, JetBrainsMono) }

/**
 * Sustituye las familias por defecto de los estilos (Space Grotesk / JetBrains Mono) por las elegidas.
 * Es el único punto donde la fuente elegida llega al texto.
 */
@Composable
fun TextStyle.themed(): TextStyle {
    val f = LocalFonts.current
    return when (fontFamily) {
        SpaceGrotesk -> copy(fontFamily = f.title)
        JetBrainsMono -> copy(fontFamily = f.text)
        else -> this
    }
}

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int) = FontFamily(
    listOf(400, 500, 600, 700).map {
        Font(resId, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    },
)

private val InterFamily = variable(R.font.inter_var)
private val ArchivoFamily = variable(R.font.archivo_var)
private val SyneFamily = variable(R.font.syne_var)
private val DotoFamily = variable(R.font.doto_var)
private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal), Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)
private val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal), Font(R.font.space_mono_bold, FontWeight.Bold),
)
private val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal), Font(R.font.dm_mono_medium, FontWeight.Medium),
)
private val Vt323 = FontFamily(Font(R.font.vt323_regular, FontWeight.Normal))

/** Opciones de fuente; el nombre persistido no cambia nunca. */
enum class FontChoice(@StringRes val labelRes: Int?, val label: String, private val family: FontFamily?) {
    DEFAULT(R.string.font_default, "", null),
    SYSTEM(R.string.font_system, "", null),
    INTER(null, "Inter", InterFamily),
    PLEX_MONO(null, "IBM Plex Mono", PlexMono),
    SPACE_MONO(null, "Space Mono", SpaceMono),
    DM_MONO(null, "DM Mono", DmMono),
    ARCHIVO(null, "Archivo", ArchivoFamily),
    SYNE(null, "Syne", SyneFamily),
    VT323(null, "VT323", Vt323),
    DOTO(null, "Doto", DotoFamily);

    fun family(title: Boolean): FontFamily = when (this) {
        DEFAULT -> if (title) SpaceGrotesk else JetBrainsMono
        SYSTEM -> if (title) FontFamily.Default else FontFamily.Monospace
        else -> family!!
    }

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
