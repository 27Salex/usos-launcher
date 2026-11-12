package com.usos.launcher.data.suggest

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Peticion HTTPS cancelable a Google o DuckDuckGo. Los errores de red devuelven lista vacia. */
class OkHttpSuggestClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
) : SuggestClient {
    override suspend fun fetch(provider: SuggestProvider, query: String): List<String> =
        suspendCancellableCoroutine { cont ->
            val url = provider.baseUrl.toHttpUrl().newBuilder().apply {
                provider.fixedParams.forEach { (k, v) -> addQueryParameter(k, v) }
                addQueryParameter(provider.queryParam, query)
            }.build()
            val call = http.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(emptyList())
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        // Se decodifica siempre como UTF-8: Google etiqueta a veces la respuesta como ISO-8859-1.
                        val text = if (it.isSuccessful) runCatching {
                            it.body.bytes().toString(Charsets.UTF_8)
                        }.getOrNull() else null
                        if (cont.isActive) cont.resume(text?.let { t -> SuggestParser.parse(provider, t) } ?: emptyList())
                    }
                }
            })
        }
}
