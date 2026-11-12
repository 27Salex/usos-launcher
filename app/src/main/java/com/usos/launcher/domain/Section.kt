package com.usos.launcher.domain

enum class Section(val title: String) {
    WORK("Trabajo"),
    TALK("Hablar"),
    MOVE("Moverse"),
    PERSONAL("Personal"),
    LEISURE("Ocio"),
    NONE("Otros");

    companion object {
        /** Secciones que se muestran en el inicio, en orden. */
        val home: List<Section> = listOf(WORK, TALK, MOVE, PERSONAL, LEISURE)
    }
}
