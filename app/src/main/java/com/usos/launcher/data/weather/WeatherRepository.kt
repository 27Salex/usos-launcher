package com.usos.launcher.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.usos.launcher.data.sections.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executor

/** Tiempo de Open-Meteo con cache de 30 min en DataStore. Sin red o sin ubicacion, no hay dato. */
class WeatherRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val http: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = com.usos.launcher.data.PersistJson
    private val mutex = Mutex()
    private var lastAttempt = 0L

    /** Dato cacheado (aunque este caducado); la UI decide si se muestra con [Weather.isVisible]. */
    val cached: Flow<WeatherNow?> = settings.weatherCache.map { raw ->
        raw?.let { runCatching { json.decodeFromString(WeatherNow.serializer(), it) }.getOrNull() }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Refresca si la cache esta caducada (reintenta como mucho cada 2 min). */
    suspend fun refreshIfStale() {
        val prefs = settings.weatherPrefs.first()
        if (!prefs.on) return
        val cur = cached.first()
        val t = now()
        if (cur != null && Weather.isFresh(cur.fetchedAt, t)) return
        if (t - lastAttempt < 2 * 60_000L) return
        if (!mutex.tryLock()) return
        try {
            lastAttempt = now()
            val (lat, lon) = if (prefs.auto) {
                if (!hasLocationPermission()) return
                locate() ?: return
            } else {
                if (!prefs.hasCity) return
                prefs.lat to prefs.lon
            }
            val body = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(Weather.forecastUrl(lat, lon)).build()).execute().use { r ->
                        if (r.isSuccessful) r.body.string() else null
                    }
                }.getOrNull()
            } ?: return
            val w = Weather.parse(body, now()) ?: return
            settings.setWeatherCache(json.encodeToString(WeatherNow.serializer(), w))
        } finally {
            mutex.unlock()
        }
    }

    /** Fuerza una descarga en el siguiente ciclo (cambio de ubicacion, unidades no necesitan red). */
    suspend fun invalidate() {
        settings.setWeatherCache(null)
        lastAttempt = 0
    }

    /** Proveedores por orden de preferencia: red (aproximada), fused si el fabricante lo ofrece, pasivo y GPS. */
    private val providerOrder = listOf(
        LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER,
    )

    @Suppress("MissingPermission")
    private suspend fun locate(): Pair<Double, Double>? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val all = runCatching { lm.allProviders }.getOrDefault(emptyList())
        val usable = providerOrder.filter { it in all }
        if (usable.isEmpty()) return null
        // Una ubicacion conocida de hace menos de 3 h basta para el tiempo.
        val lastKnown = usable.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && now() - lastKnown.time < 3 * 3600_000L) return lastKnown.latitude to lastKnown.longitude
        val enabled = usable.firstOrNull { p ->
            p != LocationManager.PASSIVE_PROVIDER && runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)
        }
        val fresh = if (enabled == null) null else withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                val exec = Executor { it.run() }
                runCatching {
                    lm.getCurrentLocation(enabled, cancel, exec) { loc -> if (cont.isActive) cont.resumeWith(Result.success(loc)) }
                }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
            }
        }
        return (fresh ?: lastKnown)?.let { it.latitude to it.longitude }
    }

    suspend fun searchCities(query: String): List<GeoPlace> {
        if (query.trim().length < 2) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(Weather.geocodeUrl(query)).build()).execute().use { r ->
                    if (r.isSuccessful) Weather.parseGeocoding(r.body.string()) else emptyList()
                }
            }.getOrDefault(emptyList())
        }
    }
}
