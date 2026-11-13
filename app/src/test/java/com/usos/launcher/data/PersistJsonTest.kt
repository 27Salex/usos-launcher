package com.usos.launcher.data

import com.usos.launcher.data.sections.AppConfig
import com.usos.launcher.data.sections.Highlight
import com.usos.launcher.data.sections.TileSize
import com.usos.launcher.domain.Section
import com.usos.launcher.ui.widgets.HostedWidget
import com.usos.launcher.ui.widgets.WidgetCells
import com.usos.launcher.ui.widgets.WidgetSizing
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo guardado por la 1.0.0 debe leerse con el código nuevo (actualizar encima no rompe la configuración). */
class PersistJsonTest {
    @Test fun appConfigFrom100() {
        val raw = """[{"packageName":"com.a","userSerial":0,"section":"WORK","order":2,"inMosaic":true,"code":"AB",
            "highlight":"ACCENT","showUsage":true,"manual":true,"campoFuturo":1}]"""
        val c = PersistJson.decodeFromString(ListSerializer(AppConfig.serializer()), raw).single()
        assertEquals(Section.WORK, c.section)
        assertEquals(Highlight.ACCENT, c.highlight)
        assertEquals(TileSize.S1X1, c.size)
        assertEquals("AB", c.code)
    }

    @Test fun unknownEnumFallsBackToDefault() {
        val raw = """[{"packageName":"com.a","userSerial":0,"section":"WORK","order":0,"inMosaic":false,"highlight":"NUEVO","size":"S9X9"}]"""
        val c = PersistJson.decodeFromString(ListSerializer(AppConfig.serializer()), raw).single()
        assertEquals(Highlight.NORMAL, c.highlight)
        assertEquals(TileSize.S1X1, c.size)
    }

    @Test fun widgetsFrom100MigrateToCells() {
        val raw = """[{"id":7,"heightDp":200},{"id":8}]"""
        val l = PersistJson.decodeFromString(ListSerializer(HostedWidget.serializer()), raw)
        assertEquals(WidgetCells(4, 2), WidgetSizing.cells(l[0], 4, 1))
        assertEquals(WidgetCells(3, 1), WidgetSizing.cells(l[1], 3, 1))
    }
}
