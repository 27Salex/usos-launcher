package com.usos.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.usos.launcher.data.info.InfoPieces

/** Intents de las piezas de información. */
object InfoLauncher {
    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /** App de calendario por defecto; si no hay selector, la vista de hoy por URI. */
    fun openCalendar(context: Context): Boolean {
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        if (start(context, main)) return true
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .appendPath(System.currentTimeMillis().toString()).build()
        return start(context, Intent(Intent.ACTION_VIEW, uri))
    }

    /** Navegación a «casa» con Maps; si no resuelve, geo:0,0?q=casa. */
    fun navigateHome(context: Context): Boolean {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=casa")).setPackage(InfoPieces.MAPS_PACKAGE)
        if (start(context, nav)) return true
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=casa")))
    }
}
