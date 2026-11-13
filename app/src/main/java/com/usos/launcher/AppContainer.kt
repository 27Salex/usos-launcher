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
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import com.usos.launcher.data.suggest.OkHttpSuggestClient
import com.usos.launcher.data.suggest.SuggestClient
import com.usos.launcher.ui.widgets.UsosWidgetHost
import com.usos.launcher.ui.widgets.WidgetStore
import com.usos.launcher.data.usage.UsageRepository
import com.usos.launcher.service.LauncherAlert
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import com.usos.launcher.data.feeds.FeedDatabase
import com.usos.launcher.data.feeds.FeedRepository
import com.usos.launcher.data.feeds.OkHttpFeedFetcher
import com.usos.launcher.data.feeds.ThumbnailLoader
import com.usos.launcher.data.info.AgendaRepository
import okhttp3.OkHttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit

/** Contenedor de inyección manual. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val dataStore = context.usosDataStore
    val appRepository = AppRepository(context)
    val appConfigStore = AppConfigStore(dataStore)
    val settingsStore = SettingsStore(dataStore)
    val displayStore = com.usos.launcher.data.sections.DisplayStore(dataStore)
    val iconRepository by lazy { com.usos.launcher.data.icons.IconRepository(appContext) }
    val usageRepository = UsageRepository(context)
    val suggestClient: SuggestClient by lazy { OkHttpSuggestClient() }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    }
    val feedRepository: FeedRepository by lazy {
        FeedRepository(
            FeedDatabase.create(appContext).dao(), OkHttpFeedFetcher(httpClient),
            keep = { settingsStore.feedsKeep.first() },
        )
    }
    val thumbnailLoader: ThumbnailLoader by lazy { ThumbnailLoader(httpClient) }
    val weatherRepository: com.usos.launcher.data.weather.WeatherRepository by lazy {
        com.usos.launcher.data.weather.WeatherRepository(appContext, settingsStore, httpClient)
    }
    val agendaRepository = AgendaRepository(appContext)
    val updateManager: com.usos.launcher.data.update.UpdateManager by lazy {
        com.usos.launcher.data.update.UpdateManager(appContext, com.usos.launcher.data.update.UpdateStore(dataStore), httpClient)
    }

    /** Host de widgets con id fijo: los ids asignados sobreviven a reinicios. */
    val widgetHost: AppWidgetHost = UsosWidgetHost(appContext, WIDGET_HOST_ID)
    val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val widgetStore = WidgetStore(dataStore)

    /** Aviso pendiente del servicio de bloqueo para el launcher (mismo proceso). */
    val pendingAlert = MutableStateFlow<LauncherAlert?>(null)
    val leisureRepository = LeisureRepository(
        dataStore, Clock.systemDefaultZone(), CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}

const val WIDGET_HOST_ID = 0x5501

class UsosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
