package com.usos.launcher.domain

import android.content.pm.ApplicationInfo

/**
 * Lista interna de distracciones conocidas (redes sociales de consumo y vídeo corto).
 * Se puede ampliar añadiendo paquetes aquí.
 */
object KnownDistractions {
    val packages: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.barcelona",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.tumblr",
        "com.bereal.ft",
        "tv.twitch.android.app",
        "com.google.android.youtube",
    )
}

/** Clasificador por reglas, en el orden del plan. */
object AppClassifier {
    fun classify(app: AppEntry): Section = when {
        !app.isMainProfile -> Section.WORK
        app.packageName in KnownDistractions.packages -> Section.LEISURE
        app.category == ApplicationInfo.CATEGORY_GAME ||
            app.category == ApplicationInfo.CATEGORY_VIDEO ||
            app.category == ApplicationInfo.CATEGORY_NEWS -> Section.LEISURE
        app.category == ApplicationInfo.CATEGORY_SOCIAL -> Section.TALK
        app.category == ApplicationInfo.CATEGORY_MAPS -> Section.MOVE
        app.category == ApplicationInfo.CATEGORY_AUDIO ||
            app.category == ApplicationInfo.CATEGORY_IMAGE ||
            app.category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> Section.PERSONAL
        else -> Section.NONE
    }
}
