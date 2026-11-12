package com.usos.launcher.data.suggest

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClient(val latencyMs: Long = 0) : SuggestClient {
    val calls = mutableListOf<Pair<SuggestProvider, String>>()
    var completed = 0
    override suspend fun fetch(provider: SuggestProvider, query: String): List<String> {
        calls += provider to query
        delay(latencyMs)
        completed++
        return listOf("$query 1")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestTest {
    @Test fun parsesGoogle() {
        val body = """["hola",["hola mundo","hola amigos","  ","hola mundo","a","b","c"],[],{"google:suggestsubtypes":[[1]]}]"""
        assertEquals(listOf("hola mundo", "hola amigos", "a", "b", "c"), SuggestParser.parseGoogle(body))
    }

    @Test fun parsesDuckDuckGo() {
        val body = """[{"phrase":"hola"},{"phrase":"hola mundo"},{"other":1},{"phrase":"x"},{"phrase":"y"},{"phrase":"z"},{"phrase":"w"}]"""
        assertEquals(listOf("hola", "hola mundo", "x", "y", "z"), SuggestParser.parseDuckDuckGo(body))
    }

    @Test fun malformedGivesEmpty() {
        assertTrue(SuggestParser.parseGoogle("<html>").isEmpty())
        assertTrue(SuggestParser.parseGoogle("{}").isEmpty())
        assertTrue(SuggestParser.parseDuckDuckGo("nope").isEmpty())
        assertTrue(SuggestParser.parseDuckDuckGo("""{"phrase":"a"}""").isEmpty())
    }

    @Test fun disabledNeverCallsClient() = runTest {
        val client = FakeClient()
        val query = MutableStateFlow("")
        val out = mutableListOf<List<String>>()
        val job = SuggestFlow.build(query, MutableStateFlow(false), MutableStateFlow(SuggestProvider.GOOGLE), client)
            .onEach { out += it }.launchIn(backgroundScope)
        for (t in listOf("h", "ho", "hol", "hola")) {
            query.value = t
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertEquals(0, client.calls.size)
        assertTrue(out.all { it.isEmpty() })
        job.cancel()
    }

    @Test fun debouncesAndCancelsPrevious() = runTest {
        val client = FakeClient(latencyMs = 1_000)
        val query = MutableStateFlow("")
        val out = mutableListOf<List<String>>()
        SuggestFlow.build(query, MutableStateFlow(true), MutableStateFlow(SuggestProvider.GOOGLE), client)
            .onEach { out += it }.launchIn(backgroundScope)
        runCurrent()
        query.value = "h"; advanceTimeBy(100)
        query.value = "ho"; advanceTimeBy(100)
        query.value = "hol"
        advanceTimeBy(249); runCurrent()
        assertEquals(0, client.calls.size)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(SuggestProvider.GOOGLE to "hol"), client.calls)
        // Escribe mientras la peticion vuela: se cancela y no llega a completarse.
        query.value = "hola"
        advanceTimeBy(300); runCurrent()
        assertEquals(2, client.calls.size)
        advanceTimeBy(1_100); runCurrent()
        assertEquals(1, client.completed)
        assertEquals(listOf("hola 1"), out.last())
    }

    @Test fun blankQueryDoesNotCallClient() = runTest {
        val client = FakeClient()
        val query = MutableStateFlow("   ")
        SuggestFlow.build(query, MutableStateFlow(true), MutableStateFlow(SuggestProvider.DUCKDUCKGO), client)
            .launchIn(backgroundScope)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, client.calls.size)
    }
}
