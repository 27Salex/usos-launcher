package com.usos.launcher.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.usos.launcher.UsosApp
import com.usos.launcher.ui.theme.UsosTheme
import com.usos.launcher.ui.widgets.WidgetAdder

class HomeActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }

    private val container get() = (application as UsosApp).container
    private lateinit var widgetAdder: WidgetAdder
    private var manualCalendarRequest = false

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshInfo()
        // Si el sistema ya no muestra el diálogo (denegado dos veces), la petición manual lleva a los ajustes de la app.
        if (!granted && manualCalendarRequest &&
            !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
        ) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
                )
            }
        }
        manualCalendarRequest = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetAdder = WidgetAdder(this, container, viewModel::addWidget, viewModel::discardWidgetId)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.calendarRequestEvents.collect { manual ->
                    manualCalendarRequest = manual
                    calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshUsage()
                viewModel.refreshInfo()
                var ticks = 0
                while (true) {
                    viewModel.tickLeisure()
                    if (++ticks % 120 == 0) { viewModel.refreshUsage(); viewModel.refreshInfo() } // cada minuto
                    delay(500)
                }
            }
        }
        setContent {
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            UsosTheme(accent) {
                HomeScreen(viewModel, onAddWidget = widgetAdder::start)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.widgetHost.startListening()
    }

    @Deprecated("Resultado del bind y la configuracion de widgets")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetAdder.onActivityResult(requestCode, resultCode)
    }

    override fun onStop() {
        container.widgetHost.stopListening()
        super.onStop()
        viewModel.onLauncherStopped()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }
}
