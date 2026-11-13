package com.usos.launcher.data.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import com.usos.launcher.data.sections.IconMode
import com.usos.launcher.domain.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Icono listo para dibujar. [bitmap] null = usar el código de 2 letras. [tint] = tintar con el color del texto. */
class LoadedIcon(val bitmap: Bitmap?, val tint: Boolean)

/**
 * Iconos de app vía LauncherApps (respeta el perfil de trabajo). Caché LRU en memoria; la carga va en
 * Dispatchers.IO. Se invalida con LauncherApps.Callback (paquete cambiado, añadido o eliminado).
 */
class IconRepository(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val density = appContext.resources.displayMetrics.densityDpi

    private val cache = object : LruCache<String, LoadedIcon>(6 * 1024) {
        override fun sizeOf(key: String, value: LoadedIcon): Int = ((value.bitmap?.byteCount ?: 0) / 1024).coerceAtLeast(1)
    }

    private val _version = MutableStateFlow(0)

    /** Sube con cada invalidación para que la UI recargue. */
    val version: StateFlow<Int> = _version

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageChanged(packageName: String, user: UserHandle) = invalidate(packageName)
            override fun onPackageAdded(packageName: String, user: UserHandle) = invalidate(packageName)
            override fun onPackageRemoved(packageName: String, user: UserHandle) = invalidate(packageName)
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                packageNames.forEach { invalidate(it) }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
        })
    }

    private fun invalidate(packageName: String) {
        synchronized(cache) {
            cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { cache.remove(it) }
        }
        _version.value++
    }

    private fun cacheKey(app: AppEntry, mode: IconMode, invert: Boolean, px: Int) =
        app.packageName + "/" + app.userSerial + "|" + mode + "|" + (if (invert) 1 else 0) + "|" + px

    /** Solo mira la caché (barato, hilo principal). */
    fun peek(app: AppEntry, mode: IconMode, invert: Boolean, px: Int): LoadedIcon? =
        synchronized(cache) { cache.get(cacheKey(app, mode, invert, px)) }

    suspend fun load(app: AppEntry, mode: IconMode, invert: Boolean, px: Int): LoadedIcon {
        peek(app, mode, invert, px)?.let { return it }
        val icon = withContext(Dispatchers.IO) {
            runCatching { build(app, mode, invert, px) }.getOrDefault(LoadedIcon(null, false))
        }
        synchronized(cache) { cache.put(cacheKey(app, mode, invert, px), icon) }
        return icon
    }

    private fun build(app: AppEntry, mode: IconMode, invert: Boolean, px: Int): LoadedIcon {
        val user = userManager.getUserForSerialNumber(app.userSerial) ?: return LoadedIcon(null, false)
        val infos = launcherApps.getActivityList(app.packageName, user)
        val info = infos.firstOrNull { it.componentName.className == app.activityName } ?: infos.firstOrNull()
            ?: return LoadedIcon(null, false)
        return when (mode) {
            IconMode.NONE -> LoadedIcon(null, false)
            // La insignia del perfil de trabajo solo en color.
            IconMode.COLOR -> LoadedIcon(render(info.getBadgedIcon(density), px, 1f), tint = false)
            IconMode.MONO -> {
                val d = info.getIcon(density)
                val mono = (d as? AdaptiveIconDrawable)?.monochrome
                if (mono != null) {
                    // La capa mide 108 dp y la zona visible son los 72 centrales.
                    LoadedIcon(render(mono, px, 1.5f), tint = true)
                } else {
                    val bmp = render(d, px, 1f)
                    val pixels = IntArray(px * px).also { bmp.getPixels(it, 0, px, 0, 0, px, px) }
                    val mask = IconMask.grayMask(pixels, invert)
                    if (mask == null) {
                        LoadedIcon(null, false)
                    } else {
                        bmp.setPixels(mask, 0, px, 0, 0, px, px)
                        LoadedIcon(bmp, tint = true)
                    }
                }
            }
        }
    }

    /** Dibuja [d] en un bitmap px x px; [scale] > 1 amplía el lienzo del drawable alrededor del centro. */
    private fun render(d: Drawable, px: Int, scale: Float): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val big = (px * scale).toInt()
        val off = (px - big) / 2
        d.setBounds(off, off, off + big, off + big)
        d.draw(Canvas(bmp))
        return bmp
    }
}
