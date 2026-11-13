package com.usos.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerLockTest {
    @Test fun free_by_default() = assertTrue(PagerLock.scrollEnabled(false, false, false, 1))
    @Test fun editing_blocks() = assertFalse(PagerLock.scrollEnabled(true, false, false, 1))
    @Test fun search_blocks() = assertFalse(PagerLock.scrollEnabled(false, false, true, 1))
    @Test fun widgetsEditing_blocks_only_on_widgets_page() {
        assertFalse(PagerLock.scrollEnabled(false, true, false, 0))
        assertTrue(PagerLock.scrollEnabled(false, true, false, 1)) // flag olvidado fuera de su página
        assertTrue(PagerLock.scrollEnabled(false, true, false, 2))
    }
    @Test fun widgetsEditing_valid_only_on_widgets_page() {
        assertTrue(PagerLock.widgetsEditingValid(true, 0))
        assertFalse(PagerLock.widgetsEditingValid(true, 1))
        assertFalse(PagerLock.widgetsEditingValid(false, 0))
    }
    @Test fun settle_only_when_idle_and_between_pages() {
        assertTrue(PagerLock.needsSettle(false, 0.3f))
        assertFalse(PagerLock.needsSettle(true, 0.3f))
        assertFalse(PagerLock.needsSettle(false, 0f))
        assertEquals(1, PagerLock.HOME_PAGE)
    }
}
