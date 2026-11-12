package com.usos.launcher.data.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionOrderTest {
    private val keys = listOf("a", "b", "c")

    @Test fun moveDownSwapsAndRenumbers() {
        assertEquals(mapOf("b" to 0, "a" to 1, "c" to 2), SectionOrder.move(keys, "a", 1))
    }

    @Test fun moveUpFromTopIsNoop() {
        assertNull(SectionOrder.move(keys, "a", -1))
    }

    @Test fun unknownKeyIsNoop() {
        assertNull(SectionOrder.move(keys, "z", 1))
    }
}
