package com.usos.launcher.data.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.usos.launcher.data.PersistJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigBackupTest {
    @Test fun roundTripKeepsValuesAndLocalKeys() {
        val src = mutablePreferencesOf(
            stringPreferencesKey("dock_apps") to "a/0\n\nb/0",
            intPreferencesKey("mosaic_columns") to 4,
            booleanPreferencesKey("dock_on") to true,
            stringPreferencesKey("weather_cache") to "x",
        )
        val file = ConfigFile(prefs = ConfigBackup.fromPrefs(src.asMap()), feeds = listOf(FeedEntry("https://x/rss", "X")))
        assertNull(file.prefs["weather_cache"])
        val decoded = PersistJson.decodeFromString(ConfigFile.serializer(), PersistJson.encodeToString(ConfigFile.serializer(), file))

        val dst = mutablePreferencesOf(
            intPreferencesKey("mosaic_columns") to 3,
            stringPreferencesKey("old_only") to "gone",
            stringPreferencesKey("update_last_check") to "keep",
        )
        ConfigBackup.apply(dst, decoded.prefs)
        assertEquals(4, dst[intPreferencesKey("mosaic_columns")])
        assertEquals(true, dst[booleanPreferencesKey("dock_on")])
        assertEquals("a/0\n\nb/0", dst[stringPreferencesKey("dock_apps")])
        assertNull(dst[stringPreferencesKey("old_only")])
        assertEquals("keep", dst[stringPreferencesKey("update_last_check")])
        assertEquals(1, decoded.feeds.size)
    }
}
