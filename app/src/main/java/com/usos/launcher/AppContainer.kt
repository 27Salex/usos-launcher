package com.usos.launcher

import android.app.Application
import android.content.Context
import com.usos.launcher.data.apps.AppRepository
import com.usos.launcher.data.sections.AppConfigStore
import com.usos.launcher.data.sections.SettingsStore
import com.usos.launcher.data.sections.usosDataStore

/** Contenedor de inyección manual. */
class AppContainer(context: Context) {
    private val dataStore = context.usosDataStore
    val appRepository = AppRepository(context)
    val appConfigStore = AppConfigStore(dataStore)
    val settingsStore = SettingsStore(dataStore)
}

class UsosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
