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
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/** Contenedor de inyección manual. */
class AppContainer(context: Context) {
    private val dataStore = context.usosDataStore
    val appRepository = AppRepository(context)
    val appConfigStore = AppConfigStore(dataStore)
    val settingsStore = SettingsStore(dataStore)
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
