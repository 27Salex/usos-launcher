package com.usos.launcher.data.sections

import org.junit.Assert.assertEquals
import org.junit.Test

class DockPrefsTest {
    @Test fun placeMovesAppAndKeepsHiddenSlots() {
        val d = DockPrefs(slots = 3, apps = listOf("a", "", "b", "c"))
        assertEquals(listOf("", "a", "b", "c"), d.with(1, "a"))
        assertEquals(listOf("a", "", "b"), d.with(3, null))
        assertEquals(listOf("a", "", "", "", "x"), DockPrefs(apps = listOf("a")).with(4, "x"))
    }

    @Test fun encodeRoundTrip() {
        val l = listOf("pkg/0", "", "other/10")
        assertEquals(l, DockPrefs.decode(DockPrefs.encode(l)))
        assertEquals(emptyList<String>(), DockPrefs.decode(null))
    }
}
