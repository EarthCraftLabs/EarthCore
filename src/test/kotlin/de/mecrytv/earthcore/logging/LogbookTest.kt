package de.mecrytv.earthcore.logging

import de.mecrytv.earthcore.logging.api.LogEntry
import de.mecrytv.earthcore.logging.api.LogLevel
import de.mecrytv.earthcore.logging.api.LogSink
import de.mecrytv.earthcore.logging.api.record
import de.mecrytv.earthcore.logging.internal.ConsoleSink
import de.mecrytv.earthcore.logging.internal.LogRecord
import de.mecrytv.earthcore.logging.internal.StandardLogbook
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class SammelndeSenke(override val name: String = "test") : LogSink {

    val eintraege = mutableListOf<LogEntry>()
    var geschlossen = false

    override fun accept(entry: LogEntry) {
        eintraege += entry
    }

    override fun close() {
        geschlossen = true
    }
}

private class KaputteSenke : LogSink {

    override val name: String = "kaputt"

    override fun accept(entry: LogEntry): Unit = error("Senke defekt")

    override fun close() = Unit
}

private class SammelnderHandler : Handler() {

    val saetze = mutableListOf<java.util.logging.LogRecord>()

    override fun publish(record: java.util.logging.LogRecord) {
        saetze += record
    }

    override fun flush() = Unit
    override fun close() = Unit
}

class LogbookTest {

    private val senke = SammelndeSenke()
    private val fallback = Logger.getLogger("fallback")
    private val logbook = StandardLogbook("EarthShop", listOf(senke), fallback)

    @Test
    fun `alle vier stufen erreichen die senke`() {
        logbook.debug("shop", "leise")
        logbook.info("shop", "normal")
        logbook.warn("shop", "achtung")
        logbook.error("shop", "kaputt", IllegalStateException("x"))

        assertContentEquals(
            listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            senke.eintraege.map { it.level },
        )
        assertTrue(senke.eintraege.last().error is IllegalStateException)
    }

    @Test
    fun `jeder eintrag traegt das plugin, das ihn geschrieben hat`() {
        logbook.info("shop", "hallo")

        assertEquals("EarthShop", senke.eintraege.single().plugin)
    }

    @Test
    fun `ein bereits gesetztes plugin bleibt stehen`() {
        logbook.log(LogEntry(LogLevel.INFO, "shop", "durchgereicht", plugin = "EarthQuests"))

        assertEquals("EarthQuests", senke.eintraege.single().plugin)
    }

    @Test
    fun `record haelt akteur und details fest`() {
        val spieler = UUID.randomUUID()
        logbook.record("moderation", spieler, "Bann ausgesprochen", "grund" to "Griefing", "dauer" to "7d")

        val eintrag = senke.eintraege.single()
        assertEquals(spieler, eintrag.actor)
        assertEquals(mapOf("grund" to "Griefing", "dauer" to "7d"), eintrag.details)
        assertEquals(LogLevel.INFO, eintrag.level)
    }

    @Test
    fun `eine kaputte senke reisst die anderen nicht mit`() {
        val zweite = SammelndeSenke("zweite")
        val mitFehler = StandardLogbook("EarthShop", listOf(KaputteSenke(), zweite), fallback)

        mitFehler.info("shop", "trotzdem")

        assertEquals(1, zweite.eintraege.size)
    }

    // ---------------------------------------------------------------- Konsole

    @Test
    fun `die konsolenausgabe zeigt kategorie, nachricht, akteur und details`() {
        val spieler = UUID.fromString("00000000-0000-0000-0000-000000000009")
        val text = ConsoleSink.format(
            LogEntry(LogLevel.INFO, "shop", "Kauf", "EarthShop", spieler, mapOf("preis" to 10)),
        )

        assertEquals("[shop] Kauf von $spieler (preis=10)", text)
    }

    @Test
    fun `debug wird nur durchgelassen, wenn es eingeschaltet ist`() {
        val handler = SammelnderHandler()
        val ziel = Logger.getLogger("konsolentest").apply {
            useParentHandlers = false
            level = Level.ALL
            addHandler(handler)
        }

        var debug = false
        val console = ConsoleSink({ ziel }, { debug })

        console.accept(LogEntry(LogLevel.DEBUG, "shop", "leise", "EarthShop"))
        assertTrue(handler.saetze.isEmpty())

        debug = true
        console.accept(LogEntry(LogLevel.DEBUG, "shop", "jetzt aber", "EarthShop"))
        assertEquals(1, handler.saetze.size)
        assertEquals(Level.FINE, handler.saetze.single().level)
    }

    @Test
    fun `die stufen bilden auf die java-stufen ab`() {
        assertEquals(Level.FINE, LogLevel.DEBUG.julLevel)
        assertEquals(Level.INFO, LogLevel.INFO.julLevel)
        assertEquals(Level.WARNING, LogLevel.WARN.julLevel)
        assertEquals(Level.SEVERE, LogLevel.ERROR.julLevel)

        assertTrue(LogLevel.ERROR.atLeast(LogLevel.WARN))
        assertTrue(LogLevel.WARN.atLeast(LogLevel.WARN))
        assertTrue(!LogLevel.INFO.atLeast(LogLevel.WARN))
    }

    // ---------------------------------------------------------------- Datenbankzeile

    @Test
    fun `aus einem eintrag wird eine speicherbare zeile`() {
        val spieler = UUID.randomUUID()
        val record = LogRecord.of(
            LogEntry(
                level = LogLevel.WARN,
                category = "shop",
                message = "knapp",
                plugin = "EarthShop",
                actor = spieler,
                details = mapOf("rest" to 3, "leer" to null),
                error = IllegalArgumentException("nanu"),
            ),
        )

        assertEquals("WARN", record.level)
        assertEquals("EarthShop", record.plugin)
        assertEquals(spieler, record.actor)
        assertEquals(mapOf("rest" to "3", "leer" to "null"), record.details)
        assertTrue(record.error!!.contains("IllegalArgumentException"))
        assertTrue(record.error!!.contains("nanu"))
    }

    @Test
    fun `ohne fehler bleibt die fehlerspalte leer`() {
        assertNull(LogRecord.of(LogEntry(LogLevel.INFO, "shop", "alles gut")).error)
    }
}
