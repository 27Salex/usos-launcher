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

    /** Alfa mínimo (0..255) para contar un píxel como opaco. */
    private const val OPAQUE_ALPHA = 200

    /** Parte mínima del lienzo que debe ser opaca para considerar que el icono es un plato macizo. */
    private const val PLATE_MIN_OPAQUE_FRACTION = 0.5f

    /** Parte mínima de los píxeles opacos que debe tener el color dominante. */
    private const val PLATE_MIN_DOMINANT_FRACTION = 0.35f

    /** Bits que se conservan de cada canal al agrupar colores parecidos (4 bits = 16 tonos por canal). */
    private const val BITS_PER_CHANNEL = 4

    /** Número de grupos de color posibles: 16 x 16 x 16. */
    private const val COLOR_GROUP_COUNT = 1 shl (BITS_PER_CHANNEL * 3)

    /**
     * Agrupa un color ARGB con los parecidos: se queda con los 4 bits altos de rojo, verde y azul y los junta en
     * un índice 0..4095 (así, dos naranjas casi iguales caen en el mismo grupo).
     */
    private fun colorGroupOf(argb: Int): Int {
        val red = (argb shr 20) and 0xF   // 4 bits altos del canal rojo (bits 20..23)
        val green = (argb shr 12) and 0xF // 4 bits altos del canal verde (bits 12..15)
        val blue = (argb shr 4) and 0xF   // 4 bits altos del canal azul (bits 4..7)
        return (red shl (BITS_PER_CHANNEL * 2)) or (green shl BITS_PER_CHANNEL) or blue
    }

    private fun isOpaque(argb: Int): Boolean = (argb ushr 24) >= OPAQUE_ALPHA

    /**
     * Color del «plato» cuando el icono es un cuadro o círculo macizo de un color con el logo encima. Se cumple si
     * más de la mitad del lienzo es opaco y un mismo color (agrupando los parecidos) ocupa al menos el 35 % de lo
     * opaco. Devuelve la media exacta de los píxeles de ese grupo, o null si el icono no es un plato macizo.
     */
    fun plateColor(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null

        // 1. Histograma: cuántos píxeles opacos caen en cada grupo de color.
        val pixelsPerGroup = IntArray(COLOR_GROUP_COUNT)
        var opaqueCount = 0
        for (pixel in pixels) {
            if (!isOpaque(pixel)) continue
            opaqueCount++
            pixelsPerGroup[colorGroupOf(pixel)]++
        }

        // 2. Si casi todo es transparente, el icono es un logo suelto, no un plato.
        if (opaqueCount < pixels.size * PLATE_MIN_OPAQUE_FRACTION) return null

        // 3. El grupo más frecuente es el candidato a plato; debe dominar de verdad.
        val dominantGroup = pixelsPerGroup.indices.maxBy { pixelsPerGroup[it] }
        if (pixelsPerGroup[dominantGroup] < opaqueCount * PLATE_MIN_DOMINANT_FRACTION) return null

        // 4. Media real (sin agrupar) de los píxeles de ese grupo, para no perder precisión del color.
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0
        for (pixel in pixels) {
            if (!isOpaque(pixel) || colorGroupOf(pixel) != dominantGroup) continue
            redSum += (pixel shr 16) and 0xFF
            greenSum += (pixel shr 8) and 0xFF
            blueSum += pixel and 0xFF
            count++
        }
        val red = (redSum / count).toInt()
        val green = (greenSum / count).toInt()
        val blue = (blueSum / count).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
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
