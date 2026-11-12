package com.usos.launcher.data.suggest

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Proveedor de sugerencias. Cada pulsacion (tras la espera) se envia al elegido. */
enum class SuggestProvider(val label: String, val baseUrl: String, val queryParam: String, val fixedParams: Map<String, String>) {
    GOOGLE("GOOGLE", "https://suggestqueries.google.com/complete/search", "q", mapOf("client" to "firefox")),
    DUCKDUCKGO("DUCKDUCKGO", "https://duckduckgo.com/ac/", "q", emptyMap());

    companion object {
        fun fromName(name: String?): SuggestProvider = entries.firstOrNull { it.name == name } ?: GOOGLE
    }
}

/** Cliente de red de las sugerencias. Una implementacion falsa permite contar llamadas en los tests. */
fun interface SuggestClient {
    /** Sugerencias para [query]; vacio si falla. Cancelable: cancelar la corrutina cancela la peticion. */
    suspend fun fetch(provider: SuggestProvider, query: String): List<String>
}

/** Analisis puro de las respuestas JSON de ambos proveedores. */
object SuggestParser {
    const val MAX = 5
    private val json = Json { ignoreUnknownKeys = true }

    /** Google (client=firefox): `["texto",["sug1","sug2",...],...]`. */
    fun parseGoogle(body: String): List<String> = runCatching {
        val root = json.parseToJsonElement(body).jsonArray
        (root.getOrNull(1) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrDefault(emptyList()).clean()

    /** DuckDuckGo (/ac/): `[{"phrase":"sug1"},{"phrase":"sug2"},...]`. */
    fun parseDuckDuckGo(body: String): List<String> = runCatching {
        json.parseToJsonElement(body).jsonArray.mapNotNull { (it as? JsonObject)?.get("phrase")?.jsonPrimitive?.contentOrNull }
    }.getOrDefault(emptyList()).clean()

    fun parse(provider: SuggestProvider, body: String): List<String> = when (provider) {
        SuggestProvider.GOOGLE -> parseGoogle(body)
        SuggestProvider.DUCKDUCKGO -> parseDuckDuckGo(body)
    }

    private fun List<String>.clean() = map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX)
}

/** Flujo de sugerencias: espera, cancelacion de la peticion anterior y garantia de privacidad. */
object SuggestFlow {
    const val DEBOUNCE_MS = 250L

    /**
     * Con [enabled] en false (o texto vacio) emite lista vacia SIN tocar [client]: no hay ninguna rama
     * de este flujo que llame al cliente si las sugerencias estan apagadas. Cada cambio de texto, proveedor
     * o ajuste cancela la espera o la peticion pendiente (flatMapLatest).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun build(
        query: Flow<String>,
        enabled: Flow<Boolean>,
        provider: Flow<SuggestProvider>,
        client: SuggestClient,
    ): Flow<List<String>> =
        combine(enabled, provider, query.distinctUntilChanged()) { on, p, q -> Triple(on, p, q.trim()) }
            .distinctUntilChanged()
            .flatMapLatest { (on, p, q) ->
                if (!on || q.isEmpty()) {
                    flow { emit(emptyList()) }
                } else {
                    flow {
                        delay(DEBOUNCE_MS)
                        emit(client.fetch(p, q))
                    }
                }
            }
}
