package com.usos.launcher.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifBadgeTest {
    private val active = setOf("a/0")

    @Test fun rules() {
        assertTrue(NotifBadge.show(NotifMode.DOT, active, "a/0", false))
        assertFalse(NotifBadge.show(NotifMode.NONE, active, "a/0", false))
        assertFalse(NotifBadge.show(NotifMode.DOT, active, "a/0", true))
        assertFalse(NotifBadge.show(NotifMode.BORDER, active, "a/10", false))
        assertEquals(NotifMode.NONE, NotifMode.fromName("raro"))
    }
}
