package com.usos.launcher.data.tiles

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import java.net.URLEncoder

/** Lógica pura (strings) de los intents de las piezas propias; se prueba en la JVM. */
object TileUris {
    const val MAPS = "com.google.android.apps.maps"
    const val WAZE = "com.waze"
    const val WHATSAPP = "com.whatsapp"
    const val TELEGRAM = "org.telegram.messenger"

    fun enc(s: String): String = URLEncoder.encode(s.trim(), "UTF-8")

    /** Solo dígitos y, opcionalmente, un «+» inicial. */
    fun normalizePhone(raw: String, keepPlus: Boolean = true): String {
        val t = raw.trim()
        val digits = t.filter { it.isDigit() }
        return if (keepPlus && t.startsWith("+")) "+$digits" else digits
    }

    fun mapsNav(address: String) = "google.navigation:q=" + enc(address)
    fun wazeNav(address: String) = "https://waze.com/ul?q=" + enc(address) + "&navigate=yes"
    fun geo(address: String) = "geo:0,0?q=" + enc(address)
    fun whatsapp(phone: String) = "https://wa.me/" + normalizePhone(phone, keepPlus = false)
    fun telegram(phone: String) = "https://t.me/+" + normalizePhone(phone, keepPlus = false)
    fun sms(phone: String) = "smsto:" + normalizePhone(phone)
    fun tel(phone: String) = "tel:" + normalizePhone(phone)

    /** URI de destino según la app de navegación elegida. */
    fun destination(address: String, appPackage: String): String = when (appPackage) {
        MAPS -> mapsNav(address)
        WAZE -> wazeNav(address)
        else -> geo(address)
    }
}

/** Apps de navegación conocidas (paquete -> nombre). */
object NavApps {
    val known: List<Pair<String, String>> = listOf(
        TileUris.MAPS to "Google Maps",
        TileUris.WAZE to "Waze",
        "com.citymapper.app.release" to "Citymapper",
        "com.here.app.maps" to "HERE WeGo",
        "com.sygic.aura" to "Sygic",
        "com.generalmagic.magicearth" to "Magic Earth",
    )

    /** Las conocidas que están instaladas (en el orden de [known]). */
    fun installed(installedPackages: Collection<String>): List<Pair<String, String>> {
        val set = installedPackages.toSet()
        return known.filter { it.first in set }
    }

    fun labelOf(pkg: String): String = known.firstOrNull { it.first == pkg }?.second ?: pkg
}

object MusicApps {
    val packages = setOf(
        "com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music", "deezer.android.app",
        "com.soundcloud.android", "com.amazon.mp3", "com.aspiro.tidal", "com.sec.android.app.music",
    )

    fun isMusic(packageName: String, category: Int): Boolean =
        packageName in packages || category == ApplicationInfo.CATEGORY_AUDIO
}

/** Construcción y lanzamiento de intents. */
object TileIntents {
    private fun view(uri: String, pkg: String? = null): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { if (!pkg.isNullOrEmpty()) it.setPackage(pkg) }

    fun destination(address: String, appPackage: String): Intent =
        view(TileUris.destination(address, appPackage), appPackage.ifEmpty { null })

    fun destinationFallback(address: String): Intent = view(TileUris.geo(address))

    fun contact(c: TileContact): Intent = when (c.app) {
        ContactApp.WHATSAPP -> view(TileUris.whatsapp(c.phone), TileUris.WHATSAPP)
        ContactApp.TELEGRAM -> view(TileUris.telegram(c.phone), TileUris.TELEGRAM)
        ContactApp.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse(TileUris.sms(c.phone))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ContactApp.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse(TileUris.tel(c.phone))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun contactFallback(c: TileContact): Intent? = when (c.app) {
        ContactApp.WHATSAPP -> view(TileUris.whatsapp(c.phone))
        ContactApp.TELEGRAM -> view(TileUris.telegram(c.phone))
        else -> null
    }

    private fun tryStart(context: Context, intent: Intent?): Boolean =
        intent != null && runCatching { context.startActivity(intent) }.isSuccess

    /** Abre la pieza: destino o el contacto [contactIndex]; si la app no responde, reintenta sin paquete. */
    fun launch(context: Context, tile: CustomTile, contactIndex: Int = 0) {
        when (tile.kind) {
            TileKind.DEST -> {
                if (tile.address.isBlank()) return
                if (!tryStart(context, destination(tile.address, tile.appPackage))) tryStart(context, destinationFallback(tile.address))
            }
            TileKind.CONTACTS -> {
                val tileContact = tile.contacts.getOrNull(contactIndex) ?: return
                // Chat elegido de la app (grupos incluidos): se abre su acceso directo; si ya no existe, por teléfono.
                if (tileContact.shortcutId.isNotEmpty() && ChatShortcuts.launch(context, tileContact)) return
                if (tileContact.phone.isBlank()) return
                if (!tryStart(context, contact(tileContact))) tryStart(context, contactFallback(tileContact))
            }
        }
    }
}
