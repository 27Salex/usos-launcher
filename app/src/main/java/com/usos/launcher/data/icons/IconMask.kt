package com.usos.launcher.data.icons

/** Máscara en escala de grises para iconos sin capa monocroma. Pura (opera sobre píxeles ARGB). */
object IconMask {
    private fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /**
     * Devuelve píxeles blancos con alfa = alfa original x luminosidad (o su inversa, [invert], para piezas
     * claras con texto negro), listos para tintar. Null si el resultado queda mal (casi vacío o un bloque macizo).
     */
    fun grayMask(pixels: IntArray, invert: Boolean): IntArray? {
        if (pixels.isEmpty()) return null
        val out = IntArray(pixels.size)
        var sum = 0f
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) / 255f
            val l = luminance(pixels[i])
            val alpha = a * if (invert) 1f - l else l
            sum += alpha
            out[i] = ((alpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        }
        val coverage = sum / pixels.size
        return if (coverage < 0.06f || coverage > 0.85f) null else out
    }
}
