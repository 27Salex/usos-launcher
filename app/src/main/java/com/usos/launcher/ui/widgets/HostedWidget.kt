package com.usos.launcher.ui.widgets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Widget colocado: el id lo asigna AppWidgetHost y se conserva entre reinicios. El orden es el de la lista. `heightDp` 0 = por defecto. */
@Serializable
data class HostedWidget(val id: Int, val heightDp: Int = 0)

/** Calculos puros de tamano y de lista. */
object WidgetSizing {
    const val STEP_DP = 48
    const val MIN_BASE_DP = 48
    const val MAX_FACTOR = 4

    /** Alto minimo en dp: minResizeHeight si existe, si no minHeight (con suelo de un paso). */
    fun baseDp(minHeightDp: Int, minResizeHeightDp: Int): Int =
        maxOf(if (minResizeHeightDp > 0) minResizeHeightDp else minHeightDp, MIN_BASE_DP)

    /** Alto maximo: 4x la base, limitado por maxResizeHeight si el widget lo declara. */
    fun maxDp(base: Int, maxResizeHeightDp: Int): Int {
        val cap = base * MAX_FACTOR
        return if (maxResizeHeightDp > 0) maxOf(base, minOf(cap, maxResizeHeightDp)) else cap
    }

    /** Alto inicial: el mayor entre minHeight declarado y el de targetCellHeight (n*70-30 dp). */
    fun defaultDp(minHeightDp: Int, targetCellHeight: Int): Int =
        maxOf(minHeightDp, if (targetCellHeight > 0) targetCellHeight * 70 - 30 else 0)

    /** Alto a dibujar: el elegido (o el por defecto) dentro de [base, max]. */
    fun heightDp(chosenDp: Int, base: Int, max: Int, defaultDp: Int): Int =
        (if (chosenDp > 0) chosenDp else defaultDp).coerceIn(base, max)

    /** Alto tras pulsar - / + : un paso de [STEP_DP], dentro de [base, max]. */
    fun step(current: Int, delta: Int, base: Int, max: Int): Int =
        (current + delta * STEP_DP).coerceIn(base, max)

    /** Ids que el host tiene asignados y que ya no estan guardados: se borran al arrancar. */
    fun orphans(hostIds: Collection<Int>, saved: List<HostedWidget>): List<Int> {
        val keep = saved.map { it.id }.toSet()
        return hostIds.filter { it !in keep }
    }

    fun move(list: List<HostedWidget>, id: Int, delta: Int): List<HostedWidget> {
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun withHeight(list: List<HostedWidget>, id: Int, heightDp: Int): List<HostedWidget> =
        list.map { if (it.id == id) it.copy(heightDp = heightDp) else it }
}

/** Widgets colocados, como un JSON en DataStore. */
class WidgetStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("hosted_widgets")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(HostedWidget.serializer())

    val widgets: Flow<List<HostedWidget>> = dataStore.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun current(): List<HostedWidget> = widgets.first()

    suspend fun update(transform: (List<HostedWidget>) -> List<HostedWidget>) {
        dataStore.edit { prefs ->
            val cur = decode(prefs[key])
            val next = transform(cur)
            if (next != cur) prefs[key] = json.encodeToString(serializer, next)
        }
    }

    private fun decode(raw: String?): List<HostedWidget> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
