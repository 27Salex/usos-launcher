package com.usos.launcher.domain

import android.content.pm.ApplicationInfo
import com.usos.launcher.data.sections.AppConfig
import com.usos.launcher.data.sections.SectionAssigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {
    private fun app(
        pkg: String = "x.y",
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
        main: Boolean = true,
        label: String = "App",
    ) = AppEntry(pkg, "$pkg.Main", if (main) 0 else 10, label, category, null, isMainProfile = main)

    @Test fun rule1WorkProfile() =
        assertEquals(Section.WORK, AppClassifier.classify(app(main = false, category = ApplicationInfo.CATEGORY_GAME)))

    @Test fun rule2KnownDistraction() =
        assertEquals(Section.LEISURE, AppClassifier.classify(app("com.instagram.android", ApplicationInfo.CATEGORY_SOCIAL)))

    @Test fun rule3GameVideoNews() {
        for (c in listOf(ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_NEWS)) {
            assertEquals(Section.LEISURE, AppClassifier.classify(app(category = c)))
        }
    }

    @Test fun rule4SocialIsTalk() =
        assertEquals(Section.TALK, AppClassifier.classify(app(category = ApplicationInfo.CATEGORY_SOCIAL)))

    @Test fun rule5MapsIsMove() =
        assertEquals(Section.MOVE, AppClassifier.classify(app(category = ApplicationInfo.CATEGORY_MAPS)))

    @Test fun rule6AudioImageProductivityIsPersonal() {
        for (c in listOf(ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_PRODUCTIVITY)) {
            assertEquals(Section.PERSONAL, AppClassifier.classify(app(category = c)))
        }
    }

    @Test fun rule7OtherIsNone() =
        assertEquals(Section.NONE, AppClassifier.classify(app(category = ApplicationInfo.CATEGORY_UNDEFINED)))

    @Test fun manualConfigIsNeverReclassified() {
        val a = app("com.netflix.mediaclient", ApplicationInfo.CATEGORY_VIDEO)
        val manual = AppConfig(a.packageName, a.userSerial, Section.WORK, 5, true, manual = true)
        val out = SectionAssigner.reconcile(listOf(a), mapOf(a.key to manual))
        assertEquals(manual, out[a.key])
    }

    @Test fun nonManualIsReclassifiedWhenRulesChange() {
        val a = app("com.netflix.mediaclient", ApplicationInfo.CATEGORY_VIDEO)
        val stale = AppConfig(a.packageName, a.userSerial, Section.WORK, 5, true, manual = false)
        val out = SectionAssigner.reconcile(listOf(a), mapOf(a.key to stale))
        assertEquals(Section.LEISURE, out[a.key]!!.section)
    }

    @Test fun initialLayoutPinsKnownAppsAndFillsMosaicInOrder() {
        val slack = app("com.Slack", ApplicationInfo.CATEGORY_SOCIAL, label = "Slack")
        val teams = app("com.microsoft.teams", label = "Teams")
        val others = (1..8).map { app("w.$it", main = false, label = "W$it") }
        val out = SectionAssigner.reconcile(listOf(others[0], teams, slack) + others.drop(1), emptyMap())
        assertEquals(Section.WORK, out[slack.key]!!.section)
        assertTrue(out[slack.key]!!.order < out[teams.key]!!.order)
        assertTrue(out[slack.key]!!.inMosaic && out[teams.key]!!.inMosaic)
        assertEquals(6, out.values.count { it.section == Section.WORK && it.inMosaic })
        assertFalse(out.values.any { it.section == Section.NONE && it.inMosaic })
    }
}
