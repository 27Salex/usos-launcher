package com.usos.launcher.data.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import com.usos.launcher.domain.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** Apps instaladas de todos los perfiles, en vivo, y lanzamiento. */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    private val changes: Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(
                packageNames: Array<out String>, user: UserHandle, replacing: Boolean,
            ) { trySend(Unit) }
            override fun onPackagesUnavailable(
                packageNames: Array<out String>, user: UserHandle, replacing: Boolean,
            ) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { trySend(Unit) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            appContext.unregisterReceiver(receiver)
        }
    }

    /** Lista actual de apps; se vuelve a emitir con cada alta, baja, cambio o pausa de perfil. */
    val apps: Flow<List<AppEntry>> = changes
        .onStart { emit(Unit) }
        .conflate()
        .map { withContext(Dispatchers.Default) { loadApps() } }

    private fun loadApps(): List<AppEntry> {
        val main = Process.myUserHandle()
        val pm = appContext.packageManager
        val result = ArrayList<AppEntry>()
        for (profile in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(profile)
            val isMain = profile == main
            val quiet = userManager.isQuietModeEnabled(profile)
            for (info in launcherApps.getActivityList(null, profile)) {
                val pkg = info.componentName.packageName
                val version = if (isMain) {
                    runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()
                } else {
                    null
                }
                result += AppEntry(
                    packageName = pkg,
                    activityName = info.componentName.className,
                    userSerial = serial,
                    label = info.label.toString(),
                    category = info.applicationInfo.category,
                    versionName = version,
                    isMainProfile = isMain,
                    isQuiet = quiet,
                )
            }
        }
        return result.distinctBy { it.key }
    }

    /** Abre la app. Devuelve false si el perfil está en pausa o el lanzamiento falla. */
    fun launch(app: AppEntry): Boolean {
        if (app.isQuiet) return false
        val user = userManager.getUserForSerialNumber(app.userSerial) ?: return false
        return runCatching {
            launcherApps.startMainActivity(ComponentName(app.packageName, app.activityName), user, null, null)
        }.isSuccess
    }

    /** Abre la URL (ACTION_VIEW). Con [pkg] la dirige a esa app y, si no resuelve el enlace, la abre sin paquete (navegador). */
    fun openUrl(url: String, pkg: String? = null): Boolean {
        fun go(p: String?) = runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply { p?.let { setPackage(it) } }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        return (pkg != null && go(pkg)) || go(null)
    }

    /** Copia [text] al portapapeles. */
    fun copyText(text: String) {
        val cm = appContext.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("pregunta", text))
    }

    /** Abre la app de reloj del sistema (lista de alarmas) o, si no se resuelve, una de las conocidas. */
    fun openSystemClock(): Boolean {
        val viaAction = runCatching {
            appContext.startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (viaAction) return true
        val pm = appContext.packageManager
        val intent = CLOCK_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) } ?: return false
        return runCatching { appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private companion object {
        val CLOCK_PACKAGES = listOf(
            "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
            "com.miui.clock", "com.coloros.alarmclock", "com.oneplus.deskclock",
        )
    }

    /** Busca el texto en el buscador elegido, abierto en el navegador por defecto (no ACTION_WEB_SEARCH). */
    fun webSearch(text: String, engine: com.usos.launcher.data.suggest.SearchEngine): Boolean = openUrl(engine.urlFor(text))
}
