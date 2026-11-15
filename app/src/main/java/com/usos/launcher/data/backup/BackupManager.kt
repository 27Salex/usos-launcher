package com.usos.launcher.data.backup

import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.usos.launcher.AppContainer
import com.usos.launcher.data.PersistJson
import com.usos.launcher.data.background.BackgroundImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Exporta/importa la configuración a un archivo elegido por el usuario (SAF). */
class BackupManager(private val c: AppContainer) {
    private val ds get() = c.dataStoreForBackup

    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = ConfigBackup.fromPrefs(ds.data.first().asMap())
            val feeds = c.feedRepository.feedsNow().map { FeedEntry(it.url, it.title) }
            val text = PersistJson.encodeToString(ConfigFile.serializer(), ConfigFile(prefs = prefs, feeds = feeds))
            c.appContext.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        }.isSuccess
    }

    /** Lee y valida un archivo; null si no es una copia de Usos. */
    suspend fun read(uri: Uri): ConfigFile? = withContext(Dispatchers.IO) {
        runCatching {
            val text = c.appContext.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            PersistJson.decodeFromString(ConfigFile.serializer(), text).takeIf { it.app == ConfigFile.APP }
        }.getOrNull()
    }

    suspend fun import(file: ConfigFile) {
        ds.edit { ConfigBackup.apply(it, file.prefs) }
        // Sin el archivo de imagen, el fondo IMAGE vuelve a negro.
        if (!BackgroundImage.file(c.appContext).exists()) {
            ds.edit { p -> if (p[stringPreferencesKey("bg_mode")] == "IMAGE") p[stringPreferencesKey("bg_mode")] = "BLACK" }
        }
        c.feedRepository.importFeeds(file.feeds)
        c.settingsStore.setFeedsSeeded()
        c.pruneMissingWidgets()
    }
}
