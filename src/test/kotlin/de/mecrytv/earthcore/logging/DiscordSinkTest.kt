package de.mecrytv.earthcore.logging

import com.google.gson.JsonParser
import de.mecrytv.earthcore.logging.api.DiscordRoute
import de.mecrytv.earthcore.logging.api.LogCategory
import de.mecrytv.earthcore.logging.api.LogEntry
import de.mecrytv.earthcore.logging.api.LogLevel
import de.mecrytv.earthcore.logging.internal.DiscordSink
import de.mecrytv.earthcore.logging.internal.WebhookResponse
import de.mecrytv.earthcore.logging.internal.WebhookSender
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class NotierenderSender : WebhookSender {

    val gesendet = mutableListOf<Pair<String, String>>()
    var antwort: WebhookResponse = WebhookResponse(204)
    var fehler: Throwable? = null

    override fun send(url: String, body: String): WebhookResponse {
        fehler?.let { throw it }
        gesendet += url to body
        return antwort
    }
}

class DiscordSinkTest {

    private val technik = DiscordRoute("https://discord.com/api/webhooks/1/technikgeheim", LogLevel.WARN)
    private val team = DiscordRoute(
        url = "https://discord.com/api/webhooks/2/teamgeheim",
        minLevel = LogLevel.INFO,
        categories = listOf(LogCategory.MODERATION),
    )

    private val sender = NotierenderSender()
    private var jetzt = 1_000_000L

    private fun sink(vararg routes: DiscordRoute) =
        DiscordSink(routes.toList(), sender, Logger.getLogger("test"), clock = { jetzt })

    private fun eintrag(level: LogLevel, category: LogCategory = LogCategory.SYSTEM, message: String = "hallo") =
        LogEntry(level, category, message, plugin = "EarthShop")

    private fun embeds(body: String) =
        JsonParser.parseString(body).asJsonObject.getAsJsonArray("embeds")

    // ---------------------------------------------------------------- Routing

    @Test
    fun `ein eintrag unter dem mindest-level geht nicht raus`() {
        val sink = sink(technik)
        sink.accept(eintrag(LogLevel.INFO))

        assertEquals(0, sink.flush())
        assertTrue(sender.gesendet.isEmpty())
    }

    @Test
    fun `kategorien filtern pro webhook`() {
        val sink = sink(technik, team)
        sink.accept(eintrag(LogLevel.ERROR, category = LogCategory.MODERATION))
        sink.flush()

        assertEquals(2, sender.gesendet.size)
        assertContentEquals(listOf(technik.url, team.url), sender.gesendet.map { it.first }.sorted().sorted())
    }

    @Test
    fun `ein webhook ohne kategorien nimmt alles ab dem level`() {
        val sink = sink(technik)
        sink.accept(eintrag(LogLevel.ERROR, category = LogCategory.SECURITY))

        assertEquals(1, sink.flush())
    }

    @Test
    fun `ein eintrag ausserhalb der kategorie erreicht den team-webhook nicht`() {
        val sink = sink(team)
        sink.accept(eintrag(LogLevel.ERROR, category = LogCategory.ECONOMY))

        assertEquals(0, sink.flush())
    }

    @Test
    fun `eine leere url wird ignoriert`() {
        val sink = sink(DiscordRoute("", LogLevel.DEBUG))
        sink.accept(eintrag(LogLevel.ERROR))

        assertEquals(0, sink.flush())
        assertTrue(sender.gesendet.isEmpty())
    }

    // ---------------------------------------------------------------- Sammeln

    @Test
    fun `bis zu zehn eintraege gehen in einer nachricht raus`() {
        val sink = sink(technik)
        repeat(12) { sink.accept(eintrag(LogLevel.ERROR, message = "nummer $it")) }

        assertEquals(10, sink.flush())
        assertEquals(1, sender.gesendet.size)
        assertEquals(10, embeds(sender.gesendet.single().second).size())

        assertEquals(2, sink.flush())
        assertEquals(2, embeds(sender.gesendet.last().second).size())
    }

    @Test
    fun `die warteschlange laeuft nicht unbegrenzt voll`() {
        val sink = sink(technik)
        repeat(DiscordSink.MAX_QUEUE + 50) { sink.accept(eintrag(LogLevel.ERROR)) }

        var gesamt = 0
        repeat(100) { gesamt += sink.flush() }

        assertTrue(gesamt <= DiscordSink.MAX_QUEUE, "Es wurden $gesamt Eintraege gehalten")
    }

    // ---------------------------------------------------------------- Ratenbegrenzung

    @Test
    fun `nach einem 429 wird pausiert und danach erneut gesendet`() {
        val sink = sink(technik)
        sender.antwort = WebhookResponse(429, retryAfterMillis = 5_000)
        sink.accept(eintrag(LogLevel.ERROR))

        assertEquals(0, sink.flush())
        assertEquals(1, sender.gesendet.size)

        sender.antwort = WebhookResponse(204)
        assertEquals(0, sink.flush(), "Waehrend der Sperre darf nichts rausgehen")

        jetzt += 5_001
        assertEquals(1, sink.flush(), "Nach der Sperre muss der Eintrag noch da sein")
    }

    @Test
    fun `ein netzwerkfehler verwirft die eintraege und wirft nicht`() {
        val sink = sink(technik)
        sender.fehler = RuntimeException("kein netz")
        sink.accept(eintrag(LogLevel.ERROR))

        assertEquals(0, sink.flush())
    }

    @Test
    fun `eine dauerhafte ablehnung wiederholt nicht endlos`() {
        val sink = sink(technik)
        sender.antwort = WebhookResponse(404)
        sink.accept(eintrag(LogLevel.ERROR))

        assertEquals(0, sink.flush())
        assertEquals(0, sink.flush())
        assertEquals(1, sender.gesendet.size, "404 darf nicht erneut versucht werden")
    }

    // ---------------------------------------------------------------- Inhalt

    @Test
    fun `das embed traegt level, kategorie, plugin und farbe`() {
        val sink = sink(technik)
        sink.accept(
            LogEntry(
                level = LogLevel.ERROR,
                category = LogCategory.ECONOMY,
                message = "Kauf fehlgeschlagen",
                plugin = "EarthShop",
                actor = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                details = mapOf("artikel" to "Schwert", "preis" to 250),
                error = IllegalStateException("kaputt"),
            ),
        )
        sink.flush()

        val embed = embeds(sender.gesendet.single().second).get(0).asJsonObject
        assertEquals("ERROR - ECONOMY", embed.get("title").asString)
        assertEquals("Kauf fehlgeschlagen", embed.get("description").asString)
        assertEquals(LogLevel.ERROR.color, embed.get("color").asInt)
        assertEquals("EarthShop", embed.getAsJsonObject("footer").get("text").asString)

        val felder = embed.getAsJsonArray("fields").map { it.asJsonObject.get("name").asString }
        assertContentEquals(listOf("Ausgeloest von", "artikel", "preis", "Fehler"), felder)
    }

    @Test
    fun `die webhook-url taucht in keiner meldung auf`() {
        assertFalse(DiscordSink.mask(technik.url).contains("technikgeheim"))
        assertTrue(DiscordSink.mask(technik.url).endsWith("kgeheim".takeLast(6)))
    }

    @Test
    fun `sehr lange nachrichten werden gekuerzt statt discord zu sprengen`() {
        val sink = sink(technik)
        sink.accept(eintrag(LogLevel.ERROR, message = "x".repeat(5000)))
        sink.flush()

        val embed = embeds(sender.gesendet.single().second).get(0).asJsonObject
        assertTrue(embed.get("description").asString.length <= 2000)
    }
}
