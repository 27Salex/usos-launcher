package com.usos.launcher.data.ai

import java.net.URLEncoder

/** Cómo se le pasa una pregunta ya escrita a un asistente (ver tabla en CLAUDE.md). */
enum class AskMethod { URL, CLIPBOARD }

/**
 * Asistentes de IA conocidos, en el orden de preferencia por defecto. [askBase] = URL web con `q=` (se abre
 * dirigida al paquete de la app por App Links y, si no la resuelve, en el navegador); sin ella se copia la
 * pregunta al portapapeles y se abre la app.
 */
enum class Assistant(
    val label: String,
    val pkg: String,
    val webHome: String,
    private val askBase: String?,
) {
    CLAUDE("CLAUDE", "com.anthropic.claude", "https://claude.ai/new", "https://claude.ai/new?q="),
    GEMINI("GEMINI", "com.google.android.apps.bard", "https://gemini.google.com/app", null),
    CHATGPT("CHATGPT", "com.openai.chatgpt", "https://chatgpt.com/", "https://chatgpt.com/?q="),
    COPILOT("COPILOT", "com.microsoft.copilot", "https://copilot.microsoft.com/", null),
    PERPLEXITY("PERPLEXITY", "ai.perplexity.app.android", "https://www.perplexity.ai/", "https://www.perplexity.ai/search?q="),
    GROK("GROK", "ai.x.grok", "https://grok.com/", "https://grok.com/?q="),
    MISTRAL("LE CHAT", "ai.mistral.chat", "https://chat.mistral.ai/chat", "https://chat.mistral.ai/chat?q="),
    DEEPSEEK("DEEPSEEK", "com.deepseek.chat", "https://chat.deepseek.com/", null),
    META("META AI", "com.facebook.stella", "https://www.meta.ai/", null);

    val method: AskMethod get() = if (askBase != null) AskMethod.URL else AskMethod.CLIPBOARD

    fun askUrl(text: String): String? = askBase?.let { it + URLEncoder.encode(text.trim(), "UTF-8") }
}

/** Asistente elegido: app instalada o servicio web. */
data class Resolved(val assistant: Assistant, val web: Boolean)

/** Qué hacer para preguntar / abrir. Lógica pura; la ejecuta el ViewModel con intents. */
sealed interface AskPlan {
    /** ACTION_VIEW de [url]; con [pkg] dirigido a la app (y sin él si la app no resuelve el enlace). */
    data class View(val url: String, val pkg: String?) : AskPlan
    /** Copiar la pregunta y abrir la app. */
    data object CopyAndOpenApp : AskPlan
    /** Copiar la pregunta y abrir la web (sin parámetro de pregunta). */
    data class CopyAndView(val url: String) : AskPlan
}

object Assistants {
    /** Valor guardado: null = automático (primero instalado), "NONE", "APP:CLAUDE", "WEB:CLAUDE". */
    const val NONE = "NONE"

    fun appChoice(a: Assistant) = "APP:" + a.name
    fun webChoice(a: Assistant) = "WEB:" + a.name

    /** Los instalados, en el orden de preferencia. */
    fun installed(packages: Set<String>): List<Assistant> = Assistant.entries.filter { it.pkg in packages }

    private fun parse(raw: String?): Pair<String, Assistant>? {
        val kind = raw?.substringBefore(':') ?: return null
        val a = Assistant.entries.firstOrNull { it.name == raw.substringAfter(':') } ?: return null
        return if (kind == "APP" || kind == "WEB") kind to a else null
    }

    /** Asistente efectivo; null = ninguno (el botón y la fila de pregunta se ocultan). */
    fun resolve(raw: String?, packages: Set<String>): Resolved? {
        if (raw == NONE) return null
        parse(raw)?.let { (kind, a) ->
            if (kind == "WEB") return Resolved(a, true)
            if (a.pkg in packages) return Resolved(a, false)
        }
        return installed(packages).firstOrNull()?.let { Resolved(it, false) }
    }

    fun askPlan(r: Resolved, text: String): AskPlan {
        val url = r.assistant.askUrl(text)
        return when {
            url != null -> AskPlan.View(url, if (r.web) null else r.assistant.pkg)
            r.web -> AskPlan.CopyAndView(r.assistant.webHome)
            else -> AskPlan.CopyAndOpenApp
        }
    }

}
