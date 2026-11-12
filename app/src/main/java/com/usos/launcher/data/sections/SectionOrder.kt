package com.usos.launcher.data.sections

/** Reordenado de apps dentro de una seccion (subir/bajar). Funcion pura. */
object SectionOrder {
    /**
     * Mueve [key] [delta] posiciones dentro de [orderedKeys] (ya ordenadas) y devuelve el nuevo
     * `order` (0, 1, 2...) de cada clave, o null si no hay cambio posible.
     */
    fun move(orderedKeys: List<String>, key: String, delta: Int): Map<String, Int>? {
        val from = orderedKeys.indexOf(key)
        if (from < 0) return null
        val to = (from + delta).coerceIn(0, orderedKeys.lastIndex)
        if (to == from) return null
        val list = orderedKeys.toMutableList()
        list.removeAt(from)
        list.add(to, key)
        return list.withIndex().associate { (i, k) -> k to i }
    }
}
