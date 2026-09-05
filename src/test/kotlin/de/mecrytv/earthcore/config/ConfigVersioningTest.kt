package de.mecrytv.earthcore.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigVersioningTest {

    private val dir = Files.createTempDirectory("earthcore-versioning").toFile()
    private val file = File(dir, "config.json")

    private val defaults = ConfigDefaults.values(
        "server.name" to "EarthCraft",
        "server.slots" to 100,
    )

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun service(versioning: ConfigVersioning) =
        JsonConfigService(file = file, defaults = defaults, versioning = versioning)

    private fun aufDerPlatte(): JsonObject = JsonParser.parseString(file.readText()).asJsonObject

    @Test
    fun `eine neue datei bekommt die aktuelle version gestempelt`() {
        service(ConfigVersioning(current = 3))

        assertEquals(3, aufDerPlatte().get("configVersion").asInt)
    }

    @Test
    fun `ohne versionsangabe bleibt alles wie bisher`() {
        val config = service(ConfigVersioning.NONE)

        assertEquals("EarthCraft", config.getString("server.name"))
        assertFalse(
            aufDerPlatte().has("configVersion"),
            "Ohne Versionierung soll kein Schluessel in der Datei landen",
        )
    }

    @Test
    fun `eine datei ohne versionsschluessel gilt als version eins`() {
        file.writeText("""{"server": {"name": "Alt"}}""")

        val gelaufen = mutableListOf<Int>()
        service(
            ConfigVersioning(
                current = 2,
                steps = mapOf(2 to ConfigMigration { gelaufen += 2 }),
            ),
        )

        assertEquals(listOf(2), gelaufen)
    }

    @Test
    fun `nur ausstehende schritte laufen, und zwar in reihenfolge`() {
        file.writeText("""{"configVersion": 2, "server": {"name": "Alt"}}""")

        val gelaufen = mutableListOf<Int>()
        val schritt = { nummer: Int -> ConfigMigration { gelaufen += nummer } }

        service(
            ConfigVersioning(
                current = 4,
                steps = mapOf(2 to schritt(2), 3 to schritt(3), 4 to schritt(4)),
            ),
        )

        assertEquals(listOf(3, 4), gelaufen)
    }

    @Test
    fun `eine migration kann schluessel umbenennen`() {
        file.writeText("""{"configVersion": 1, "server": {"title": "Alt", "slots": 42}}""")

        val config = service(
            ConfigVersioning(
                current = 2,
                steps = mapOf(
                    2 to ConfigMigration { root ->
                        val server = root.getAsJsonObject("server")
                        server.add("name", server.remove("title"))
                    },
                ),
            ),
        )

        assertEquals("Alt", config.getString("server.name"))
        assertEquals(42, config.getInt("server.slots"))
        assertNull(config.find("server.title"))
        assertEquals(2, config.getInt("configVersion"))
    }

    @Test
    fun `eine schon aktuelle datei wird nicht angefasst`() {
        file.writeText("""{"configVersion": 2, "server": {"name": "Alt", "slots": 7}}""")

        var gelaufen = false
        val config = service(
            ConfigVersioning(current = 2, steps = mapOf(2 to ConfigMigration { gelaufen = true })),
        )

        assertFalse(gelaufen)
        assertEquals("Alt", config.getString("server.name"))
    }

    @Test
    fun `eine scheiternde migration laesst die datei auf der platte unangetastet`() {
        val original = """{"configVersion": 1, "server": {"name": "Alt"}}"""
        file.writeText(original)

        assertFailsWith<IllegalStateException> {
            service(
                ConfigVersioning(
                    current = 2,
                    steps = mapOf(2 to ConfigMigration { error("kaputt") }),
                ),
            )
        }

        assertEquals(original, file.readText())
    }

    @Test
    fun `unsinnige versionsangaben werden beim anlegen abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { ConfigVersioning(current = 0) }
        assertFailsWith<IllegalArgumentException> {
            ConfigVersioning(current = 2, steps = mapOf(5 to ConfigMigration { }))
        }
        assertFailsWith<IllegalArgumentException> {
            ConfigVersioning(current = 2, steps = mapOf(1 to ConfigMigration { }))
        }
    }

    @Test
    fun `ein eigener versionsschluessel wird benutzt`() {
        service(ConfigVersioning(current = 5, key = "schemaVersion"))

        assertEquals(5, aufDerPlatte().get("schemaVersion").asInt)
        assertFalse(aufDerPlatte().has("configVersion"))
    }
}
