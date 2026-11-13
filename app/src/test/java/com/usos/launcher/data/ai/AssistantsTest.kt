package com.usos.launcher.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantsTest {
    private val claude = Assistant.CLAUDE.pkg
    private val chatgpt = Assistant.CHATGPT.pkg

    @Test fun autoPicksFirstInstalledInOrder() {
        assertEquals(Resolved(Assistant.CLAUDE, false), Assistants.resolve(null, setOf(chatgpt, claude)))
        assertEquals(Resolved(Assistant.CHATGPT, false), Assistants.resolve(null, setOf(chatgpt)))
        assertNull(Assistants.resolve(null, emptySet()))
    }

    @Test fun noneHidesAndUninstalledChoiceFallsBack() {
        assertNull(Assistants.resolve(Assistants.NONE, setOf(claude)))
        assertEquals(Resolved(Assistant.CLAUDE, false), Assistants.resolve("APP:GROK", setOf(claude)))
        assertEquals(Resolved(Assistant.GROK, true), Assistants.resolve("WEB:GROK", emptySet()))
        assertEquals(Resolved(Assistant.CLAUDE, false), Assistants.resolve("basura", setOf(claude)))
    }

    @Test fun askPlans() {
        assertEquals(
            AskPlan.View("https://claude.ai/new?q=hola+mundo%3F", claude),
            Assistants.askPlan(Resolved(Assistant.CLAUDE, false), " hola mundo? "),
        )
        assertEquals(AskPlan.CopyAndOpenApp, Assistants.askPlan(Resolved(Assistant.GEMINI, false), "x"))
        assertEquals(
            AskPlan.CopyAndView("https://gemini.google.com/app"),
            Assistants.askPlan(Resolved(Assistant.GEMINI, true), "x"),
        )
        assertEquals(
            AskPlan.View("https://grok.com/?q=x", null),
            Assistants.askPlan(Resolved(Assistant.GROK, true), "x"),
        )
    }
}
