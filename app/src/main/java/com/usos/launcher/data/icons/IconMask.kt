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

    /** Color medio de los píxeles opacos del borde (anillo exterior del 10 %): el «plato» del icono. Null si no hay. */
    fun borderColor(pixels: IntArray, size: Int): Int? {
        if (size <= 0 || pixels.size < size * size) return null
        val ring = (size * 0.10f).toInt().coerceAtLeast(1)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (y in 0 until size) for (x in 0 until size) {
            if (x >= ring && x < size - ring && y >= ring && y < size - ring) continue
            val p = pixels[y * size + x]
            if ((p ushr 24) < 200) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        // Un plato real ocupa buena parte del borde; si casi todo es transparente, no hay fondo.
        if (n < ring * size) return null
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /** Color medio de los píxeles opacos (fondo de un AdaptiveIconDrawable). Null si no hay ninguno. */
    fun averageColor(pixels: IntArray): Int? {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        if (n == 0) return null
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /** Distancia de color 0..1 (RGB ponderado, aproxima la percepción). */
    fun colorDistance(a: Int, b: Int): Float {
        val dr = (((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) / 255f
        val dg = (((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) / 255f
        val db = ((a and 0xFF) - (b and 0xFF)) / 255f
        return kotlin.math.sqrt((2f * dr * dr + 4f * dg * dg + 3f * db * db) / 9f)
    }

    /**
     * Silueta por contraste con el fondo [bg]: lo que se distingue del plato (logo) queda opaco y el plato
     * desaparece, sea el logo claro u oscuro. Sin [bg] (icono sin plato) se usa el alfa tal cual. Null si queda
     * casi vacío o macizo.
     */
    fun contrastMask(pixels: IntArray, bg: Int?): IntArray? {
        if (pixels.isEmpty()) return null
        val out = IntArray(pixels.size)
        var sum = 0f
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) / 255f
            val k = if (bg == null) 1f else ((colorDistance(pixels[i], bg) - 0.08f) / 0.22f).coerceIn(0f, 1f)
            val alpha = a * k
            sum += alpha
            out[i] = ((alpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        }
        val coverage = sum / pixels.size
        return if (coverage < 0.03f || coverage > 0.80f) null else out
    }
}
