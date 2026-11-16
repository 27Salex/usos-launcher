package com.usos.launcher.data.tiles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.usos.launcher.data.PersistJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

/** Piezas propias como un único JSON en DataStore (clave `custom_tiles`). */
class CustomTileStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("custom_tiles")
    private val serializer = ListSerializer(CustomTile.serializer())

    val tiles: Flow<List<CustomTile>> = dataStore.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun upsert(tile: CustomTile) = write { list ->
        if (list.any { it.id == tile.id }) list.map { if (it.id == tile.id) tile else it } else list + tile
    }

    suspend fun remove(id: String) = write { list -> list.filterNot { it.id == id } }

    private suspend fun write(transform: (List<CustomTile>) -> List<CustomTile>) {
        dataStore.edit { prefs ->
            val next = transform(decode(prefs[key]))
            prefs[key] = PersistJson.encodeToString(serializer, next)
        }
    }

    private fun decode(raw: String?): List<CustomTile> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { PersistJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
