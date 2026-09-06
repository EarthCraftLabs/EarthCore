package de.mecrytv.earthcore.config

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigFileTest {

    private val dir = Files.createTempDirectory("earthcore-datei").toFile()
    private val file = File(dir, "config.json")

    private val defaults = ConfigDefaults.values(
        "server.name" to "EarthCraft",
        "server.slots" to 100,
    )

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `eine vorhandene datei wird nicht ueberschrieben`() {
        val eigen = """{"server":{"name":"Mein Server"},"eigenes":{"feld":true}}"""
        file.writeText(eigen)

        val config = JsonConfigService(file = file, defaults = defaults)

        assertEquals(eigen, file.readText(), "Die vorhandene config.json darf unveraendert bleiben")
        assertEquals("Mein Server", config.getString("server.name"))
        assertTrue(config.getBoolean("eigenes.feld", false))
        assertEquals(100, config.getInt("server.slots"), "Fehlende Werte kommen weiter aus der Vorlage")
    }

    @Test
    fun `eine fehlende datei wird aus der vorlage angelegt`() {
        assertFalse(file.exists())

        JsonConfigService(file = file, defaults = defaults)

        assertTrue(file.readText().contains("EarthCraft"))
    }
}
