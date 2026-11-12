package com.usos.launcher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import com.usos.launcher.AppContainer

/**
 * Flujo de alta de un widget, atado a la actividad (necesita startActivityForResult):
 * allocateAppWidgetId, bind (directo o con el dialogo del sistema), configuracion y, si se cancela, deleteAppWidgetId.
 * El id pendiente solo vive en memoria; si la actividad muere a mitad, el id queda huerfano y se limpia al arrancar.
 */
class WidgetAdder(
    private val activity: Activity,
    private val container: AppContainer,
    private val onAdded: (Int) -> Unit,
    private val onDiscarded: (Int) -> Unit,
) {
    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    fun start(info: AppWidgetProviderInfo) {
        val id = container.widgetHost.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        if (container.widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureOrFinish()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            runCatching { activity.startActivityForResult(intent, REQ_BIND) }.onFailure { discard() }
        }
    }

    private fun configureOrFinish() {
        val info = pendingInfo ?: return
        if (info.configure == null) {
            finish()
        } else {
            runCatching {
                container.widgetHost.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIG, null)
            }.onFailure { discard() }
        }
    }

    private fun finish() {
        val id = pendingId
        clear()
        if (id >= 0) onAdded(id)
    }

    private fun discard() {
        val id = pendingId
        clear()
        if (id >= 0) onDiscarded(id)
    }

    private fun clear() {
        pendingId = -1
        pendingInfo = null
    }

    /** Devuelve true si el resultado era de este flujo. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIG) return false
        if (pendingId < 0) return true
        if (resultCode != Activity.RESULT_OK) {
            discard()
        } else if (requestCode == REQ_BIND) {
            configureOrFinish()
        } else {
            finish()
        }
        return true
    }

    private companion object {
        const val REQ_BIND = 7101
        const val REQ_CONFIG = 7102
    }
}
