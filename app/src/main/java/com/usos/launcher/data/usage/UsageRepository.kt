package com.usos.launcher.data.usage

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * Uso y almacenamiento por app. El tiempo de primer plano sale de `queryEvents` (no de
 * `queryUsageStats`) para coincidir con Bienestar digital. Solo ve el perfil principal.
 */
class UsageRepository(context: Context, private val clock: Clock = Clock.systemDefaultZone()) {
    private val appContext = context.applicationContext
    private val usm = appContext.getSystemService(UsageStatsManager::class.java)
    private val storage = appContext.getSystemService(StorageStatsManager::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    fun hasPermission(): Boolean = hasUsageAccess(appContext)

    /** Uso por paquete y dia desde la medianoche de hace [days]-1 dias hasta ahora. */
    suspend fun load(days: Int): Map<String, Map<LocalDate, DayUsage>> = withContext(Dispatchers.Default) {
        val zone = clock.zone
        val now = clock.millis()
        val start = LocalDate.now(clock).minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val records = ArrayList<UsageEventRecord>()
        val events = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventKind.PAUSED
                UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventKind.SHUTDOWN
                else -> continue
            }
            records += UsageEventRecord(e.packageName.orEmpty(), e.className.orEmpty(), kind, e.timeStamp)
        }
        UsageAggregator.aggregate(records, start, now, zone)
    }

    /** Minutos de hoy por paquete. */
    suspend fun todayMinutes(): Map<String, Int> {
        val today = LocalDate.now(clock)
        return load(1).mapValues { (_, d) -> ((d[today]?.foregroundMs ?: 0L) / 60_000).toInt() }
    }

    suspend fun summary(packageName: String): UsageSummary {
        val days = load(UsageSummary.DAYS)[packageName].orEmpty()
        return UsageSummary.build(days, LocalDate.now(clock))
    }

    /** Bytes ocupados por la app (codigo + datos + cache); null si no se pueden leer. */
    suspend fun storageBytes(packageName: String, userSerial: Long): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val user = userManager.getUserForSerialNumber(userSerial) ?: Process.myUserHandle()
            val s = storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, user)
            s.appBytes + s.dataBytes + s.cacheBytes
        }.getOrNull()
    }
}

fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    return ops.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
    ) == AppOpsManager.MODE_ALLOWED
}
