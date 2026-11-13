package com.usos.launcher.data.feeds

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object FeedLimits {
    const val MIN_PER_HOUR = 3
    const val MAX_PER_HOUR = 10
    const val DEFAULT_PER_HOUR = 5

    fun clamp(n: Int): Int = n.coerceIn(MIN_PER_HOUR, MAX_PER_HOUR)

    /** Un feed se vuelve a descargar al abrir el panel o filtrarlo si lleva más de 10 min sin actualizar. */
    const val REFRESH_AFTER_MS = 10 * 60 * 1000L

    /** Artículos que se conservan por feed (más los guardados): opciones de Ajustes y valor por defecto. */
    val KEEP_CHOICES = listOf(10, 20, 30, 50)
    const val DEFAULT_KEEP = 20

    fun clampKeep(n: Int): Int = if (n in KEEP_CHOICES) n else DEFAULT_KEEP
}

/** Cubo de lecturas: [count] enteras y [refilledAt] = inicio del tramo en curso hacia la siguiente. */
data class TokenState(val count: Int, val refilledAt: Long)

/**
 * Cubo de lecturas con relleno continuo. Lógica pura: el reloj entra como parámetro.
 * Máximo = lecturas por hora (3..10); se gana 1 lectura cada 60/N minutos. Abrir un artículo no leído gasta 1.
 */
object ReadTokens {
    fun max(perHour: Int): Int = FeedLimits.clamp(perHour)
    fun intervalMs(perHour: Int): Long = 3_600_000L / max(perHour)

    /** Estado al día: aplica el relleno acumulado y el nuevo máximo. null = cubo lleno. */
    fun normalize(state: TokenState?, now: Long, perHour: Int): TokenState {
        val max = max(perHour)
        if (state == null) return TokenState(max, now)
        val count = state.count.coerceIn(0, max)
        if (count >= max) return TokenState(max, now)
        val since = minOf(state.refilledAt, now) // un reloj que retrocede no bloquea el relleno
        val interval = intervalMs(perHour)
        val earned = ((now - since) / interval).toInt()
        val total = count + earned
        return if (total >= max) TokenState(max, now) else TokenState(total, since + earned * interval)
    }

    /** Gasta una lectura; null si no queda ninguna. */
    fun spend(state: TokenState?, now: Long, perHour: Int): TokenState? {
        val n = normalize(state, now, perHour)
        if (n.count == 0) return null
        // Con el cubo lleno el relleno arranca ahora; si no, sigue el tramo en curso.
        return TokenState(n.count - 1, if (n.count == max(perHour)) now else n.refilledAt)
    }

    /** Momento en que llega la próxima lectura; null si el cubo está lleno. */
    fun nextAt(state: TokenState, perHour: Int): Long? =
        if (state.count >= max(perHour)) null else state.refilledAt + intervalMs(perHour)

    /** «+1 A LAS 19:05»; null con el cubo lleno. */
    fun nextLabel(state: TokenState, perHour: Int, zone: ZoneId): String? {
        val at = nextAt(state, perHour) ?: return null
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
        return "+1 A LAS %02d:%02d".format(t.hour, t.minute)
    }
}
