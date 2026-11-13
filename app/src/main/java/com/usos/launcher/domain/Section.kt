package com.usos.launcher.domain

import androidx.annotation.StringRes
import com.usos.launcher.R

/** Los nombres persistidos son los del enum; el título visible sale de recursos. */
enum class Section(@StringRes val titleRes: Int) {
    WORK(R.string.section_work),
    TALK(R.string.section_talk),
    MOVE(R.string.section_move),
    PERSONAL(R.string.section_personal),
    LEISURE(R.string.section_leisure),
    NONE(R.string.section_other);

    companion object {
        /** Secciones que se muestran en el inicio, en orden. */
        val home: List<Section> = listOf(WORK, TALK, MOVE, PERSONAL, LEISURE)
    }
}
