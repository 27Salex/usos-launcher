package com.usos.launcher

import android.app.Application
import android.content.Context
import com.usos.launcher.data.apps.AppRepository
import com.usos.launcher.data.leisure.LeisureRepository
import com.usos.launcher.data.sections.AppConfigStore
import com.usos.launcher.data.sections.SettingsStore
import com.usos.launcher.data.sections.usosDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.usos.launcher.data.usage.UsageRepository
import com.usos.launcher.service.LauncherAlert
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock

/** Contenedor de inyección manual. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val dataStore = context.usosDataStore
    val appRepository = AppRepository(context)
    val appConfigStore = AppConfigStore(dataStore)
    val settingsStore = SettingsStore(dataStore)
    val usageRepository = UsageRepository(context)

    /** Aviso pendiente del servicio de bloqueo para el launcher (mismo proceso). */
    val pendingAlert = MutableStateFlow<LauncherAlert?>(null)
    val leisureRepository = LeisureRepository(
        dataStore, Clock.systemDefaultZone(), CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}

class UsosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
