package com.usos.launcher.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test fun semanticCompare() {
        assertTrue(UpdateVersion.isNewer("v1.0.10", "1.0.9"))
        assertTrue(UpdateVersion.isNewer("v1.1.0", "1.0.3"))
        assertFalse(UpdateVersion.isNewer("v1.0.3", "1.0.3"))
        assertFalse(UpdateVersion.isNewer("v1.0.2", "1.0.3"))
        assertFalse(UpdateVersion.isNewer("v2.0.0-beta1", "1.0.3"))
        assertFalse(UpdateVersion.isNewer("latest", "1.0.3"))
    }

    private fun release(extra: String = "", tag: String = "v1.0.4") =
        """{"tag_name":"$tag","body":"## Novedades\n- **Algo** [web](http://x)","assets":[{"name":"usos-1.0.4.apk","browser_download_url":"https://x/a.apk","size":123}]$extra}"""

    @Test fun parserIgnoresDraftsAndPrereleases() {
        val r = ReleaseParser.parse(release())!!
        assertEquals("1.0.4", r.version); assertEquals(123, r.apkSize)
        assertNull(ReleaseParser.parse(release(""","draft":true""")))
        assertNull(ReleaseParser.parse(release(""","prerelease":true""")))
        assertNull(ReleaseParser.parse(release(tag = "v1.0.4-rc1")))
        assertNull(ReleaseParser.parse("""{"tag_name":"v1.0.4","assets":[]}"""))
    }

    @Test fun notesPlainAndTrimmed() {
        assertEquals("Novedades\n· Algo web", ReleaseNotes.plain(ReleaseParser.parse(release())!!.notes))
        assertEquals(11, ReleaseNotes.plain("a".repeat(50), 10).length)
    }

    @Test fun apkCheck() {
        val s = setOf("aa")
        fun v(pkg: String?, code: Long, sig: Set<String>) = ApkCheck.verify("p", pkg, 4, code, s, sig)
        assertEquals(ApkCheck.Result.OK, v("p", 5, s))
        assertEquals(ApkCheck.Result.WRONG_SIGNATURE, v("p", 5, setOf("bb")))
        assertEquals(ApkCheck.Result.WRONG_PACKAGE, v("q", 5, s))
        assertEquals(ApkCheck.Result.NOT_NEWER, v("p", 4, s))
        assertEquals(ApkCheck.Result.UNREADABLE, v(null, 5, s))
    }

    @Test fun schedule() {
        assertTrue(UpdateSchedule.due(0, 1000))
        assertFalse(UpdateSchedule.due(1000, 1000 + 3600_000))
        assertTrue(UpdateSchedule.due(1000, 1000 + UpdateSchedule.INTERVAL_MS))
    }
}
