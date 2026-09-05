package de.mecrytv.earthcore.gui

import de.mecrytv.earthcore.gui.api.GuiItem
import de.mecrytv.earthcore.gui.internal.GuiBuffer
import de.mecrytv.earthcore.gui.internal.LoadCache
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuiRenderTest {

    @BeforeTest
    fun start() {
        MockBukkit.mock()
    }

    @AfterTest
    fun stop() {
        MockBukkit.unmock()
    }

    private fun item(material: Material) = GuiItem(ItemStack(material))

    // ---------------------------------------------------------------- Diffing

    @Test
    fun `beim ersten zeichnen sind alle slots neu`() {
        val puffer = GuiBuffer(9)
        puffer[0] = item(Material.STONE)

        assertContentEquals((0 until 9).toList(), puffer.diff(null))
    }

    @Test
    fun `ohne aenderung wird kein slot neu gesetzt`() {
        val puffer = GuiBuffer(9)
        puffer[0] = item(Material.STONE)
        puffer[4] = item(Material.DIRT)
        val vorher = puffer.snapshot()

        assertContentEquals(emptyList(), puffer.diff(vorher))
    }

    @Test
    fun `nur die geaenderten slots kommen zurueck`() {
        val puffer = GuiBuffer(9)
        puffer[0] = item(Material.STONE)
        puffer[4] = item(Material.DIRT)
        val vorher = puffer.snapshot()

        puffer[4] = item(Material.DIAMOND)
        puffer[7] = item(Material.EMERALD)

        assertContentEquals(listOf(4, 7), puffer.diff(vorher))
    }

    @Test
    fun `ein geleerter slot zaehlt als aenderung`() {
        val puffer = GuiBuffer(9)
        puffer[3] = item(Material.STONE)
        val vorher = puffer.snapshot()

        puffer[3] = null

        assertContentEquals(listOf(3), puffer.diff(vorher))
    }

    @Test
    fun `ein anderer klick-handler allein zeichnet nicht neu`() {
        val puffer = GuiBuffer(9)
        puffer[0] = GuiItem(ItemStack(Material.STONE))
        val vorher = puffer.snapshot()

        puffer[0] = GuiItem(ItemStack(Material.STONE)) { }

        assertContentEquals(emptyList(), puffer.diff(vorher), "Gleiches Item, nur anderer Handler")
    }

    @Test
    fun `slots ausserhalb des puffers werden ignoriert`() {
        val puffer = GuiBuffer(9)
        puffer[99] = item(Material.STONE)
        puffer[-1] = item(Material.STONE)

        assertNull(puffer[99])
        assertNull(puffer[-1])
        assertEquals(0, puffer.filled())
    }

    @Test
    fun `clear leert alles`() {
        val puffer = GuiBuffer(9)
        (0 until 9).forEach { puffer[it] = item(Material.STONE) }
        assertEquals(9, puffer.filled())

        puffer.clear()

        assertEquals(0, puffer.filled())
    }

    // ---------------------------------------------------------------- Nachladen

    @Test
    fun `der erste zugriff liefert nichts und stoesst das laden an`() {
        val fertig = CountDownLatch(1)
        var neuGezeichnet = 0
        val cache = LoadCache(
            runAsync = { it.run() },
            onDone = { neuGezeichnet++; fertig.countDown() },
            onError = { _, _ -> },
        )

        assertNull(cache.get("artikel") { "geladen" }, "Beim ersten Aufruf gibt es noch nichts")
        assertTrue(fertig.await(2, TimeUnit.SECONDS))
        assertEquals(1, neuGezeichnet, "Nach dem Laden muss genau einmal neu gezeichnet werden")
        assertEquals("geladen", cache.get("artikel") { "egal" })
    }

    @Test
    fun `ein zweiter zugriff waehrend des ladens startet nicht nochmal`() {
        var aufrufe = 0
        val cache = LoadCache(runAsync = { it.run() }, onDone = {}, onError = { _, _ -> })

        cache.get("artikel") { aufrufe++; "wert" }
        cache.get("artikel") { aufrufe++; "wert" }
        cache.get("artikel") { aufrufe++; "wert" }

        assertEquals(1, aufrufe)
    }

    @Test
    fun `ein fehler beim laden wird gemeldet und nicht endlos wiederholt`() {
        val fehler = mutableListOf<String>()
        val cache = LoadCache(
            runAsync = { it.run() },
            onDone = {},
            onError = { schluessel, _ -> fehler += schluessel },
        )

        assertNull(cache.get("kaputt") { error("geht nicht") })
        assertNull(cache.get("kaputt") { error("geht nicht") })

        assertContentEquals(listOf("kaputt"), fehler)
        assertFalse(cache.pending)
    }

    @Test
    fun `nach invalidate wird erneut geladen`() {
        var aufrufe = 0
        val cache = LoadCache(runAsync = { it.run() }, onDone = {}, onError = { _, _ -> })

        cache.get("artikel") { aufrufe++; "wert" }
        cache.invalidate("artikel")
        cache.get("artikel") { aufrufe++; "wert" }

        assertEquals(2, aufrufe)
    }

    @Test
    fun `ein null-ergebnis wird gemerkt und nicht dauernd neu geladen`() {
        var aufrufe = 0
        val cache = LoadCache(runAsync = { it.run() }, onDone = {}, onError = { _, _ -> })

        cache.get<String?>("leer") { aufrufe++; null }
        cache.get<String?>("leer") { aufrufe++; null }

        assertEquals(1, aufrufe)
    }
}
