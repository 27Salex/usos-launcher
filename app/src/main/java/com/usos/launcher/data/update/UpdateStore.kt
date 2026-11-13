package com.usos.launcher.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.usos.launcher.data.PersistJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Ajustes y estado persistido del actualizador (claves nuevas con valor por defecto). */
class UpdateStore(private val dataStore: DataStore<Preferences>) {
    private val autoKey = booleanPreferencesKey("update_auto")
    private val lastKey = longPreferencesKey("update_last_check")
    private val notifiedKey = stringPreferencesKey("update_notified")
    private val releaseKey = stringPreferencesKey("update_release")

    /** Buscar actualizaciones automáticamente. Encendido por defecto. */
    val auto: Flow<Boolean> = dataStore.data.map { it[autoKey] ?: true }.distinctUntilChanged()
    suspend fun setAuto(on: Boolean) { dataStore.edit { it[autoKey] = on } }

    suspend fun lastCheck(): Long = dataStore.data.first()[lastKey] ?: 0L
    suspend fun setLastCheck(ms: Long) { dataStore.edit { it[lastKey] = ms } }

    suspend fun notified(): String? = dataStore.data.first()[notifiedKey]
    suspend fun setNotified(v: String) { dataStore.edit { it[notifiedKey] = v } }

    suspend fun release(): ReleaseInfo? = dataStore.data.first()[releaseKey]
        ?.let { runCatching { PersistJson.decodeFromString(ReleaseInfo.serializer(), it) }.getOrNull() }

    suspend fun setRelease(r: ReleaseInfo?) {
        dataStore.edit {
            if (r == null) it.remove(releaseKey) else it[releaseKey] = PersistJson.encodeToString(ReleaseInfo.serializer(), r)
        }
    }
}
