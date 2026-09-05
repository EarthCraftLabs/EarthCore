package de.mecrytv.earthcore.gui

import de.mecrytv.earthcore.gui.api.GuiMask
import de.mecrytv.earthcore.gui.api.GuiType
import de.mecrytv.earthcore.gui.api.Page
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuiLayoutTest {

    // ---------------------------------------------------------------- Maske

    private val maske = GuiMask.of(
        "#########",
        "#.......#",
        "#.......#",
        "P###B###N",
    )

    @Test
    fun `die maske rechnet zeile und spalte in slots um`() {
        assertEquals(36, maske.size)
        assertEquals(9, maske.width)
        assertContentEquals(listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25), maske.slots('.'))
        assertContentEquals(listOf(27), maske.slots('P'))
        assertContentEquals(listOf(35), maske.slots('N'))
        assertContentEquals(listOf(31), maske.slots('B'))
    }

    @Test
    fun `leerzeichen dienen nur der lesbarkeit`() {
        val mitLuecken = GuiMask.of("# # # # #", "# . . . #")

        assertEquals(5, mitLuecken.width)
        assertContentEquals(listOf(6, 7, 8), mitLuecken.slots('.'))
    }

    @Test
    fun `ein unbekanntes symbol liefert keine slots`() {
        assertContentEquals(emptyList(), maske.slots('Z'))
    }

    @Test
    fun `zu jedem slot laesst sich das symbol nachschlagen`() {
        assertEquals('#', maske.symbolAt(0))
        assertEquals('.', maske.symbolAt(10))
        assertEquals('N', maske.symbolAt(35))
        assertNull(maske.symbolAt(36))
        assertNull(maske.symbolAt(-1))
    }

    @Test
    fun `ungleich lange zeilen werden abgelehnt`() {
        val fehler = assertFailsWith<IllegalArgumentException> { GuiMask.of("#####", "###") }
        assertTrue(fehler.message!!.contains("gleich lang"), fehler.message!!)
    }

    @Test
    fun `eine leere maske wird abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { GuiMask(emptyList()) }
    }

    // ---------------------------------------------------------------- Seiten

    @Test
    fun `die seitenzahl rundet auf`() {
        assertEquals(3, Page.of(0, 7, 15).count)
        assertEquals(2, Page.of(0, 7, 14).count)
        assertEquals(1, Page.of(0, 7, 1).count)
    }

    @Test
    fun `ohne eintraege gibt es trotzdem eine seite`() {
        val seite = Page.of(0, 7, 0)

        assertEquals(1, seite.count)
        assertTrue(seite.first)
        assertTrue(seite.last)
        assertContentEquals(emptyList(), seite.slice(emptyList<String>()))
    }

    @Test
    fun `der ausschnitt passt zur seite`() {
        val eintraege = (1..10).map { "eintrag $it" }

        assertContentEquals(eintraege.subList(0, 4), Page.of(0, 4, 10).slice(eintraege))
        assertContentEquals(eintraege.subList(4, 8), Page.of(1, 4, 10).slice(eintraege))
        assertContentEquals(eintraege.subList(8, 10), Page.of(2, 4, 10).slice(eintraege))
    }

    @Test
    fun `die letzte seite ist nicht voll und laeuft nicht ueber`() {
        val eintraege = (1..5).map { it }
        val letzte = Page.of(1, 4, 5)

        assertEquals(1, letzte.slice(eintraege).size)
        assertTrue(letzte.last)
    }

    @Test
    fun `eine seite ausserhalb des bereichs wird eingefangen`() {
        assertEquals(2, Page.of(99, 4, 10).index)
        assertEquals(0, Page.of(-5, 4, 10).index)
    }

    @Test
    fun `blaettern bleibt in den grenzen`() {
        val erste = Page.of(0, 4, 10)

        assertEquals(0, erste.previous().index)
        assertEquals(1, erste.next().index)
        assertEquals(2, erste.next().next().index)
        assertEquals(2, erste.next().next().next().index)
    }

    @Test
    fun `erste und letzte seite werden erkannt`() {
        assertTrue(Page.of(0, 4, 10).first)
        assertFalse(Page.of(0, 4, 10).last)
        assertTrue(Page.of(2, 4, 10).last)
        assertFalse(Page.of(2, 4, 10).first)
    }

    // ---------------------------------------------------------------- Typen

    @Test
    fun `truhengroessen ergeben sich aus den reihen`() {
        assertEquals(9, GuiType.chest(1).size)
        assertEquals(54, GuiType.chest(6).size)
        assertEquals(9, GuiType.chest(3).width)
    }

    @Test
    fun `unmoegliche truhengroessen werden abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { GuiType.chest(0) }
        assertFailsWith<IllegalArgumentException> { GuiType.chest(7) }
    }

    @Test
    fun `die sondertypen haben ihre eigene groesse`() {
        assertEquals(5, GuiType.HOPPER.size)
        assertEquals(9, GuiType.DISPENSER.size)
        assertEquals(3, GuiType.ANVIL.size)
        assertEquals(3, GuiType.DISPENSER.width)
    }
}
