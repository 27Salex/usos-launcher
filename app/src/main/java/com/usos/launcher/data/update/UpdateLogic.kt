package com.usos.launcher.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Comparación semántica de versiones «X.Y.Z» (con «v» opcional). Las prereleases («1.1.0-beta») no se aceptan. */
object UpdateVersion {
    fun parse(tag: String?): List<Int>? {
        val t = tag?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        val parts = t.split('.')
        if (parts.size !in 1..4) return null
        return parts.map { p -> if (p.isNotEmpty() && p.all { it in '0'..'9' } && p.length <= 9) p.toInt() else return null }
    }

    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** true si [remote] es una versión estable estrictamente mayor que [local]. Cualquier valor ilegible = false. */
    fun isNewer(remote: String?, local: String?): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(local) ?: return false
        return compare(r, l) > 0
    }
}

/** Release de GitHub reducida a lo necesario. Se persiste en DataStore (valores por defecto en todo campo nuevo). */
@Serializable
data class ReleaseInfo(
    val version: String = "",
    val notes: String = "",
    val apkUrl: String = "",
    val apkSize: Long = 0,
)

object ReleaseParser {
    /** null si es draft/prerelease, el tag no es una versión estable o no trae un .apk. */
    fun parse(json: String): ReleaseInfo? {
        val o = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        if (o.bool("draft") || o.bool("prerelease")) return null
        val tag = o.str("tag_name") ?: return null
        if (UpdateVersion.parse(tag) == null) return null
        val assets = (o["assets"] as? JsonArray) ?: return null
        val apk = assets.mapNotNull { it as? JsonObject }.firstOrNull { a ->
            a.str("name")?.endsWith(".apk", ignoreCase = true) == true && a.str("browser_download_url") != null
        } ?: return null
        return ReleaseInfo(
            version = tag.trim().removePrefix("v").removePrefix("V"),
            notes = o.str("body").orEmpty(),
            apkUrl = apk.str("browser_download_url")!!,
            apkSize = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0,
        )
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
}

/** Notas de la release (Markdown) a texto plano corto. */
object ReleaseNotes {
    fun plain(body: String, maxChars: Int = 600): String {
        val lines = body.replace("\r", "").lines().map { l ->
            var s = l.trim()
            s = s.replace(Regex("^#{1,6}\\s*"), "")
            s = s.replace(Regex("^[-*+]\\s+"), "· ")
            s = s.replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            s = s.replace("**", "").replace("__", "").replace("`", "")
            s
        }
        val text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
    }
}

/** Comprobación previa a instalar: mismo paquete, misma firma y versionCode mayor. */
object ApkCheck {
    enum class Result { OK, WRONG_PACKAGE, WRONG_SIGNATURE, NOT_NEWER, UNREADABLE }

    fun verify(
        installedPackage: String, apkPackage: String?, installedCode: Long, apkCode: Long,
        installedSigners: Set<String>, apkSigners: Set<String>,
    ): Result = when {
        apkPackage == null || apkSigners.isEmpty() -> Result.UNREADABLE
        apkPackage != installedPackage -> Result.WRONG_PACKAGE
        installedSigners.isEmpty() || apkSigners != installedSigners -> Result.WRONG_SIGNATURE
        apkCode <= installedCode -> Result.NOT_NEWER
        else -> Result.OK
    }
}

/** Cuándo toca la comprobación automática: como máximo una vez cada 24 h. */
object UpdateSchedule {
    const val INTERVAL_MS = 24L * 60 * 60 * 1000
    fun due(lastCheckMs: Long, nowMs: Long): Boolean =
        lastCheckMs <= 0 || nowMs - lastCheckMs >= INTERVAL_MS || nowMs < lastCheckMs
}
