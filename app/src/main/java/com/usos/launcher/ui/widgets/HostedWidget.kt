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

/** Tamano elegido: bajo = minHeight declarado, medio = x1,5, alto = x2 (con suelo de [WidgetSizing.MIN_BASE_DP]). */
enum class WidgetSize(val factor: Float, val label: String) {
    LOW(1f, "BAJO"), MEDIUM(1.5f, "MEDIO"), HIGH(2f, "ALTO"),
}

/** Widget colocado: el id lo asigna AppWidgetHost y se conserva entre reinicios. El orden es el de la lista. */
@Serializable
data class HostedWidget(val id: Int, val size: WidgetSize = WidgetSize.LOW)

/** Calculos puros de tamano y de lista. */
object WidgetSizing {
    const val MIN_BASE_DP = 72

    /** Alto en dp para un widget cuyo minHeight declarado es [declaredMinHeightDp]. */
    fun heightDp(declaredMinHeightDp: Int, size: WidgetSize): Int =
        (maxOf(declaredMinHeightDp, MIN_BASE_DP) * size.factor).toInt()

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

    fun withSize(list: List<HostedWidget>, id: Int, size: WidgetSize): List<HostedWidget> =
        list.map { if (it.id == id) it.copy(size = size) else it }
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
