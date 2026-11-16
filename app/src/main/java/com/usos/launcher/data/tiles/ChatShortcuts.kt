package com.usos.launcher.data.tiles

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import java.io.File

/** Chat publicado por una app de mensajería como acceso directo (incluye grupos). */
data class ChatShortcut(val packageName: String, val id: String, val label: String, val info: ShortcutInfo)

/**
 * Chats de WhatsApp/Telegram/etc. a través de los accesos directos que publican (recientes y conversaciones).
 * Solo el launcher predeterminado puede leerlos; los elegidos se fijan (`pinShortcuts`) para que sigan valiendo.
 */
object ChatShortcuts {
    val packages = listOf(TileUris.WHATSAPP, "com.whatsapp.w4b", TileUris.TELEGRAM, "org.thunderdog.challegram", "org.thoughtcrime.securesms")

    private fun launcherApps(context: Context): LauncherApps = context.getSystemService(LauncherApps::class.java)

    /** true si el sistema deja a Usos leer accesos directos de otras apps (solo si es el launcher predeterminado). */
    fun available(context: Context): Boolean =
        runCatching { launcherApps(context).hasShortcutHostPermission() }.getOrDefault(false)

    fun list(context: Context): List<ChatShortcut> {
        val launcherApps = launcherApps(context)
        // Sin ser launcher predeterminado, getShortcuts lanzaría SecurityException: lista vacía.
        if (!available(context)) return emptyList()
        // Qué accesos queremos: los dinámicos (chats recientes que publica la app), los ya fijados por nosotros y
        // los «cacheados» (conversaciones que el sistema conserva aunque la app los haya quitado).
        val queryFlags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
            LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        return packages.flatMap { packageName ->
            // Una consulta por app de mensajería; si no está instalada o falla, no aporta nada.
            val query = LauncherApps.ShortcutQuery().setPackage(packageName).setQueryFlags(queryFlags)
            runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty() }
                .getOrDefault(emptyList())
                // Los desactivados (chat borrado, grupo abandonado) no se pueden abrir.
                .filter { shortcut -> shortcut.isEnabled }
                // Nombre visible: el corto (nombre del chat o grupo) y si no, el largo.
                .map { shortcut ->
                    val label = (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString()
                    ChatShortcut(packageName, shortcut.id, label, shortcut)
                }
        }
            // Un mismo chat puede venir como dinámico y fijado a la vez: se deja una sola vez.
            .distinctBy { chat -> chat.packageName to chat.id }
    }

    /** Fija todos los accesos usados de cada paquete (pinShortcuts sustituye el conjunto fijado del paquete). */
    fun pinAll(context: Context, tiles: List<CustomTile>) {
        if (!available(context)) return
        val byPkg = tiles.flatMap { it.contacts }.filter { it.shortcutId.isNotEmpty() }.groupBy({ it.shortcutPackage }, { it.shortcutId })
        packages.forEach { pkg ->
            runCatching { launcherApps(context).pinShortcuts(pkg, byPkg[pkg].orEmpty().distinct(), Process.myUserHandle()) }
        }
    }

    fun launch(context: Context, contact: TileContact): Boolean = runCatching {
        launcherApps(context).startShortcut(contact.shortcutPackage, contact.shortcutId, null, null, Process.myUserHandle())
    }.isSuccess

    private fun dir(context: Context) = File(context.filesDir, "tile_photos").apply { mkdirs() }

    fun photoFile(context: Context, name: String): File? = name.takeIf { it.isNotEmpty() }?.let { File(dir(context), it) }?.takeIf { it.exists() }

    fun loadPhoto(context: Context, name: String): Bitmap? = photoFile(context, name)?.let { BitmapFactory.decodeFile(it.path) }

    private fun save(context: Context, bmp: Bitmap): String {
        val name = CustomTiles.newId() + ".png"
        File(dir(context), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    fun saveShortcutIcon(context: Context, s: ChatShortcut): String = runCatching {
        val d: Drawable = launcherApps(context).getShortcutIconDrawable(s.info, context.resources.displayMetrics.densityDpi) ?: return ""
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        save(context, bmp)
    }.getOrDefault("")

    fun saveContactPhoto(context: Context, uri: Uri): String = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return ""
        save(context, bmp)
    }.getOrDefault("")

    /** Borra fotos que ya no usa ninguna pieza. */
    fun prunePhotos(context: Context, tiles: List<CustomTile>) {
        val used = tiles.flatMap { it.contacts }.map { it.photo }.toSet()
        dir(context).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }
}
