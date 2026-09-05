package de.mecrytv.earthcore.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.mecrytv.earthcore.logging.api.DiscordRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigTemplateTest {

    private val gson = JsonConfigService.defaultGson()

    private val vorlage: JsonObject =
        ConfigDefaults.resource("config.json").load(gson)

    private val ausModell: JsonObject =
        ConfigDefaults.model(PluginConfig()).load(gson)

    private fun pfade(element: JsonElement, prefix: String = ""): Set<String> = buildSet {
        if (element !is JsonObject) return@buildSet
        for ((schluessel, wert) in element.entrySet()) {
            val pfad = if (prefix.isEmpty()) schluessel else "$prefix.$schluessel"
            add(pfad)
            addAll(pfade(wert, pfad))
        }
    }

    // ---------------------------------------------------------------- Kein Geheimnis im Jar

    @Test
    fun `die mitgelieferte vorlage enthaelt kein passwort`() {
        val passwort = vorlage.getAsJsonObject("database").get("password").asString

        assertEquals("", passwort, "In der ausgelieferten config.json darf kein Passwort stehen")
    }

    @Test
    fun `die mitgelieferte vorlage enthaelt keine webhook-url`() {
        val urls = vorlage.getAsJsonObject("logging").getAsJsonArray("discord")
            .map { it.asJsonObject.get("url").asString }

        assertTrue(urls.isNotEmpty(), "Die Vorlage soll die Form eines Webhooks zeigen")
        urls.forEachIndexed { index, url ->
            assertEquals("", url, "Webhook $index in der ausgelieferten config.json muss leer sein")
        }
    }

    @Test
    fun `die vorlage enthaelt keine sonstigen verdaechtigen werte`() {
        val text = gson.toJson(vorlage)

        listOf("discord.com/api/webhooks/", "@", "://textures", "geheim", "password123").forEach { fund ->
            assertTrue(
                !text.contains(fund),
                "Die Vorlage enthaelt '$fund' - das sieht nach echten Daten aus",
            )
        }
    }

    // ---------------------------------------------------------------- Kein Auseinanderlaufen

    @Test
    fun `vorlage und datenklassen haben dieselben schluessel`() {
        assertEquals(
            pfade(ausModell).sorted(),
            pfade(vorlage).sorted(),
            "config.json und PluginConfig sind auseinandergelaufen",
        )
    }

    @Test
    fun `der beispiel-webhook hat die felder von DiscordRoute`() {
        val beispiel = vorlage.getAsJsonObject("logging").getAsJsonArray("discord")
            .first().asJsonObject
        val erwartet = gson.toJsonTree(DiscordRoute()).asJsonObject

        assertEquals(erwartet.keySet().sorted(), beispiel.keySet().sorted())
    }

    @Test
    fun `die vorlage laesst sich als PluginConfig lesen`() {
        val gelesen = gson.fromJson(vorlage, PluginConfig::class.java)

        assertEquals("earthcraft", gelesen.settings.namespace)
        assertEquals(3306, gelesen.database.port)
        assertEquals(30, gelesen.logging.retentionDays)
        assertEquals(2, gelesen.logging.discord.size)
    }

    @Test
    fun `leere webhooks sind wirkungslos`() {
        val gelesen = gson.fromJson(vorlage, PluginConfig::class.java)

        assertTrue(
            gelesen.logging.discord.none { it.url.isNotBlank() },
            "Ein Beispiel-Webhook darf beim ersten Start nichts verschicken",
        )
    }
}
