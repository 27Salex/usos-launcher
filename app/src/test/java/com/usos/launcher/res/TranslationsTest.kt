package com.usos.launcher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Las traducciones deben tener exactamente las mismas claves que values/strings.xml (el idioma por defecto)
 * y cada idioma de res/xml/locales_config.xml debe tener su carpeta values-xx. Ver TRANSLATING.md.
 */
class TranslationsTest {
    private val res: File = listOf("app/src/main/res", "src/main/res").map { File(it) }.first { it.isDirectory }

    private fun keys(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = sortedSetOf<String>()
        for (tag in listOf("string", "plurals", "string-array")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n.attributes.getNamedItem("translatable")?.nodeValue == "false") continue
                out += tag + ":" + n.attributes.getNamedItem("name").nodeValue
            }
        }
        return out
    }

    private fun translations(): List<File> =
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!
            .map { File(it, "strings.xml") }.filter { it.exists() }.sortedBy { it.parent }

    @Test fun everyTranslationHasExactlyTheDefaultKeys() {
        val base = keys(File(res, "values/strings.xml"))
        assertTrue("values/strings.xml vacío", base.isNotEmpty())
        val all = translations()
        assertTrue("no hay ninguna traducción", all.isNotEmpty())
        for (f in all) {
            val k = keys(f)
            assertEquals("Faltan en ${f.parentFile.name}: ${base - k}", emptySet<String>(), base - k)
            assertEquals("Sobran en ${f.parentFile.name}: ${k - base}", emptySet<String>(), k - base)
        }
    }

    @Test fun placeholdersMatchTheDefaultLanguage() {
        fun placeholders(file: File): Map<String, List<String>> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val out = sortedMapOf<String, List<String>>()
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                out[n.attributes.getNamedItem("name").nodeValue] =
                    Regex("""%\d\$[sd]""").findAll(n.textContent).map { it.value }.sorted().toList()
            }
            return out
        }
        val base = placeholders(File(res, "values/strings.xml"))
        for (f in translations()) {
            for ((k, v) in placeholders(f)) {
                assertEquals("Marcadores distintos en ${f.parentFile.name}/$k", base[k], v)
            }
        }
    }

    @Test fun localesConfigMatchesResourceFolders() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "xml/locales_config.xml"))
        val nodes = doc.getElementsByTagName("locale")
        val tags = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }
        val folders = translations().map { it.parentFile.name.removePrefix("values-") }
        // «es» es el idioma por defecto (values/), el resto necesita su values-xx.
        assertTrue(tags.contains("es"))
        assertEquals(tags.filter { it != "es" }.sorted(), folders.sorted())
    }
}
