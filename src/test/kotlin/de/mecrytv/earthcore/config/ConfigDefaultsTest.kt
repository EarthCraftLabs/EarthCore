package de.mecrytv.earthcore.config

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

private class WeiterWegAufrufer {

    fun sucher(): Class<*>? = CallerLookup.outside(ConfigDefaults::class.java)
}

private class KindZuerstLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith("de.mecrytv.fremd.")) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            return findClass(name).also { if (resolve) resolveClass(it) }
        }
    }
}

class ConfigDefaultsTest {

    private val dir = Files.createTempDirectory("fremdes-plugin").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `der aufrufer wird gefunden, nicht ConfigDefaults selbst`() {
        assertEquals(ConfigDefaultsTest::class.java, CallerLookup.outside(ConfigDefaults::class.java))
        assertEquals(WeiterWegAufrufer::class.java, WeiterWegAufrufer().sucher())
    }

    @Test
    fun `eine klasse aus einem fremden classloader findet ihre eigene ressource`() {
        val klassenpfad = File(javaClass.protectionDomain.codeSource.location.toURI())
        val name = "de/mecrytv/fremd/FremdesPlugin.class"
        File(dir, name).apply { parentFile.mkdirs() }
            .writeBytes(File(klassenpfad, name).readBytes())
        File(dir, "fremd.json").writeText("""{"quelle": "aus-dem-fremden-plugin"}""")

        val loader = KindZuerstLoader(arrayOf(dir.toURI().toURL()), javaClass.classLoader)
        val klasse = loader.loadClass("de.mecrytv.fremd.FremdesPlugin")
        assertNotSame(javaClass.classLoader, klasse.classLoader)

        val ergebnis = klasse.getMethod("laden").invoke(klasse.getDeclaredConstructor().newInstance())

        assertTrue(
            (ergebnis as String).contains("aus-dem-fremden-plugin"),
            "Es wurde die falsche fremd.json geladen: $ergebnis",
        )
    }

    @Test
    fun `ein ausdruecklich uebergebener classloader gewinnt`() {
        val leer = URLClassLoader(emptyArray(), null)

        val fehler = assertFailsWith<IllegalStateException> {
            ConfigDefaults.resource("messages.json", leer).load(JsonConfigService.defaultGson())
        }

        assertTrue(fehler.message!!.contains("messages.json"), fehler.message!!)
    }

    @Test
    fun `ohne uebergabe findet EarthCore seine eigene messages-datei`() {
        val wurzel = ConfigDefaults.resource("messages.json").load(JsonConfigService.defaultGson())

        assertTrue(wurzel.has("cooldown"), "messages.json aus dem eigenen Jar erwartet, war $wurzel")
    }

    @Test
    fun `values und model bleiben unveraendert nutzbar`() {
        val ausWerten = ConfigDefaults.values("a.b" to 1, "a.c" to "x").load(JsonConfigService.defaultGson())
        assertEquals(1, ausWerten.getAsJsonObject("a").get("b").asInt)

        val ausModell = ConfigDefaults.model(Settings()).load(JsonConfigService.defaultGson())
        assertEquals("earthcraft", ausModell.get("namespace").asString)
    }
}
