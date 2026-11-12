package com.usos.launcher.data.sections

import com.usos.launcher.domain.AppEntry
import com.usos.launcher.domain.Section
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionLayoutMosaicTest {
    private fun placed(name: String, order: Int, inMosaic: Boolean): PlacedApp {
        val app = AppEntry("p.$name", "A", 0, name, 0, null)
        return PlacedApp(app, AppConfig(app.packageName, 0, Section.WORK, order, inMosaic))
    }

    @Test fun uninstalledAppLeavesNoGap() {
        // a..f en el mosaico, g y h fuera; "c" desinstalada
        val list = listOf(
            placed("a", 0, true), placed("b", 1, true), placed("d", 3, true),
            placed("e", 4, true), placed("f", 5, true), placed("g", 6, false), placed("h", 7, false),
        )
        assertEquals(listOf("a", "b", "d", "e", "f", "g"), SectionLayout.mosaic(list).map { it.app.label })
    }

    @Test fun fullMosaicUnchanged() {
        val list = (0..7).map { placed("app$it", it, it < 6) }
        assertEquals(6, SectionLayout.mosaic(list).size)
        assertEquals(5, SectionLayout.mosaic(list, 5).size)
    }
}
