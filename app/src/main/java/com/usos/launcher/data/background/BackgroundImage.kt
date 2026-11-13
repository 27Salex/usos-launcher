package com.usos.launcher.data.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Imagen de fondo: se copia a filesDir escalada y recortada al tamaño de pantalla (sin permisos de almacenamiento). */
object BackgroundImage {
    fun file(context: Context) = File(context.filesDir, "background.jpg")

    /** Escala [w]x[h] origen para cubrir [sw]x[sh] sin ampliar (si es menor se deja tal cual). */
    fun coverScale(w: Int, h: Int, sw: Int, sh: Int): Float = minOf(1f, maxOf(sw.toFloat() / w, sh.toFloat() / h))

    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dm = context.resources.displayMetrics
            val sw = dm.widthPixels
            val sh = dm.heightPixels
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val k = coverScale(info.size.width, info.size.height, sw, sh)
                if (k < 1f) decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            }
            // Recorte centrado a la proporción de la pantalla.
            val target = sw.toFloat() / sh
            val cur = bmp.width.toFloat() / bmp.height
            val cw = if (cur > target) (bmp.height * target).toInt().coerceAtLeast(1) else bmp.width
            val ch = if (cur > target) bmp.height else (bmp.width / target).toInt().coerceAtLeast(1)
            val cropped = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
            val tmp = File(context.filesDir, "background.tmp")
            tmp.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            check(tmp.renameTo(file(context)) || (file(context).delete() && tmp.renameTo(file(context))))
            true
        }.getOrDefault(false)
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { file(context).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) } }.getOrNull()
    }
}
