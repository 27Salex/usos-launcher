package com.usos.launcher.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.usos.launcher.R

/** Space Grotesk es una fuente variable: se fija el eje de peso en 600. */
@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    Font(
        resId = R.font.space_grotesk_semibold,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Estilos de texto. El color lo aporta el componente de texto con tokens. */
object UsosType {
    val SectionName = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
    val PanelTitle = SectionName
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.06.em)
    val Date = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.14.em)
    val SectionData = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.08.em)
    val WeekLetter = Label
    val WeekNumber = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp)
    val TileCode = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 22.sp)
    val TileName = Label
    val SearchHint = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, letterSpacing = 0.1.em)
}
