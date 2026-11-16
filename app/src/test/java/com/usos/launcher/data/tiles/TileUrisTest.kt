package com.usos.launcher.data.tiles

import org.junit.Assert.assertEquals
import org.junit.Test

class TileUrisTest {
    @Test fun phone() {
        assertEquals("+34612345678", TileUris.normalizePhone("+34 612-34 56 78"))
        assertEquals("34612345678", TileUris.normalizePhone("+34 612 345 678", keepPlus = false))
        assertEquals("612345678", TileUris.normalizePhone("(612) 345.678"))
    }

    @Test fun uris() {
        assertEquals("google.navigation:q=Calle+Mayor+1%2C+Madrid", TileUris.mapsNav(" Calle Mayor 1, Madrid "))
        assertEquals("https://waze.com/ul?q=Casa&navigate=yes", TileUris.wazeNav("Casa"))
        assertEquals("geo:0,0?q=Casa", TileUris.destination("Casa", "x.y"))
        assertEquals("https://wa.me/34612345678", TileUris.whatsapp("+34 612 345 678"))
        assertEquals("https://t.me/+34612345678", TileUris.telegram("+34 612 345 678"))
        assertEquals("smsto:+34612345678", TileUris.sms("+34 612 345 678"))
        assertEquals("tel:612345678", TileUris.tel("612 34 56 78"))
    }

    @Test fun navInstalled() {
        assertEquals(listOf(TileUris.MAPS, TileUris.WAZE), NavApps.installed(listOf("com.waze", "a.b", TileUris.MAPS)).map { it.first })
    }

    @Test fun virtualKeys() {
        val e = CustomTile(id = "abc", name = "Casa").toAppEntry()
        assertEquals("usos.tile.abc/-1", e.key)
        assertEquals("abc", CustomTiles.idOf(e.packageName))
    }
}
