package com.usos.launcher.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.usos.launcher.R
import java.util.Locale

/**
 * Idioma de la app. La lista de idiomas es res/xml/locales_config.xml (único sitio a tocar al añadir uno);
 * la elección la guarda el sistema (LocaleManager, API 33) y también se ve en Ajustes > Idioma de la app.
 */
object AppLocales {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** Etiquetas de idioma de locales_config.xml, en orden («es», «en»). */
    fun supportedTags(context: Context): List<String> {
        val tags = mutableListOf<String>()
        context.resources.getXml(R.xml.locales_config).use { p ->
            while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (p.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && p.name == "locale") {
                    p.getAttributeValue(ANDROID_NS, "name")?.let { tags += it }
                }
            }
        }
        return tags
    }

    /** Nombre del idioma en el propio idioma («Español», «English»). */
    fun displayName(tag: String): String {
        val l = Locale.forLanguageTag(tag)
        return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }

    /** Idioma elegido para la app entre [supportedTags]; null = el del sistema. */
    fun currentTag(context: Context): String? {
        val chosen = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (chosen.isEmpty) return null
        val lang = chosen[0].language
        return supportedTags(context).firstOrNull { Locale.forLanguageTag(it).language == lang }
    }

    /** Cambia el idioma de la app (null = seguir al sistema); Android recrea la actividad. */
    fun set(context: Context, tag: String?) {
        val list = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        context.getSystemService(LocaleManager::class.java).applicationLocales = list
    }
}
